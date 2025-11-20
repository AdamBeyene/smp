package com.telemessage.simulators.smpp_cloudhopper.sender;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.type.Address;
import com.telemessage.simulators.smpp_cloudhopper.concatenation.CloudhopperConcatenationType;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for sending long SMS messages with automatic splitting and concatenation.
 *
 * <p>Handles the complexity of splitting long messages into multiple parts and
 * sending them with proper concatenation headers (UDHI, SAR, PAYLOAD, or TEXT_BASE).</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Automatic encoding detection (GSM7 vs UCS2/UTF-8)</li>
 *   <li>Smart message splitting based on encoding and concatenation type</li>
 *   <li>Support for all concatenation methods (UDHI, SAR, PAYLOAD, TEXT_BASE)</li>
 *   <li>Thread-safe reference number generation</li>
 *   <li>Detailed error tracking and logging</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * CloudhopperMessageSender sender = new CloudhopperMessageSender();
 * SendResult result = sender.sendLongMessage(
 *     session,
 *     "1234567890",      // source
 *     "0987654321",      // destination
 *     longMessageText,
 *     "GSM7",            // encoding
 *     CloudhopperConcatenationType.UDHI,
 *     5000               // timeout ms
 * );
 * </pre>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 */
@Slf4j
public class CloudhopperMessageSender {

    /**
     * Maximum sizes for different encoding types (single message, no concatenation).
     */
    private static final int MAX_SINGLE_GSM7_LENGTH = 160;
    private static final int MAX_SINGLE_UCS2_LENGTH = 70;

    /**
     * Maximum sizes for concatenated messages (accounting for UDH overhead).
     */
    private static final int MAX_CONCAT_GSM7_LENGTH = 153;  // 160 - 7 bytes UDH
    private static final int MAX_CONCAT_UCS2_LENGTH = 67;   // 70 - 3 bytes UDH

    /**
     * Maximum size for message_payload TLV.
     */
    private static final int MAX_PAYLOAD_LENGTH = 65535;

    /**
     * Reference number generator (thread-safe).
     */
    private static final AtomicInteger referenceGenerator = new AtomicInteger(0);

    /**
     * Result of a send operation.
     */
    public static class SendResult {
        private final boolean success;
        private final int partsSent;
        private final int totalParts;
        private final List<String> messageIds;
        private final String errorMessage;

        public SendResult(boolean success, int partsSent, int totalParts,
                         List<String> messageIds, String errorMessage) {
            this.success = success;
            this.partsSent = partsSent;
            this.totalParts = totalParts;
            this.messageIds = messageIds != null ? messageIds : new ArrayList<>();
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() { return success; }
        public int getPartsSent() { return partsSent; }
        public int getTotalParts() { return totalParts; }
        public List<String> getMessageIds() { return messageIds; }
        public String getErrorMessage() { return errorMessage; }

        public static SendResult success(int totalParts, List<String> messageIds) {
            return new SendResult(true, totalParts, totalParts, messageIds, null);
        }

        public static SendResult failure(int partsSent, int totalParts, String errorMessage) {
            return new SendResult(false, partsSent, totalParts, new ArrayList<>(), errorMessage);
        }
    }

    /**
     * Sends a long message with automatic splitting if needed.
     *
     * @param session SMPP session to send through
     * @param source Source address (sender)
     * @param destination Destination address (recipient)
     * @param messageText Full message text
     * @param encoding Encoding name (GSM7, UCS2, UTF-8, etc.)
     * @param concatenationType Concatenation type to use
     * @param timeoutMs Timeout for each PDU submission
     * @return SendResult with success status and message IDs
     */
    public SendResult sendLongMessage(
            SmppSession session,
            String source,
            String destination,
            String messageText,
            String encoding,
            CloudhopperConcatenationType concatenationType,
            long timeoutMs) {

        if (session == null || !session.isBound()) {
            return SendResult.failure(0, 0, "Session not bound");
        }

        try {
            // Encode message to bytes
            byte[] messageBytes = CloudhopperUtils.encodeMessage(messageText, encoding);

            // Determine if message needs splitting
            int maxSingleSize = isUnicodeEncoding(encoding) ? MAX_SINGLE_UCS2_LENGTH : MAX_SINGLE_GSM7_LENGTH;

            if (messageBytes.length <= maxSingleSize) {
                // Single message - no splitting needed
                return sendSingleMessage(session, source, destination, messageBytes,
                                        encoding, timeoutMs);
            } else {
                // Long message - split and send with concatenation
                return sendMultiPartMessage(session, source, destination, messageText,
                                           encoding, concatenationType, timeoutMs);
            }

        } catch (Exception e) {
            log.error("Failed to send message: {}", e.getMessage(), e);
            return SendResult.failure(0, 0, "Exception: " + e.getMessage());
        }
    }

