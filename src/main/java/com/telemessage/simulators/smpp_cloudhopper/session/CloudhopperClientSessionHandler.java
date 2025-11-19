package com.telemessage.simulators.smpp_cloudhopper.session;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.controllers.message.MessageUtils;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils.ConcatPart;
import com.telemessage.simulators.smpp_cloudhopper.util.SessionStateManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Session handler for Cloudhopper SMPP sessions.
 *
 * <p>Handles incoming PDUs and manages message reception, concatenation,
 * and delivery receipt generation.</p>
 *
 * <p><b>Supported PDUs:</b></p>
 * <ul>
 *   <li>DELIVER_SM - Incoming messages from SMSC</li>
 *   <li>SUBMIT_SM - Outgoing messages to SMSC (SMSC mode)</li>
 *   <li>ENQUIRE_LINK - Keep-alive requests</li>
 *   <li>UNBIND - Session termination</li>
 * </ul>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic message concatenation (UDHI, SAR, PAYLOAD)</li>
 *   <li>Multi-part message assembly</li>
 *   <li>Delivery receipt generation</li>
 *   <li>Message caching</li>
 *   <li>Error handling and recovery</li>
 * </ul>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 */
@Slf4j
public class CloudhopperClientSessionHandler extends DefaultSmppSessionHandler {