    /**
     * Sends a single (non-concatenated) message.
     */
    private SendResult sendSingleMessage(
            SmppSession session,
            String source,
            String destination,
            byte[] messageBytes,
            String encoding,
            long timeoutMs) throws Exception {

        SubmitSm submitSm = createBasicSubmitSm(source, destination, encoding);
        submitSm.setShortMessage(messageBytes);

        SubmitSmResp response = session.submit(submitSm, timeoutMs);

        if (response.getCommandStatus() == SmppConstants.STATUS_OK) {
            List<String> messageIds = new ArrayList<>();
            messageIds.add(response.getMessageId());
            log.debug("Single message sent successfully: msgId={}", response.getMessageId());
            return SendResult.success(1, messageIds);
        } else {
            return SendResult.failure(0, 1,
                "Submit failed with status: " + response.getCommandStatus());
        }
    }

    /**
     * Sends a multi-part message with concatenation.
     */
    private SendResult sendMultiPartMessage(
            SmppSession session,
            String source,
            String destination,
            String messageText,
            String encoding,
            CloudhopperConcatenationType concatenationType,
            long timeoutMs) throws Exception {

        // Split message into parts
        List<String> parts = CloudhopperUtils.splitMessage(messageText, encoding, concatenationType);
        int totalParts = parts.size();

        if (totalParts == 0) {
            return SendResult.failure(0, 0, "Message splitting resulted in 0 parts");
        }

        log.info("Sending multi-part message: {} parts, concatenation type: {}",
                totalParts, concatenationType);

        // Generate reference number for this message
        int referenceNumber = generateReferenceNumber();

        List<String> messageIds = new ArrayList<>();
        int partsSent = 0;

        // Send each part
        for (int i = 0; i < totalParts; i++) {
            int partNumber = i + 1;
            String partText = parts.get(i);

            try {
                SubmitSm submitSm = createMultiPartSubmitSm(
                    source, destination, partText, encoding,
                    concatenationType, referenceNumber, partNumber, totalParts
                );

                SubmitSmResp response = session.submit(submitSm, timeoutMs);

                if (response.getCommandStatus() == SmppConstants.STATUS_OK) {
                    messageIds.add(response.getMessageId());
                    partsSent++;
                    log.debug("Part {}/{} sent successfully: msgId={}",
                             partNumber, totalParts, response.getMessageId());
                } else {
                    log.error("Part {}/{} failed with status: {}",
                             partNumber, totalParts, response.getCommandStatus());
                    return SendResult.failure(partsSent, totalParts,
                        "Part " + partNumber + " failed with status: " + response.getCommandStatus());
                }

            } catch (Exception e) {
                log.error("Exception sending part {}/{}: {}", partNumber, totalParts, e.getMessage());
                return SendResult.failure(partsSent, totalParts,
                    "Part " + partNumber + " threw exception: " + e.getMessage());
            }
        }

        return SendResult.success(totalParts, messageIds);
    }

    /**
     * Creates a basic SubmitSm PDU with common fields.
     */
    private SubmitSm createBasicSubmitSm(String source, String destination, String encoding) {
        SubmitSm submitSm = new SubmitSm();

        // Determine source address TON/NPI
        byte srcTon = SmppConstants.TON_ALPHANUMERIC;
        byte srcNpi = SmppConstants.NPI_UNKNOWN;
        if (source != null && source.matches("^[0-9+]+$")) {
            srcTon = SmppConstants.TON_INTERNATIONAL;
            srcNpi = SmppConstants.NPI_E164;
        }

        // Determine destination address TON/NPI (usually numeric)
        byte dstTon = SmppConstants.TON_INTERNATIONAL;
        byte dstNpi = SmppConstants.NPI_E164;

        // Set addresses
        submitSm.setSourceAddress(CloudhopperUtils.createAddress(srcTon, srcNpi, source));
        submitSm.setDestAddress(CloudhopperUtils.createAddress(dstTon, dstNpi, destination));

        // Set data coding
        submitSm.setDataCoding(CloudhopperUtils.getDataCoding(encoding));

        // Request delivery receipt
        submitSm.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);

        return submitSm;
    }

    /**
     * Creates a SubmitSm PDU for a multi-part message with concatenation headers.
     */
    private SubmitSm createMultiPartSubmitSm(
            String source,
            String destination,
            String partText,
            String encoding,
            CloudhopperConcatenationType concatenationType,
            int referenceNumber,
            int partNumber,
            int totalParts) throws Exception {

        SubmitSm submitSm = createBasicSubmitSm(source, destination, encoding);
        byte[] partBytes = CloudhopperUtils.encodeMessage(partText, encoding);

        switch (concatenationType) {
            case UDHI:
                // UDHI: Add UDH header to short_message
                byte[] udhHeader = CloudhopperUtils.createUdhiHeader(
                    referenceNumber, totalParts, partNumber);
                byte[] fullMessage = new byte[udhHeader.length + partBytes.length];
                System.arraycopy(udhHeader, 0, fullMessage, 0, udhHeader.length);
                System.arraycopy(partBytes, 0, fullMessage, udhHeader.length, partBytes.length);

                submitSm.setEsmClass(SmppConstants.ESM_CLASS_UDHI_MASK);
                submitSm.setShortMessage(fullMessage);
                break;

            case SAR:
                // SAR: Use TLV parameters
                submitSm.setShortMessage(partBytes);
                submitSm.addOptionalParameter(new Tlv(
                    SmppConstants.TAG_SAR_MSG_REF_NUM,
                    new byte[]{(byte)(referenceNumber >> 8), (byte)(referenceNumber & 0xFF)}));
                submitSm.addOptionalParameter(new Tlv(
                    SmppConstants.TAG_SAR_TOTAL_SEGMENTS,
                    new byte[]{(byte)totalParts}));
                submitSm.addOptionalParameter(new Tlv(
                    SmppConstants.TAG_SAR_SEGMENT_SEQNUM,
                    new byte[]{(byte)partNumber}));
                break;

            case PAYLOAD:
                // PAYLOAD: Use message_payload TLV (for very long messages)
                submitSm.setShortMessage(new byte[0]);  // Empty short_message
                submitSm.addOptionalParameter(new Tlv(
                    SmppConstants.TAG_MESSAGE_PAYLOAD,
                    partBytes));
                break;

            case TEXT_BASE:
                // TEXT_BASE: Add "N/M " prefix to message text
                String prefixedText = partNumber + "/" + totalParts + " " + partText;
                byte[] prefixedBytes = CloudhopperUtils.encodeMessage(prefixedText, encoding);
                submitSm.setShortMessage(prefixedBytes);
                break;

            case UDHI_PAYLOAD:
                // UDHI_PAYLOAD: UDH in message_payload TLV
                byte[] udhHeaderPayload = CloudhopperUtils.createUdhiHeader(
                    referenceNumber, totalParts, partNumber);
                byte[] payloadMessage = new byte[udhHeaderPayload.length + partBytes.length];
                System.arraycopy(udhHeaderPayload, 0, payloadMessage, 0, udhHeaderPayload.length);
                System.arraycopy(partBytes, 0, payloadMessage, udhHeaderPayload.length, partBytes.length);

                submitSm.setEsmClass(SmppConstants.ESM_CLASS_UDHI_MASK);
                submitSm.setShortMessage(new byte[0]);  // Empty short_message
                submitSm.addOptionalParameter(new Tlv(
                    SmppConstants.TAG_MESSAGE_PAYLOAD,
                    payloadMessage));
                break;

            default:
                // DEFAULT: No concatenation (shouldn't reach here)
                submitSm.setShortMessage(partBytes);
                break;
        }

        return submitSm;
    }

    /**
     * Generates a unique reference number for concatenated messages.
     */
    private int generateReferenceNumber() {
        return referenceGenerator.incrementAndGet() & 0xFFFF;  // Keep it 16-bit
    }

    /**
     * Checks if the encoding is Unicode-based (requires UCS2 limits).
     */
    private boolean isUnicodeEncoding(String encoding) {
        return encoding != null &&
               (encoding.equalsIgnoreCase("UCS2") ||
                encoding.equalsIgnoreCase("UTF-8") ||
                encoding.equalsIgnoreCase("UTF-16") ||
                encoding.equalsIgnoreCase("UTF-16BE"));
    }
}