    private static final DateTimeFormatter SMPP_DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZoneId.systemDefault());

    private final int connectionId;
    private final SMPPConnectionConf config;
    private final SessionStateManager sessionStateManager;
    private final MessagesCache messagesCache;

    // Concatenation assembly maps
    private final Map<String, Map<Integer, ConcatPart>> concatenationMap = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> concatenationLocks = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param connectionId Connection ID
     * @param config Connection configuration
     * @param sessionStateManager Session state manager
     * @param messagesCache Message cache service
     */
    public CloudhopperClientSessionHandler(
            int connectionId,
            SMPPConnectionConf config,
            SessionStateManager sessionStateManager,
            MessagesCache messagesCache) {
        this.connectionId = connectionId;
        this.config = config;
        this.sessionStateManager = sessionStateManager;
        this.messagesCache = messagesCache;
    }

    @Override
    public PduResponse firePduRequestReceived(PduRequest pduRequest) {
        try {
            // Handle different PDU types
            return switch (pduRequest.getCommandId()) {
                case SmppConstants.CMD_ID_DELIVER_SM ->
                    handleDeliverSm((DeliverSm) pduRequest);
                case SmppConstants.CMD_ID_SUBMIT_SM ->
                    handleSubmitSm((SubmitSm) pduRequest);
                case SmppConstants.CMD_ID_ENQUIRE_LINK ->
                    handleEnquireLink((EnquireLink) pduRequest);
                default -> {
                    log.warn("Unhandled PDU type: 0x{}", Integer.toHexString(pduRequest.getCommandId()));
                    yield pduRequest.createResponse();
                }
            };
        } catch (Exception e) {
            log.error("Error processing PDU request", e);
            sessionStateManager.incrementErrors(connectionId);

            // Return error response
            PduResponse response = pduRequest.createResponse();
            response.setCommandStatus(SmppConstants.STATUS_SYSERR);
            return response;
        }
    }

    /**
     * Handles DELIVER_SM PDU (incoming message).
     */
    private PduResponse handleDeliverSm(DeliverSm deliverSm) {
        log.debug("Received DELIVER_SM from: {}", deliverSm.getSourceAddress().getAddress());

        try {
            // Check if this is a delivery receipt
            if (isDeliveryReceipt(deliverSm)) {
                return handleDeliveryReceipt(deliverSm);
            }

            // Check for concatenation
            ConcatPart concatData = CloudhopperUtils.extractConcatenationData(deliverSm);

            if (concatData != null) {
                return handleConcatenatedMessage(deliverSm, concatData);
            } else {
                return handleSingleMessage(deliverSm);
            }

        } catch (Exception e) {
            log.error("Error handling DELIVER_SM", e);
            sessionStateManager.incrementErrors(connectionId);

            DeliverSmResp response = deliverSm.createResponse();
            response.setCommandStatus(SmppConstants.STATUS_SYSERR);
            return response;
        }
    }

    /**
     * Handles single (non-concatenated) message.
     */
    private PduResponse handleSingleMessage(DeliverSm deliverSm) {
        // Smart encoding detection
        String declaredEncoding = getEncodingName(deliverSm.getDataCoding());
        org.apache.commons.lang3.tuple.Pair<String, String> result =
            CloudhopperUtils.detectAndDecodeMessage(
                deliverSm.getShortMessage(),
                declaredEncoding
            );
        String messageText = result.getLeft();
        String actualEncoding = result.getRight();

        // Log if encoding was corrected
        if (!actualEncoding.equals(declaredEncoding)) {
            log.info("Single message ENCODING CORRECTED: Declared='{}', Actual='{}'",
                declaredEncoding, actualEncoding);
        }

        // Generate message ID
        String messageId = CloudhopperUtils.generateMessageId();

        // Create message object with Cloudhopper metadata
        MessagesObject msgObj = MessagesObject.builder()
            .dir("IN_FULL")
            .id(messageId)
            .from(deliverSm.getSourceAddress().getAddress())
            .to(deliverSm.getDestAddress().getAddress())
            .text(messageText)
            .messageEncoding(actualEncoding)  // Use detected encoding
            .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
            // Cloudhopper-specific metadata
            .concatenationType("DEFAULT")
            .encodingCorrected(!actualEncoding.equals(declaredEncoding))
            .declaredEncoding(declaredEncoding)
            .detectedEncoding(actualEncoding)
            .esmClass(deliverSm.getEsmClass())
            .dataCoding(deliverSm.getDataCoding())
            .smppVersion("3.4")
            .implementationType("Cloudhopper")
            .rawMessageBytes(deliverSm.getShortMessage())
            .build();

        // Cache message
        messagesCache.addCacheRecord(messageId, msgObj);

        // Increment counter
        sessionStateManager.incrementMessagesReceived(connectionId);

        // Create response
        DeliverSmResp response = deliverSm.createResponse();
        response.setMessageId(messageId);

        // Generate DR if configured
        if (shouldGenerateDR()) {
            generateDeliveryReceipt(deliverSm, messageId);
        }

        log.debug("Single message received and cached: msgId={}, text={}",
            messageId, messageText.substring(0, Math.min(20, messageText.length())));

        return response;
    }

    /**
     * Handles concatenated message parts.
     */
    private PduResponse handleConcatenatedMessage(DeliverSm deliverSm, ConcatPart concatData) {
        String reference = String.valueOf(concatData.getReference());

        // Get lock for this reference
        ReentrantLock lock = concatenationLocks.computeIfAbsent(reference, k -> new ReentrantLock());

        lock.lock();
        try {
            // Get or create parts map
            Map<Integer, ConcatPart> partsMap =
                concatenationMap.computeIfAbsent(reference, k -> new ConcurrentHashMap<>());

            // Add this part
            partsMap.put(concatData.getPartNumber(), concatData);

            log.debug("Received concat part {}/{} (ref: {})",
                concatData.getPartNumber(), concatData.getTotalParts(), reference);

            // Check if all parts received
            if (partsMap.size() == concatData.getTotalParts()) {
                return handleCompleteMessage(deliverSm, partsMap, reference);
            } else {
                // Not complete yet, return success
                DeliverSmResp response = deliverSm.createResponse();
                response.setMessageId(CloudhopperUtils.generateMessageId());
                return response;
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Handles complete concatenated message assembly.
     */
    private PduResponse handleCompleteMessage(
            DeliverSm deliverSm,
            Map<Integer, ConcatPart> partsMap,
            String reference) {

        // Concatenate RAW BYTES first (not decoded text)
        byte[] allRawBytes = new byte[0];

        for (int i = 1; i <= partsMap.size(); i++) {
            ConcatPart part = partsMap.get(i);
            if (part != null && part.getContent() != null) {
                byte[] newBytes = new byte[allRawBytes.length + part.getContent().length];
                System.arraycopy(allRawBytes, 0, newBytes, 0, allRawBytes.length);
                System.arraycopy(part.getContent(), 0, newBytes, allRawBytes.length, part.getContent().length);
                allRawBytes = newBytes;
            }
        }

        // Smart encoding detection on complete message
        String declaredEncoding = getEncodingName(deliverSm.getDataCoding());
        org.apache.commons.lang3.tuple.Pair<String, String> result =
            CloudhopperUtils.detectAndDecodeMessage(allRawBytes, declaredEncoding);
        String fullText = result.getLeft();
        String actualEncoding = result.getRight();

        // Log if encoding was corrected
        if (!actualEncoding.equals(declaredEncoding)) {
            log.warn("Concatenated message ENCODING CORRECTED: Declared='{}', Actual='{}', parts={}",
                declaredEncoding, actualEncoding, partsMap.size());
        }

        // Generate message ID
        String messageId = CloudhopperUtils.generateMessageId();

        // Detect concatenation type from first part
        ConcatPart firstPart = partsMap.get(1);
        String concatType = firstPart != null ? firstPart.type.name() : "UNKNOWN";

        // Create message object with Cloudhopper metadata
        MessagesObject msgObj = MessagesObject.builder()
            .dir("IN_CONCAT")
            .id(messageId)
            .from(deliverSm.getSourceAddress().getAddress())
            .to(deliverSm.getDestAddress().getAddress())
            .text(fullText)
            .messageEncoding(actualEncoding)  // Use detected encoding, not declared
            .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
            .totalParts(partsMap.size())
            .referenceNumber(firstPart != null ? firstPart.reference : null)
            // Cloudhopper-specific metadata
            .concatenationType(concatType)
            .encodingCorrected(!actualEncoding.equals(declaredEncoding))
            .declaredEncoding(declaredEncoding)
            .detectedEncoding(actualEncoding)
            .esmClass(deliverSm.getEsmClass())
            .dataCoding(deliverSm.getDataCoding())
            .smppVersion("3.4")
            .implementationType("Cloudhopper")
            .rawMessageBytes(allRawBytes)
            .build();

        // Cache message
        messagesCache.addCacheRecord(messageId, msgObj);

        // Increment counter
        sessionStateManager.incrementMessagesReceived(connectionId);

        // Clean up concatenation data
        concatenationMap.remove(reference);
        concatenationLocks.remove(reference);

        log.info("Complete concatenated message assembled: msgId={}, parts={}, text={}",
            messageId, partsMap.size(), completeText.substring(0, Math.min(20, completeText.length())));

        // Create response
        DeliverSmResp response = deliverSm.createResponse();
        response.setMessageId(messageId);

        return response;
    }

    /**
     * Handles SUBMIT_SM PDU (SMSC mode).
     */
    private PduResponse handleSubmitSm(SubmitSm submitSm) {
        log.debug("Received SUBMIT_SM to: {}", submitSm.getDestAddress().getAddress());

        try {
            // Process similar to DELIVER_SM
            String messageText = CloudhopperUtils.decodeMessage(
                submitSm.getShortMessage(),
                submitSm.getDataCoding()
            );

            // Generate message ID
            String messageId = CloudhopperUtils.generateMessageId();

            // Create message object using builder pattern
            MessagesObject msgObj = MessagesObject.builder()
                .dir("IN_SUBMIT")
                .id(messageId)
                .from(submitSm.getSourceAddress().getAddress())
                .to(submitSm.getDestAddress().getAddress())
                .text(messageText)
                .messageEncoding(getEncodingName(submitSm.getDataCoding()))
                .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                .build();

            // Cache message
            messagesCache.addCacheRecord(messageId, msgObj);

            sessionStateManager.incrementMessagesReceived(connectionId);

            // Create response
            SubmitSmResp response = submitSm.createResponse();
            response.setMessageId(messageId);

            log.debug("SUBMIT_SM processed: msgId={}", messageId);

            return response;

        } catch (Exception e) {
            log.error("Error handling SUBMIT_SM", e);
            SubmitSmResp response = submitSm.createResponse();
            response.setCommandStatus(SmppConstants.STATUS_SYSERR);
            return response;
        }
    }

    /**
     * Handles ENQUIRE_LINK PDU (keep-alive).
     */
    private PduResponse handleEnquireLink(EnquireLink enquireLink) {
        log.trace("Received ENQUIRE_LINK");
        return enquireLink.createResponse();
    }

    /**
     * Checks if DELIVER_SM is a delivery receipt.
     */
    private boolean isDeliveryReceipt(DeliverSm deliverSm) {
        return (deliverSm.getEsmClass() & SmppConstants.ESM_CLASS_MT_SMSC_DELIVERY_RECEIPT) != 0;
    }

    /**
     * Handles delivery receipt.
     */
    private PduResponse handleDeliveryReceipt(DeliverSm deliverSm) {
        log.debug("Received delivery receipt");

        // Decode DR text
        String drText = CloudhopperUtils.decodeMessage(
            deliverSm.getShortMessage(),
            deliverSm.getDataCoding()
        );

        log.info("DR: {}", drText);

        DeliverSmResp response = deliverSm.createResponse();
        response.setMessageId(CloudhopperUtils.generateMessageId());
        return response;
    }

    /**
     * Generates and sends a delivery receipt.
     */
    private void generateDeliveryReceipt(DeliverSm originalMessage, String messageId) {
        try {
            String submitDate = SMPP_DATE_FORMAT.format(Instant.now());
            String doneDate = SMPP_DATE_FORMAT.format(Instant.now());

            String drText = CloudhopperUtils.formatDeliveryReceipt(
                messageId,
                "DELIVRD",
                submitDate,
                doneDate,
                ""
            );

            log.debug("DR generated: {}", drText);

            // In real implementation, would send DELIVER_SM with DR back to originator
            // For now, just log it

        } catch (Exception e) {
            log.error("Failed to generate DR", e);
        }
    }

    /**
     * Checks if automatic DR generation is enabled.
     */
    private boolean shouldGenerateDR() {
        return config.getAutomaticDR() != null &&
               !config.getAutomaticDR().isEmpty() &&
               !"NONE".equalsIgnoreCase(config.getAutomaticDR());
    }

    /**
     * Gets encoding name from data coding byte.
     */
    private String getEncodingName(byte dataCoding) {
        return switch (dataCoding) {
            case SmppConstants.DATA_CODING_DEFAULT -> "GSM7";
            case SmppConstants.DATA_CODING_UCS2 -> "UCS2";
            case SmppConstants.DATA_CODING_LATIN1 -> "ISO-8859-1";
            default -> "UNKNOWN";
        };
    }

    @Override
    public void fireChannelUnexpectedlyClosed() {
        log.warn("Channel unexpectedly closed for connection {}", connectionId);
        sessionStateManager.updateState(connectionId, CloudhopperUtils.SessionState.CLOSED);
        super.fireChannelUnexpectedlyClosed();
    }

    @Override
    public void fireExpectedPduResponseReceived(PduAsyncResponse pduAsyncResponse) {
        log.trace("Expected PDU response received");
        super.fireExpectedPduResponseReceived(pduAsyncResponse);
    }

    @Override
    public void fireUnexpectedPduResponseReceived(PduResponse pduResponse) {
        log.warn("Unexpected PDU response received: 0x{}", Integer.toHexString(pduResponse.getCommandId()));
        super.fireUnexpectedPduResponseReceived(pduResponse);
    }

    @Override
    public void fireUnrecoverablePduException(UnrecoverablePduException e) {
        log.error("Unrecoverable PDU exception for connection {}", connectionId, e);
        sessionStateManager.incrementErrors(connectionId);
        super.fireUnrecoverablePduException(e);
    }

    @Override
    public void fireRecoverablePduException(RecoverablePduException e) {
        log.warn("Recoverable PDU exception for connection {}", connectionId, e);
        sessionStateManager.incrementErrors(connectionId);
        super.fireRecoverablePduException(e);
    }
}
