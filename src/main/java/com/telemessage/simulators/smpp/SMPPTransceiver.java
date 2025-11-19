package com.telemessage.simulators.smpp;

import com.logica.smpp.Data;
import com.logica.smpp.pdu.*;
import com.logica.smpp.pdu.tlv.TLVInt;
import com.logica.smpp.pdu.tlv.TLVString;
import com.logica.smpp.pdu.tlv.WrongLengthException;
import com.logica.smpp.util.ByteBuffer;
import com.telemessage.simulators.EnvUtils;
import com.telemessage.simulators.common.Utils;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessageUtils;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.smpp.concatenation.ConcatMessageContent;
import com.telemessage.simulators.smpp.concatenation.ConcatenationData;
import com.telemessage.simulators.smpp.concatenation.ConcatenationType;
import com.telemessage.qatools.error.ErrorTracker;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.telemessage.simulators.smpp.SimUtils.*;


/**
 * @author ronen
 * @since 21/06/2021
 */
@Slf4j
@NoArgsConstructor
@Component
public class SMPPTransceiver extends SMPPConnection {

    // Lock manager for thread-safe concatenated message assembly
    private static final ConcurrentHashMap<String, Object> multipartLocks = new ConcurrentHashMap<>();

    // Timestamp tracking for incomplete multipart messages (for timeout cleanup)
    private static final ConcurrentHashMap<String, Long> multipartTimestamps = new ConcurrentHashMap<>();

    // Timeout for incomplete multipart messages (5 minutes)
    private static final long MULTIPART_TIMEOUT_MS = 5 * 60 * 1000;

    EnvConfiguration conf;
    static SMPPSimulator smppSim;
    private ErrorTracker errorTracker;

    @Autowired
    public SMPPTransceiver(EnvConfiguration conf,
                           SMPPSimulator smppSim,
                           ErrorTracker errorTracker) {
        super.setConf(conf);
        this.conf = conf;
        this.smppSim = smppSim;
        this.errorTracker = errorTracker;
    }

    @Override
    public void receive(Request request) throws IOException {
        final SMPPTransceiver me = this;

        int commandID = request.getCommandId();
        Response response = null;
        SendMessageSM sm = null;
        String msgId = null;
        boolean isIncomingReceived = false;

        switch (commandID) {
            case Data.DELIVER_SM:
            case Data.SUBMIT_SM:
                try {
                    monitor.setLastMessage(System.currentTimeMillis());
                    sm = (SendMessageSM) request;
                    byte dataCoding = sm.getDataCoding();
                    String encoding = SimUtils.determineEncoding(dataCoding, sm.getShortMessage());
                    response = generateReceiveMessageResponse(sm);
                    msgId = ((SendMessageResponse)response).getMessageId();

                    // Handle concatenated message detection
                    ConcatenationData concatData = ConcatenationType.extractSmConcatenationData((StandardSendMessageSM<?>) sm);
                    boolean isMultipart = concatData.getConcatenationType() != ConcatenationType.DEFAULT;
                    log.debug("Concatenation data: {}", concatData);
                    if (isMultipart) {
                        // Validate part number is within valid range
                        if (concatData.getSegmentIndex() < 1 || 
                            concatData.getSegmentIndex() > concatData.getConcatenatedMessageSize()) {
                            log.error("Invalid segment index {} for total parts {}. Message will be ignored.", 
                                concatData.getSegmentIndex(), concatData.getConcatenatedMessageSize());
                            errorTracker.captureError(
                                "SMPPTransceiver.receive",
                                new IllegalArgumentException("Invalid segment index"),
                                "invalid-segment-index",
                                Map.of(
                                    "operation", "concat_message_validation",
                                    "segmentIndex", String.valueOf(concatData.getSegmentIndex()),
                                    "totalParts", String.valueOf(concatData.getConcatenatedMessageSize())
                                )
                            );
                            break;
                        }
                        
                        String partKey = concatData.getConcatenatedMessageId() + "_" + concatData.getSegmentIndex();

                        // Use proper lock object instead of synchronized on String literal
                        Object lock = multipartLocks.computeIfAbsent(
                            "multipart_" + concatData.getConcatenatedMessageId(),
                            k -> new Object()
                        );

                        // Track timestamp for incomplete message cleanup
                        multipartTimestamps.putIfAbsent(
                                String.valueOf(concatData.getConcatenatedMessageId()),
                                System.currentTimeMillis()
                        );

                        synchronized (lock) {
                            // Extract message content using thread-safe method
                            ConcatMessageContent content = SimUtils.extractConcatMessageContent(
                                    (StandardSendMessageSM<?>)sm,
                                    concatData.getConcatenationType(),
                                    encoding,
                                    concatData,me
                            );
                            log.debug("Extracted message content: {}", content);
                            if (!content.isSuccess()) {
                                log.error("Failed to extract message content: {}", content.getError());
                                errorTracker.captureError(
                                    "SMPPTransceiver.receive",
                                    new RuntimeException(content.getError()),
                                    "extract-concat-message-failed",
                                    Map.of(
                                        "operation", "extract_concat_content",
                                        "error", content.getError()
                                    )
                                );
                                throw new RuntimeException(content.getError());
                            }

                            // Cache the message part
                            MessagesObject partMessage = MessagesObject.builder()
                                    .dir("IN_PART")
                                    .id(partKey)
                                    .text(content.getMessageText())
                                    .from(sm.getSourceAddr().getAddress())
                                    .to(sm.getDestAddr().getAddress())
                                    .sendMessageSM(sm.debugString())
                                    .directResponse(response.debugString())
                                    .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                                    .providerId(connManager.getProviderId())
                                    .partNumber(concatData.getSegmentIndex())
                                    .totalParts(concatData.getConcatenatedMessageSize())
                                    .referenceNumber(concatData.getConcatenatedMessageId())
                                    .messageEncoding(encoding)
                                    .rawMessageBytes(content.getRawContent())
                                    .build();

                            smppSim.getMessagesCacheService().addCacheRecord(partKey, partMessage);

                            // Check if we have all parts
                            boolean complete = true;
                            byte[] allRawContent = new byte[0];
                            String firstPartEncoding = null;

                            for (int i = 1; i <= concatData.getConcatenatedMessageSize(); i++) {
                                MessagesObject part = smppSim.getMessagesCacheService()
                                        .getMessageByID(concatData.getConcatenatedMessageId() + "_" + i);
                                if (part == null) {
                                    complete = false;
                                    log.debug("Part {} not yet received for message {}", i, concatData.getConcatenatedMessageId());
                                    break;
                                }
                                
                                // Track first part's encoding
                                if (i == 1) {
                                    firstPartEncoding = part.getMessageEncoding();
                                }
                                
                                // Verify encoding consistency
                                if (part.getMessageEncoding() != null && !part.getMessageEncoding().equals(firstPartEncoding)) {
                                    log.warn("Part {} has different encoding {} vs first part {}", 
                                        i, part.getMessageEncoding(), firstPartEncoding);
                                }
                                
                                // Concatenate raw bytes
                                if (part.getRawMessageBytes() != null) {
                                    byte[] newAllRawContent = new byte[allRawContent.length + part.getRawMessageBytes().length];
                                    System.arraycopy(allRawContent, 0, newAllRawContent, 0, allRawContent.length);
                                    System.arraycopy(part.getRawMessageBytes(), 0, newAllRawContent,
                                            allRawContent.length, part.getRawMessageBytes().length);
                                    allRawContent = newAllRawContent;
                                    log.debug("Part {}: added {} bytes, total now {} bytes", 
                                        i, part.getRawMessageBytes().length, allRawContent.length);
                                }
                            }

                            // If all parts received, create complete message (duplicate check inside sync block)
                            if (complete) {
                                log.info("All {} parts received for message {}, assembling {} total bytes", 
                                    concatData.getConcatenatedMessageSize(), concatData.getConcatenatedMessageId(), allRawContent.length);
                                
                                // CRITICAL FIX: Smart encoding detection and decoding
                                // This handles cases where declared encoding doesn't match actual content
                                String fullText;
                                String actualEncoding;
                                try {
                                    String declaredEncoding = firstPartEncoding != null ? firstPartEncoding : encoding;
                                    Pair<String, String> result = detectAndDecodeMessage(allRawContent, declaredEncoding);
                                    fullText = result.getLeft();
                                    actualEncoding = result.getRight();

                                    log.info("Full message decoded using {}: {} chars from {} bytes",
                                        actualEncoding, fullText.length(), allRawContent.length);

                                    if (!actualEncoding.equals(declaredEncoding)) {
                                        log.warn("ENCODING MISMATCH CORRECTED: Declared={}, Actual={}",
                                            declaredEncoding, actualEncoding);
                                    }

                                    // Log preview of decoded text
                                    if (fullText.length() > 0) {
                                        String preview = fullText.length() > 100
                                            ? fullText.substring(0, 100) + "..."
                                            : fullText;
                                        log.debug("Full message preview: {}", preview);
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to decode full message from raw bytes", e);
                                    errorTracker.captureError(
                                        "SMPPTransceiver.receive",
                                        e,
                                        "decode-full-message-failed",
                                        Map.of(
                                            "operation", "decode_full_message",
                                            "encoding", String.valueOf(firstPartEncoding)
                                        )
                                    );
                                    fullText = "[Error decoding message: " + e.getMessage() + "]";
                                    actualEncoding = firstPartEncoding != null ? firstPartEncoding : encoding;
                                }
                                
                                // Prevent duplicate IN_FULL caching for the same msgId (moved inside sync block)
                                MessagesObject existing = smppSim.getMessagesCacheService().getMessageByID(msgId);
                                if (existing == null || !"IN_FULL".equals(existing.getDir())) {
                                    MessagesObject completeMessage = MessagesObject.builder()
                                            .dir("IN_FULL")
                                            .id(msgId)
                                            .text(fullText)
                                            .from(sm.getSourceAddr().getAddress())
                                            .to(sm.getDestAddr().getAddress())
                                            .sendMessageSM(sm.debugString())
                                            .directResponse(response.debugString())
                                            .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                                            .providerId(connManager.getProviderId())
                                            .messageEncoding(actualEncoding)  // Use detected encoding, not declared
                                            .rawMessageBytes(allRawContent)
                                            .build();

                                    smppSim.getMessagesCacheService().addCacheRecord(msgId, completeMessage);
                                    log.info("Cached complete message with ID {} using encoding: {}", msgId, actualEncoding);
                                } else {
                                    log.debug("IN_FULL message for msgId {} already exists, skipping duplicate cache.", msgId);
                                }

                                // Clean up lock and timestamp after successful assembly
                                String refId = String.valueOf(concatData.getConcatenatedMessageId());
                                multipartLocks.remove("multipart_" + refId);
                                multipartTimestamps.remove(refId);
                                log.debug("Cleaned up multipart tracking for completed message: {}", refId);
                            }
                        }
                    } else {
                        ConcatMessageContent content = SimUtils.extractConcatMessageContent(
                                (StandardSendMessageSM<?>)sm,
                                concatData.getConcatenationType(),
                                encoding,
                                concatData, me
                        );

                        if (!content.isSuccess()) {
                            log.error("Failed to extract message content: {}", content.getError());
                            errorTracker.captureError(
                                "SMPPTransceiver.receive",
                                new RuntimeException(content.getError()),
                                "extract-message-content-failed",
                                Map.of(
                                    "operation", "extract_single_message",
                                    "error", content.getError()
                                )
                            );
                            throw new RuntimeException(content.getError());
                        }
                        // Handle normal non-concatenated message
                        MessagesObject message = MessagesObject.builder()
                                .dir("IN_FULL")
                                .id(msgId)
                                .text(SimUtils.getMessageTextForCaching(sm, me))
                                .from(sm.getSourceAddr().getAddress())
                                .to(sm.getDestAddr().getAddress())
                                .sendMessageSM(sm.debugString())
                                .directResponse(response.debugString())
                                .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                                .providerId(connManager.getProviderId())
                                .messageEncoding(encoding)
                                .build();

                        smppSim.getMessagesCacheService().addCacheRecord(msgId, message);
                    }
                } catch (Exception e) {
                    log.error("Error processing received message", e);
                    errorTracker.captureError(
                        "SMPPTransceiver.receive",
                        e,
                        "process-received-message-failed",
                        Map.of(
                            "operation", "process_received_message",
                            "commandId", String.valueOf(commandID)
                        )
                    );
                }
                break;

            case Data.UNBIND:
                response = request.getResponse();
                break;

            case Data.BIND_TRANSMITTER:
            case Data.BIND_TRANSCEIVER:
                response = request.getResponse();
                response.setCommandId(Data.ESME_RALYBND);
                if (!isBound()) {
                    throw new IOException("Receive bind command, not in startConnection code, while connection is Unbound " + this.getId() + " " + this.getClass().getName());
                }
                break;

            case Data.ENQUIRE_LINK:
                response = request.getResponse();
                if (isBound()) {
                    if (monitor != null)
                        monitor.setLastAckedEnquireLinkTime(System.currentTimeMillis());
                    break;
                }

            default:
                response = new GenericNack(Data.ESME_RINVCMDID, request.getSequenceNumber());
        }

        connManager.respond(response);
        if (commandID == Data.UNBIND) {
            connManager.closeConnection(false);
            initConnection();
        }

        String dr = smppSim.get(this.getId()).getAutomaticDR();
        if (isIncomingReceived && !StringUtils.isEmpty(dr)) {
            try {
                String source = sm.getDestAddr().getAddress();
                String dest = sm.getSourceAddr().getAddress();
                SMPPRequest r;
                if (dest.endsWith("0101010")) {
                    String msgIdHex = EnvUtils.toHex(msgId);
                    r = new SMPPRequest(source, dest, null, String.format(SMPPConnection.DR2, "1600000331F141DD", System.currentTimeMillis(), dr), null);
                } else {
                    r = new SMPPRequest(source, dest, null, String.format(SMPPConnection.DR, msgId, System.currentTimeMillis(), dr), null);
                }

                try {
                    MessagesObject drMessage = MessagesObject.builder()
                            .dir("OUT_transceiver_dr")
                            .to(dest)
                            .from(source)
                            .id(msgId)
                            .text(r.getText())
                            .sendMessageSM(r.toString())
                            .directResponse("DR: " + dr)
                            .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                            .providerId(monitor != null && monitor.connManager != null && StringUtils.isNotEmpty(monitor.connManager.getProviderId())
                                    ? monitor.connManager.getProviderId()
                                    : (connManager != null ? String.valueOf(connManager.getPort()) : ""))
                            .build();
                    if (smppSim.getMessagesCacheService() != null) {
                        boolean ok = smppSim.getMessagesCacheService().addCacheRecord(msgId, drMessage);
                        if (!ok) {
                            log.error("Failed to add outgoing DR message to cache for id {}", msgId);
                            errorTracker.captureError(
                                "SMPPTransceiver.receive",
                                new RuntimeException("Failed to add DR to cache"),
                                "cache-dr-message-failed",
                                Map.of(
                                    "operation", "cache_dr_message",
                                    "messageId", msgId
                                )
                            );
                        }
                    } else {
                        log.error("messagesCache is null! Cannot cache outgoing DR message id {}", msgId);
                        errorTracker.captureError(
                            "SMPPTransceiver.receive",
                            new NullPointerException("messagesCache is null"),
                            "messages-cache-null",
                            Map.of(
                                "operation", "cache_dr_message",
                                "messageId", msgId
                            )
                        );
                    }

                    MessagesObject cachedMessage = smppSim.getMessagesCacheService().getMessageByID(msgId) != null ? smppSim.getMessagesCacheService().getMessageByID(msgId) : null;
                    if (cachedMessage != null) {
                        log.info("found cachedMessage record, Updating for mid: {}, and dr: {}", msgId, dr);
                        cachedMessage.setDirectResponse(cachedMessage.getDirectResponse() + "\n\n" + String.valueOf(r));
                        cachedMessage.setDeliveryReceiptShortMessage(dr);
                        cachedMessage.setDeliveryReceiptTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()));
                        smppSim.getMessagesCacheService().addCacheRecord(msgId, cachedMessage);
                    } else {
                        log.info("Not found cachedMessage record, Adding new for mid: {} and dr: {}", msgId, dr);
                        MessagesObject newDrMsg = MessagesObject.builder()
                                .id(msgId)
                                .directResponse(String.valueOf(r))
                                .deliveryReceiptShortMessage(dr)
                                .deliveryReceiptTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                                .providerId(monitor != null && monitor.connManager != null && StringUtils.isNotEmpty(monitor.connManager.getProviderId())
                                        ? monitor.connManager.getProviderId()
                                        : (connManager != null ? String.valueOf(connManager.getPort()) : ""))
                                .build();
                        smppSim.getMessagesCacheService().addCacheRecord(msgId, newDrMsg);
                    }
                } catch (Exception e) {
                    log.error("Error caching smpp DR message record", e);
                    errorTracker.captureError(
                        "SMPPTransceiver.receive",
                        e,
                        "cache-dr-record-failed",
                        Map.of(
                            "operation", "cache_dr_record",
                            "messageId", msgId
                        )
                    );
                }
                smppSim.send(this.getId(), r, true);
                log.debug(String.format("Trying to send DR %s for connection %d from %s to %s", msgId, this.getId(), source, dest));
            } catch (Exception e) {
                log.error("", e);
                errorTracker.captureError(
                    "SMPPTransceiver.receive",
                    e,
                    "send-dr-failed",
                    Map.of(
                        "operation", "send_dr",
                        "messageId", msgId
                    )
                );
            }
        }
    }


    protected Response generateReceiveMessageResponse(SendMessageSM message) throws WrongLengthOfStringException {
        if (monitor != null)
            monitor.setLastMessage(System.currentTimeMillis());
        SendMessageResponse response = message.getResponse();
        String msgId = Utils.generateRandomKey(10, false);
        response.setMessageId(msgId);
        response.setCommandStatus(smppSim.get(this.id).getDirectStatusAsNumber());
        return response;
    }


    @Override
    public void handleResponse(Response response, SMPPRequestManager requestManager) {
        int commandID = response.getCommandId();
        switch (commandID) {
            case Data.ENQUIRE_LINK_RESP:
                final Integer id = requestManager.get(Integer.class, response.getSequenceNumber());
                if (id != null)
                    requestManager.putAndNotify(id, response);
                monitor.setLastMessage(System.currentTimeMillis());
                break;

            case Data.UNBIND_RESP:
                // actually we already should be disconnect
                break;

            case Data.BIND_RECEIVER_RESP:
            case Data.BIND_TRANSCEIVER_RESP:
                // we shouldn't receive this
                break;

            case Data.SUBMIT_SM_RESP:
            case Data.DELIVER_SM_RESP:
                // received OK for sending message
                break;

            default:
                // nothing else shouldn't be received
                break;
        }
    }


    @Override
    public String getName() {
        return smppSim.getName() + "_" + bindType.name() + "-Transceiver";
    }


    public void send(final SendMessageSM msg) {
        final SMPPTransceiver me = this;
        this.service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignore) {}
                    Response resp = connManager.send(msg);
                    String mid = String.valueOf(new Date().getTime());
                    MessagesObject cacheMessage = MessagesObject.builder()
                            .dir("OUT_transceiver")
                            .to(msg.getDestAddr().getAddress())
                            .from(msg.getSourceAddr().getAddress())
                            .id(mid)
                            .text(SimUtils.getMessageTextForCaching(msg, me))
                            .sendMessageSM(msg.debugString())
                            .directResponse(resp.debugString())
                            .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                            .providerId(StringUtils.isNotEmpty(connManager.getProviderId()) ? connManager.getProviderId() : String.valueOf(connManager.getPort()))
                            .build();
                    MessagesCache cache = smppSim.getMessagesCacheService();
                    if (cache != null) {
                        boolean ok = cache.addCacheRecord(mid, cacheMessage);
                        if (!ok) {
                            log.error("Failed to add outgoing message to cache for id {}", mid);
                            errorTracker.captureError(
                                "SMPPTransceiver.send",
                                new RuntimeException("Failed to add outgoing message to cache"),
                                "cache-outgoing-message-failed",
                                Map.of(
                                    "operation", "cache_outgoing_message",
                                    "messageId", mid
                                )
                            );
                        }
                    }  else {
                        log.error("messagesCache is null! Cannot cache outgoing message id {}", mid);
                        errorTracker.captureError(
                            "SMPPTransceiver.send",
                            new NullPointerException("messagesCache is null"),
                            "messages-cache-null-outgoing",
                            Map.of(
                                "operation", "cache_outgoing_message",
                                "messageId", mid
                            )
                        );
                    }
                    log.info(String.format("Send message %s for conn %d with resp %s", msg.debugString(), SMPPTransceiver.this.getId(), String.valueOf(resp.debugString())));
                } catch (IOException e) {
                    log.error("", e);
                    errorTracker.captureError(
                        "SMPPTransceiver.send",
                        e,
                        "send-message-io-failed",
                        Map.of(
                            "operation", "send_message"
                        )
                    );
                }
            }
        });
    }

    public void sendOld(final SendMessageSM msg) {
        this.service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignore) {}
                    Response resp = connManager.send(msg);
                    String mid = String.valueOf(new Date().getTime());
                    MessagesObject cacheMessage = MessagesObject.builder()
                            .dir("OUT_old")
                            .to(msg.getDestAddr().getAddress())
                            .from(msg.getSourceAddr().getAddress())
                            .id(mid)
                            .text(msg.getShortMessage(StandardCharsets.UTF_8.name()))
                            .sendMessageSM(msg.debugString())
                            .directResponse(resp.debugString())
                            .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                            .providerId(StringUtils.isNotEmpty(connManager.getProviderId()) ? connManager.getProviderId() : String.valueOf(connManager.getPort()))
                            .build();
                    if (smppSim.getMessagesCacheService() != null) {
                        boolean ok = smppSim.getMessagesCacheService().addCacheRecord(mid, cacheMessage);
                        if (!ok) {
                            log.error("Failed to add outgoing message to cache for id {}", mid);
                            errorTracker.captureError(
                                "SMPPTransceiver.sendOld",
                                new RuntimeException("Failed to add outgoing message to cache"),
                                "cache-outgoing-old-message-failed",
                                Map.of(
                                    "operation", "cache_outgoing_old_message",
                                    "messageId", mid
                                )
                            );
                        }
                    }  else {
                        log.error("messagesCache is null! Cannot cache outgoing message id {}", mid);
                        errorTracker.captureError(
                            "SMPPTransceiver.sendOld",
                            new NullPointerException("messagesCache is null"),
                            "messages-cache-null-old",
                            Map.of(
                                "operation", "cache_outgoing_old_message",
                                "messageId", mid
                            )
                        );
                    }
                    log.info(String.format("Send message %s for conn %d with resp %s", msg.debugString(), SMPPTransceiver.this.getId(), String.valueOf(resp.debugString())));
                } catch (IOException e) {
                    log.error("", e);
                    errorTracker.captureError(
                        "SMPPTransceiver.sendOld",
                        e,
                        "send-old-message-io-failed",
                        Map.of(
                            "operation", "send_old_message"
                        )
                    );
                }
            }
        });
    }

    public List<SendMessageSM> prepareMessage(SMPPRequest req, boolean sendAllPartsOfConcatenateMessage) throws UnsupportedEncodingException, WrongLengthOfStringException, IntegerOutOfRangeException, WrongDateFormatException {
        List<String> texts = splitMessages(req);

        List<SendMessageSM> messages = new ArrayList<>();
        byte refNum = (byte) Utils.random.nextInt(256);
        boolean isConvertToUnicode = isConvertToUnicode(req.getText(), this.encoding);
        int splitLength = isConvertToUnicode ? MAX_UNICODE_CONCAT_LENGTH : MAX_ASCII_CONCAT_LENGTH;

        String enc = this.encoding;
        byte dataCoding = this.dataCoding;
        Pair<Byte, String> dataCodingAndEnc = prepareDataCodingAndEnc(isConvertToUnicode, enc, dataCoding, req.getText());
        dataCoding = dataCodingAndEnc.getLeft();
        enc = dataCodingAndEnc.getRight();

        /*
         * TODO Check smpp sim with BE charlie
         *  If  charlie affected -> add if (ArchiveSMPP) do logic else default to 0
         */

        byte srcTON;
        byte srcNPI = this.srcNPI;
        srcTON = determineTONByAddressSuffix(req.src) != -1 ? determineTONByAddressSuffix(req.src) : this.srcTON;

        byte dstTON;
        byte dstNPI = this.dstNPI;
        dstTON = determineTONByAddressSuffix(req.dst) != -1 ? determineTONByAddressSuffix(req.dst) : this.dstTON;


        String src = StringUtils.remove(req.getSrc(), "+- \t");
        if (!"".equals(StringUtils.defaultString(src).replaceAll("[0-9\\-\\+\\s]", "").trim())) {
            srcTON = Data.GSM_TON_ALPHANUMERIC;
            srcNPI = Data.GSM_NPI_UNKNOWN;
        }

        ByteBuffer callback = null;
        if (!StringUtils.isEmpty(req.getCallback())) {
            callback = new ByteBuffer();
            callback.appendByte((byte) 1); // 1=ASCII digits
            callback.appendByte(clbTON);
            callback.appendByte(clbNPI);
            callback.appendString(req.getCallback());
        }

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            SendMessageSM message = createMessage();

            if (callback != null)
                message.setCallbackNum(callback);

            message.setSourceAddr(srcTON, srcNPI, src);
            message.setDestAddr(dstTON, dstNPI, req.getDst());
            if (!StringUtils.isEmpty(req.getServiceType()))
                message.setServiceType(req.getServiceType());

            if (req.getUserMessageRef() != null) {
                message.setUserMessageReference(req.getUserMessageRef());
            }

            if (!StringUtils.isEmpty(req.getDstSubAddress())) {
                message.setDestSubaddress(new ByteBuffer(req.getDstSubAddress().getBytes()));
            }

            if (!StringUtils.isEmpty(req.getSrcSubAddress())) {
                message.setSourceSubaddress(new ByteBuffer(req.getSrcSubAddress().getBytes()));
            }

            Short owner_tag = 0x1926;
            Short extMessageId_tag = 0x1927;
            Short messageTime_tag = 0x1928;
            if (req.getParams() != null && req.getParams().size() > 0) {
                req.getParams().forEach(m -> {
                    if ("owner".equals(m.get("tag"))) {
                        Integer owner_value = Integer.valueOf(m.get("value"));
                        TLVInt tlv = new TLVInt();
                        tlv.setTag(owner_tag);
                        tlv.setValue(owner_value);
                        message.setExtraOptional(tlv);
                    }
                    if ("messageid".equals(m.get("tag"))) {
                        String extMessageId_value = m.get("value");
                        TLVString tlv = new TLVString();
                        tlv.setTag(extMessageId_tag);
                        try {
                            tlv.setValue(extMessageId_value);
                        } catch (WrongLengthException e) {
                            e.printStackTrace();
                        }
                        message.setExtraOptional(tlv);
                    }
                    if ("messagetime".equals(m.get("tag"))) {
                        String messageTime_value = m.get("value");
                        TLVString tlv = new TLVString();
                        tlv.setTag(messageTime_tag);
                        try {
                            tlv.setValue(messageTime_value);
                        } catch (WrongLengthException e) {
                            e.printStackTrace();
                        }
                        message.setExtraOptional(tlv);
                    }
                });

            }


            if (!StringUtils.isEmpty(req.getScheduleDeliveryTime())) {
                String scheduleDeliveryTime = req.getScheduleDeliveryTime();
                if (!scheduleDeliveryTime.endsWith("+"))
                    scheduleDeliveryTime += "+";
                message.setScheduleDeliveryTime(scheduleDeliveryTime);
            }

            if (req.getMessageState() != null && req.getMessageState().getValue() > -1) {
                message.setMessageState(req.getMessageState().getValue());
            }

            message.setDataCoding(dataCoding);
            switch (this.concatenation) {
                case UDHI_PAYLOAD:
                    if (texts.size() > 1) {
                        message.setEsmClass((byte) (Data.SM_UDH_GSM | Data.SM_STORE_FORWARD_MODE)); //Set UDHI Flag Data.SM_UDH_GSM=0x40
                        ByteBuffer udh = new ByteBuffer();
                        udh.appendByte((byte) 5); // UDH Length
                        udh.appendByte((byte) 0x00); // IE Identifier
                        udh.appendByte((byte) 3); // IE Data Length
                        udh.appendByte(refNum); //Reference Number
                        udh.appendByte((byte) texts.size()); //Number of pieces
                        udh.appendByte((byte) (i + 1)); //Sequence number
                        //This encoding comes in Logica Open SMPP. Refer to its docs for more detail
                        udh.appendString(text, enc);
                        message.setMessagePayload(udh);

                        log.debug("UDHI_PAYLOAD: " + "\n" +
                                message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding + "\n" +
                                "Number of pieces :" + (byte) texts.size() + "\n" +
                                "Sequence number :" + (byte) (i + 1) + "\n" +
                                "refNum:" + refNum);
                    }else {
                        message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                        log.debug("default: " + "\n" +
                                message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding);
                    }
                    break;
                case PAYLOAD_MESSAGE:
                    message.setMessagePayload(new ByteBuffer(StringUtils.defaultString(text).getBytes(enc)));
                    log.debug("PAYLOAD_MESSAGE: " + "\n" +
                            "message:" + message.debugString() + "\n" +
                            "val:" + text +
                            "enc:" + enc + "\n" +
                            "dataCoding:" + dataCoding
                    );
                    break;
                case PAYLOAD:
                    if (StringUtils.defaultString(text).length() > splitLength) {
                        byte[] val = StringUtils.defaultString(text).getBytes(enc);
                        message.setMessagePayload(new ByteBuffer(StringUtils.defaultString(text).getBytes(enc)));
                        log.debug("PAYLOAD: " + "\n" +
                                "message:" + message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding
                        );
                    }else {
                        message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                        log.debug("default: " + "\n" +
                                message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding);
                    }
                    break;
                case UDHI:
                    if (texts.size() > 1) {
                        message.setEsmClass((byte) (Data.SM_UDH_GSM | Data.SM_STORE_FORWARD_MODE)); //Set UDHI Flag Data.SM_UDH_GSM=0x40
                        ByteBuffer udh = new ByteBuffer();
                        udh.appendByte((byte) 5); // UDH Length
                        udh.appendByte((byte) 0x00); // IE Identifier
                        udh.appendByte((byte) 3); // IE Data Length
                        udh.appendByte(refNum); //Reference Number
                        udh.appendByte((byte) texts.size()); //Number of pieces
                        udh.appendByte((byte) (i + 1)); //Sequence number
                        //This encoding comes in Logica Open SMPP. Refer to its docs for more detail
                        udh.appendString(text, enc);
                        String encodedText = SimUtils.createString(udh, enc);
                        message.setShortMessage(encodedText, enc);

                        log.debug("UDHI: " + "\n" +
                                "message:" + message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding + "\n" +
                                "Number of pieces :" + (byte) texts.size() + "\n" +
                                "Sequence number :" + (byte) (i + 1) + "\n" +
                                "refNum:" + refNum);
                    }else {
                        message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                        log.debug("default: " + "\n" +
                                message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding);
                    }
                    break;
                case SAR:
                    if (texts.size() > 1) {
                        message.setSarTotalSegments((short) texts.size());
                        message.setSarSegmentSeqnum((short) (i + 1));
                        message.setSarMsgRefNum(refNum);
                        message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                        log.debug("SAR: " + "\n" +
                                "message:" + message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding + "\n" +
                                "setSarTotalSegments :" + (byte) texts.size() + "\n" +
                                "setSarSegmentSeqnum :" + (byte) (i + 1) + "\n" +
                                "refNum:" + refNum);
                        break;
                    } else {
                        message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                        log.debug("default: " + "\n" +
                                message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding);
                    }
                default:
                    message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                    log.debug("default: " + "\n" +
                            message.debugString() + "\n" +
                            "val:" + text + "\n" +
                            "enc:" + enc + "\n" +
                            "dataCoding:" + dataCoding);
                    break;
            }
            message.assignSequenceNumber(true);
            if (shouldAddMessage(sendAllPartsOfConcatenateMessage, texts.size(), i + 1, messages.size()))
                messages.add(message);
        }
        log.debug("message prepare done");
        return messages;
    }


    private boolean shouldAddMessage(boolean sendAllPartsOfConcatenateMessage, int totalPartsNumber, int partNumber, int numberOfMessages) {
        return sendAllPartsOfConcatenateMessage || new Random().nextInt(4) == 1 ||
                numberOfMessages == totalPartsNumber - 1 ||
                (numberOfMessages == 0 && totalPartsNumber == partNumber);
    }

    @Override
    public void onStart(boolean success) {
        super.onStart(success);
    }

    /**
     * Helper method to get correct Charset for given encoding name
     * Handles GSM7, CCGSM and other special encodings
     */
    private static Charset getCharsetForEncoding(String encoding) {
        if (encoding == null || encoding.isEmpty()) {
            log.warn("No encoding specified, using UTF-8");
            return StandardCharsets.UTF_8;
        }
        
        // Handle GSM encodings
        List<String> gsmEncodings = List.of("GSM7", "SCGSM", "CCGSM", "GSM_DEFAULT");
        if (gsmEncodings.contains(encoding.toUpperCase())) {
            try {
                return new com.telemessage.simulators.common.conf.CombinedCharsetProvider().charsetForName(encoding);
            } catch (Exception e) {
                log.warn("Failed to get GSM charset for {}, falling back to ISO-8859-1", encoding, e);
                return StandardCharsets.ISO_8859_1;
            }
        }
        
        // Handle UTF-16BE (common for UCS2)
        if ("UTF-16BE".equalsIgnoreCase(encoding) || "UCS2".equalsIgnoreCase(encoding)) {
            return StandardCharsets.UTF_16BE;
        }
        
        // Standard charsets
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            log.warn("Unknown charset {}, falling back to UTF-8", encoding, e);
            return StandardCharsets.UTF_8;
        }
    }


    private byte determineTON(String number, byte defaultTON) {
        if (number.endsWith("9991")) {
            return (byte) 1;
        } else if (number.endsWith("9992")) {
            return (byte) 2;
        }
        return defaultTON;
    }

    private byte getDataCoding(String enc, SMPPRequest req, boolean isConvertToUnicode) {
        if (isConvertToUnicode) {
            return (byte) 8; // UCS2
        }
        switch (enc) {
            case "Cp1252":
            case "GSM7":
            case "CCGSM":
                return (byte) 0;
            case "ISO-8859-5":
                return (byte) 6;
            case "ISO-8859-8":
                return (byte) 7;
            case "UTF-16BE":
            case "UTF-8":
                return (byte) 8;
            case "US-ASCII":
                return Utils.canBeDisplayedInEnc(req.getText(), "ISO-8859-1") ? (byte) 3 : (byte) 0;
            default:
                return (byte) 0;
        }
    }

    private Optional<ByteBuffer> buildCallback(String callback) {
        if (StringUtils.isEmpty(callback)) {
            return Optional.empty();
        }

        ByteBuffer buffer = new ByteBuffer();
        buffer.appendByte((byte) 1); // 1=ASCII digits
        buffer.appendByte(clbTON);
        buffer.appendByte(clbNPI);
        buffer.appendString(callback);
        return Optional.of(buffer);
    }

    private void addOptionalParams(SMPPRequest req, SendMessageSM message) {
        Short ownerTag = 0x1926;
        Short extMessageIdTag = 0x1927;
        Short messageTimeTag = 0x1928;

        if (req.getParams() != null && !req.getParams().isEmpty()) {
            req.getParams().forEach(param -> {
                String tag = (String) param.get("tag");
                if ("owner".equals(tag)) {
                    Integer ownerValue = Integer.valueOf((String) param.get("value"));
                    TLVInt tlv = new TLVInt();
                    tlv.setTag(ownerTag);
                    tlv.setValue(ownerValue);
                    message.setExtraOptional(tlv);
                } else if ("messageid".equals(tag)) {
                    String messageIdValue = (String) param.get("value");
                    TLVString tlv = new TLVString();
                    tlv.setTag(extMessageIdTag);
                    try {
                        tlv.setValue(messageIdValue);
                    } catch (WrongLengthException e) {
                        log.error("Failed to set messageId", e);
                        errorTracker.captureError(
                            "SMPPTransceiver.addTLVParams",
                            e,
                            "set-message-id-tlv-failed",
                            Map.of(
                                "operation", "set_message_id_tlv",
                                "messageId", messageIdValue
                            )
                        );
                    }
                    message.setExtraOptional(tlv);
                } else if ("messagetime".equals(tag)) {
                    String messageTimeValue = (String) param.get("value");
                    TLVString tlv = new TLVString();
                    tlv.setTag(messageTimeTag);
                    try {
                        tlv.setValue(messageTimeValue);
                    } catch (WrongLengthException e) {
                        log.error("Failed to set messageTime", e);
                        errorTracker.captureError(
                            "SMPPTransceiver.addTLVParams",
                            e,
                            "set-message-time-tlv-failed",
                            Map.of(
                                "operation", "set_message_time_tlv",
                                "messageTime", messageTimeValue
                            )
                        );
                    }
                    message.setExtraOptional(tlv);
                }
            });
        }
    }

    private void processMessagePayload(SMPPRequest req, List<String> texts, int index, byte refNum, String text, SendMessageSM message, String enc) {
        switch (this.concatenation) {
            case UDHI_PAYLOAD:
                if (texts.size() > 1) {
                    createUDHIPayload(refNum, texts, index, text, message, enc);
                }
                break;
            case PAYLOAD_MESSAGE:
                try {
                    message.setMessagePayload(new ByteBuffer(StringUtils.defaultString(text).getBytes(enc)));
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                break;
            case PAYLOAD:
                try {
                    if (text.length() > (isConvertToUnicode(req.getText(), enc) ? MAX_UNICODE_CONCAT_LENGTH : MAX_ASCII_CONCAT_LENGTH)) {
                        message.setMessagePayload(new ByteBuffer(text.getBytes(enc)));
                    }
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                break;
            case UDHI:
                if (texts.size() > 1) {
                    createUDHIPayload(refNum, texts, index, text, message, enc);
                }
                break;
            case SAR:
                if (texts.size() > 1) {
                    try {
                        message.setSarTotalSegments((short) texts.size());
                    } catch (IntegerOutOfRangeException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        message.setSarSegmentSeqnum((short) (index + 1));
                    } catch (IntegerOutOfRangeException e) {
                        throw new RuntimeException(e);
                    }
                    message.setSarMsgRefNum(refNum);
                    try {
                        message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                    } catch (WrongLengthOfStringException | UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
                break;
            default:
                try {
                    message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                } catch (WrongLengthOfStringException | UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                break;
        }
    }

    private void createUDHIPayload(byte refNum, List<String> texts, int index, String text, SendMessageSM message, String enc) {
        message.setEsmClass((byte) (Data.SM_UDH_GSM | Data.SM_STORE_FORWARD_MODE));
        ByteBuffer udh = new ByteBuffer();
        udh.appendByte((byte) 5); // UDH Length
        udh.appendByte((byte) 0x00); // IE Identifier
        udh.appendByte((byte) 3); // IE Data Length
        udh.appendByte(refNum); // Reference Number
        udh.appendByte((byte) texts.size()); // Number of pieces
        udh.appendByte((byte) (index + 1)); // Sequence number
        try {
            udh.appendString(text, enc);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        message.setMessagePayload(udh);
    }

    /**
     * Cleanup stale incomplete multipart messages
     *
     * This method handles incomplete multipart messages after timeout:
     * 1. Releases the multipart lock (prevents memory leak)
     * 2. Creates a BEST-EFFORT IN_FULL message from available parts
     * 3. KEEPS all partial parts in cache for analysis (test tool requirement)
     * 4. Marks incomplete messages clearly for debugging
     *
     * Should be called periodically (e.g., via Spring @Scheduled annotation)
     */
    public static void cleanupStaleMultiparts() {
        long now = System.currentTimeMillis();

        multipartTimestamps.entrySet().removeIf(entry -> {
            String refId = entry.getKey();
            long timestamp = entry.getValue();

            if (now - timestamp > MULTIPART_TIMEOUT_MS) {
                log.warn("Processing incomplete multipart message: refId={}, age={}ms",
                        refId, now - timestamp);

                // Remove the lock to prevent memory leak
                multipartLocks.remove("multipart_" + refId);

                // Create best-effort full message from available parts
                // IMPORTANT: Keep partial parts in cache for test/debug purposes
                if (smppSim != null && smppSim.getMessagesCacheService() != null) {
                    createBestEffortFullMessage(refId);
                }

                return true; // Remove this entry from timestamps map
            }
            return false; // Keep this entry
        });
    }

    /**
     * Creates a best-effort full message from available partial parts
     *
     * This method:
     * 1. Finds all available parts in cache
     * 2. Assembles them in order (skipping missing parts)
     * 3. Creates an IN_FULL_INCOMPLETE message
     * 4. KEEPS all partial parts for analysis
     *
     * @param refId The concatenation reference ID
     */
    private static void createBestEffortFullMessage(String refId) {
        try {
            log.info("Creating best-effort full message for incomplete multipart: {}", refId);

            // Find all available parts (check up to 255 parts per SMPP spec)
            java.util.List<com.telemessage.simulators.controllers.message.MessagesObject> availableParts =
                new java.util.ArrayList<>();
            int expectedTotal = 0;
            String from = null, to = null, providerId = null;

            for (int i = 1; i <= 255; i++) {
                String partId = refId + "_" + i;
                com.telemessage.simulators.controllers.message.MessagesObject part =
                    smppSim.getMessagesCacheService().getMessageByID(partId);

                if (part != null) {
                    availableParts.add(part);
                    if (part.getTotalParts() != null) {
                        expectedTotal = part.getTotalParts();
                    }
                    if (from == null) from = part.getFrom();
                    if (to == null) to = part.getTo();
                    if (providerId == null) providerId = part.getProviderId();

                    log.debug("Found part {}/{} for refId {}", i, expectedTotal, refId);
                }
            }

            if (availableParts.isEmpty()) {
                log.warn("No parts found for refId {} - nothing to assemble", refId);
                return;
            }

            // Sort parts by part number
            availableParts.sort((a, b) -> {
                Integer aNum = a.getPartNumber();
                Integer bNum = b.getPartNumber();
                if (aNum == null) return 1;
                if (bNum == null) return -1;
                return aNum.compareTo(bNum);
            });

            // Assemble available parts (concatenate text and raw bytes)
            StringBuilder textBuilder = new StringBuilder();
            java.io.ByteArrayOutputStream rawBytesStream = new java.io.ByteArrayOutputStream();
            java.util.List<Integer> missingParts = new java.util.ArrayList<>();
            String encoding = null;

            for (int i = 1; i <= expectedTotal; i++) {
                boolean found = false;
                for (com.telemessage.simulators.controllers.message.MessagesObject part : availableParts) {
                    if (part.getPartNumber() != null && part.getPartNumber() == i) {
                        // Add this part's content
                        if (part.getText() != null) {
                            textBuilder.append(part.getText());
                        }
                        if (part.getRawMessageBytes() != null) {
                            rawBytesStream.write(part.getRawMessageBytes());
                        }
                        if (encoding == null && part.getMessageEncoding() != null) {
                            encoding = part.getMessageEncoding();
                        }
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    missingParts.add(i);
                    textBuilder.append("[MISSING PART ").append(i).append("]");
                }
            }

            // Create incomplete full message
            String incompleteMsgId = refId + "_INCOMPLETE";
            String incompleteText = String.format(
                "INCOMPLETE MESSAGE (%d/%d parts received - missing: %s)\n\n%s",
                availableParts.size(),
                expectedTotal > 0 ? expectedTotal : availableParts.size(),
                missingParts.isEmpty() ? "none" : missingParts.toString(),
                textBuilder.toString()
            );

            com.telemessage.simulators.controllers.message.MessagesObject incompleteMessage =
                com.telemessage.simulators.controllers.message.MessagesObject.builder()
                    .dir("IN_FULL_INCOMPLETE")
                    .id(incompleteMsgId)
                    .text(incompleteText)
                    .from(from)
                    .to(to)
                    .providerId(providerId)
                    .messageTime(com.telemessage.simulators.controllers.message.MessageUtils
                        .getMessageDateFromTimestamp(System.currentTimeMillis()))
                    .messageEncoding(encoding)
                    .rawMessageBytes(rawBytesStream.toByteArray())
                    .totalParts(expectedTotal)
                    .partNumber(null) // Full message, no specific part number
                    .referenceNumber(Integer.parseInt(refId))
                    .sendMessageSM("Best-effort assembly after timeout - " + availableParts.size() + " parts available")
                    .build();

            smppSim.getMessagesCacheService().addCacheRecord(incompleteMsgId, incompleteMessage);

            log.info("Created best-effort incomplete message: {} ({}/{} parts, missing: {})",
                incompleteMsgId, availableParts.size(), expectedTotal, missingParts);

            // IMPORTANT: Do NOT delete the partial parts - keep them for analysis
            log.info("Preserved {} partial parts in cache for analysis", availableParts.size());

        } catch (Exception e) {
            log.error("Error creating best-effort full message for refId: {}", refId, e);
            // Note: Cannot use errorTracker here as this is a static method
        }
    }

    /**
     * Build smart encoding priority list based on declared encoding
     *
     * This handles common encoding confusion patterns for ANY encoding:
     * - UTF-16BE ↔ UTF-16LE (endianness swap)
     * - UTF-8 ↔ ISO-8859-1 (single vs multi-byte)
     * - ISO-8859-1 ↔ Windows-1252 (similar but different)
     * - Any encoding might actually be UTF-8 (most common globally)
     *
     * @param declaredEncoding The encoding claimed by sender
     * @return Array of encodings to try, in priority order
     */
    private static String[] buildEncodingPriorityList(String declaredEncoding) {
        if (declaredEncoding == null || declaredEncoding.isEmpty()) {
            return new String[]{"UTF-8", "UTF-16BE", "UTF-16LE", "ISO-8859-1", "Cp1252"};
        }

        String normalized = declaredEncoding.toUpperCase();

        // UTF-16 Big Endian: Often confused with Little Endian
        if (normalized.contains("UTF-16BE") || normalized.equals("UCS2")) {
            log.info("Declared UTF-16BE - will try UTF-16LE early (common endianness swap)");
            return new String[]{
                declaredEncoding, "UTF-16LE", "UTF-8", "ISO-8859-1", "Cp1252"
            };
        }

        // UTF-16 Little Endian: Often confused with Big Endian
        if (normalized.contains("UTF-16LE")) {
            log.info("Declared UTF-16LE - will try UTF-16BE early (common endianness swap)");
            return new String[]{
                declaredEncoding, "UTF-16BE", "UTF-8", "ISO-8859-1", "Cp1252"
            };
        }

        // UTF-8: Often confused with ISO-8859-1 or Windows-1252
        if (normalized.contains("UTF-8") || normalized.equals("UTF8")) {
            log.info("Declared UTF-8 - will try ISO-8859-1 and Windows-1252 (common confusion)");
            return new String[]{
                declaredEncoding, "ISO-8859-1", "Cp1252", "UTF-16BE", "UTF-16LE"
            };
        }

        // ISO-8859-1: Often confused with UTF-8 or Windows-1252
        if (normalized.contains("ISO-8859-1") || normalized.equals("LATIN1")) {
            log.info("Declared ISO-8859-1 - will try UTF-8 and Windows-1252 (common confusion)");
            return new String[]{
                declaredEncoding, "UTF-8", "Cp1252", "UTF-16BE", "UTF-16LE"
            };
        }

        // Windows-1252: Often confused with ISO-8859-1 or UTF-8
        if (normalized.contains("1252") || normalized.equals("CP1252")) {
            log.info("Declared Windows-1252 - will try ISO-8859-1 and UTF-8 (common confusion)");
            return new String[]{
                declaredEncoding, "ISO-8859-1", "UTF-8", "UTF-16BE", "UTF-16LE"
            };
        }

        // GSM encodings: Often confused with ISO-8859-1
        if (normalized.contains("GSM") || normalized.contains("CCGSM") || normalized.contains("SCGSM")) {
            log.info("Declared GSM encoding - will try ISO-8859-1 and UTF-8 (common confusion)");
            return new String[]{
                declaredEncoding, "ISO-8859-1", "UTF-8", "Cp1252", "UTF-16BE"
            };
        }

        // Default: Try declared first, then most common encodings
        log.info("Using default encoding priority for declared: {}", declaredEncoding);
        return new String[]{
            declaredEncoding, "UTF-8", "UTF-16BE", "UTF-16LE", "ISO-8859-1", "Cp1252"
        };
    }

    /**
     * Smart encoding detection and decoding for message bytes
     *
     * This method tries multiple encodings and scores each result to find the best match.
     * Works for ANY encoding mismatch using universal heuristics.
     *
     * @param rawBytes The raw message bytes to decode
     * @param declaredEncoding The encoding declared by the sender
     * @return Pair of (decoded text, actual encoding used)
     */
    private static Pair<String, String> detectAndDecodeMessage(byte[] rawBytes, String declaredEncoding) {
        if (rawBytes == null || rawBytes.length == 0) {
            return Pair.of("", declaredEncoding != null ? declaredEncoding : "UTF-8");
        }

        log.info("Starting smart encoding detection for {} bytes, declared encoding: {}",
            rawBytes.length, declaredEncoding);

        // Build smart encoding priority list based on declared encoding
        String[] encodingsToTry = buildEncodingPriorityList(declaredEncoding);

        String bestText = null;
        String bestEncoding = declaredEncoding;
        double bestScore = 0.0;

        for (String encodingName : encodingsToTry) {
            try {
                Charset charset = Charset.forName(encodingName);
                String decoded = new String(rawBytes, charset);
                double score = scoreDecodedText(decoded, rawBytes.length);

                String preview = decoded.length() > 50 ? decoded.substring(0, 50) + "..." : decoded;
                log.info("Tried encoding {}: score={:.3f}, length={}, preview={}",
                    encodingName, score, decoded.length(), preview.replaceAll("[\r\n]+", " "));

                if (score > bestScore) {
                    bestScore = score;
                    bestText = decoded;
                    bestEncoding = encodingName;
                }

                // If we found an excellent match (>0.95), stop searching
                if (score > 0.95) {
                    log.info("Found excellent match with {} (score={:.3f}), stopping search",
                        encodingName, score);
                    break;
                }
            } catch (Exception e) {
                log.debug("Failed to decode with {}: {}", encodingName, e.getMessage());
            }
        }

        log.info("Smart detection result: bestEncoding={}, bestScore={:.3f}, length={}",
            bestEncoding, bestScore, bestText != null ? bestText.length() : 0);

        return Pair.of(bestText != null ? bestText : "", bestEncoding);
    }

    /**
     * Score decoded text based on multiple heuristics
     *
     * @param text The decoded text to score
     * @param originalByteLength Original byte length for ratio analysis
     * @return Score from 0.0 (worst) to 1.0 (best)
     */
    private static double scoreDecodedText(String text, int originalByteLength) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        int length = text.length();
        int replacementChars = 0;
        int controlChars = 0;
        int printableChars = 0;
        int commonAscii = 0;

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);

            // Replacement character (indicates wrong encoding)
            if (c == '\uFFFD') {
                replacementChars++;
            }
            // Control characters (except common whitespace)
            else if (Character.isISOControl(c) && c != '\r' && c != '\n' && c != '\t') {
                controlChars++;
            }
            // Printable characters
            else if (!Character.isISOControl(c)) {
                printableChars++;

                // Common ASCII (English letters, numbers, spaces, basic punctuation)
                if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                    (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == ',' ||
                    c == ':' || c == ';' || c == '!' || c == '?') {
                    commonAscii++;
                }
            }
        }

        // Calculate base score
        double score = 1.0;

        // Heavy penalty for replacement characters (wrong encoding)
        score -= (replacementChars * 2.0 / length);

        // Penalty for excessive control characters
        score -= (controlChars * 1.5 / length);

        // Bonus for printable characters
        score += (printableChars * 0.5 / length);

        // Bonus for common ASCII
        score += (commonAscii * 0.1 / length);

        // Bonus for common ASCII characters (suggests correct encoding)
        if (commonAscii > length * 0.3) { // More than 30% common chars
            score *= 1.1;
        }

        // UNIVERSAL CHECK: Detect wrong encoding via byte-to-char ratio anomalies
        // This works for ANY encoding mismatch, not just specific languages
        if (originalByteLength > 0) {
            double charToByteRatio = (double) length / originalByteLength;

            // Check for suspicious patterns that indicate wrong encoding:

            // 1. Ratio ~0.5: Multi-byte encoding decoded as single-byte, or vice versa
            //    Examples: UTF-16BE of UTF-16LE, UTF-16 of UTF-8, etc.
            if (charToByteRatio < 0.55 && charToByteRatio > 0.45) {
                // Check if characters look random/unusual (span many Unicode blocks)
                int unicodeBlockChanges = countUnicodeBlockChanges(text);
                double blockChangeRatio = (double) unicodeBlockChanges / Math.max(1, length);

                // Excessive block changes (>50%) suggests random garbage from wrong encoding
                if (blockChangeRatio > 0.5) {
                    log.debug("Suspicious pattern: char/byte ratio {:.2f}, block changes {:.2f} - likely wrong encoding",
                        charToByteRatio, blockChangeRatio);
                    score *= 0.15; // Heavy penalty
                }
            }

            // 2. Ratio < 0.4: Likely binary data or severely wrong encoding
            if (charToByteRatio < 0.4) {
                score *= 0.2; // Very heavy penalty
            }

            // 3. Ratio > 3.0: Might be single-byte decoded as multi-byte UTF-8
            //    Can happen with ISO-8859-1 incorrectly decoded as UTF-8
            if (charToByteRatio > 3.0) {
                score *= 0.5; // Moderate penalty
            }
        }

        // Additional check: Null bytes in text (shouldn't happen in valid text)
        int nullCount = 0;
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == '\0') nullCount++;
        }
        if (nullCount > 0) {
            double nullRatio = (double) nullCount / length;
            score -= (nullRatio * 2.0); // Heavy penalty for null bytes
        }

        // Clamp score between 0 and 1
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Count Unicode block changes in text
     *
     * This is a universal heuristic that works for ANY language.
     * When encoding is wrong, characters appear random and span many Unicode blocks.
     * Correct encoding produces coherent text with fewer block changes.
     *
     * Examples:
     * - English text: Mostly in Basic Latin (U+0000-U+007F), few block changes
     * - Hebrew text: Mostly in Hebrew block (U+0590-U+05FF), few block changes
     * - Multilingual: Multiple blocks but organized, moderate changes
     * - Garbage: Random blocks, MANY changes
     *
     * @param text Text to analyze
     * @return Number of times Unicode block changes between adjacent characters
     */
    private static int countUnicodeBlockChanges(String text) {
        if (text == null || text.length() <= 1) {
            return 0;
        }

        int changes = 0;
        Character.UnicodeBlock previousBlock = null;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Skip whitespace and common punctuation (they're expected to mix)
            if (Character.isWhitespace(c) || (c >= 0x20 && c <= 0x2F) ||
                (c >= 0x3A && c <= 0x40) || (c >= 0x5B && c <= 0x60) ||
                (c >= 0x7B && c <= 0x7E)) {
                continue;
            }

            Character.UnicodeBlock currentBlock = Character.UnicodeBlock.of(c);

            if (currentBlock != null && previousBlock != null && !currentBlock.equals(previousBlock)) {
                changes++;
            }

            if (currentBlock != null) {
                previousBlock = currentBlock;
            }
        }

        return changes;
    }

    @Override
    public void handleRequest(final Request request, SMPPRequestManager requestManager)  {
        final SMPPConnection me = this;
        this.service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    receive(request);
                } catch (IOException e) {
                    log.error(me.toString(), e);
                    errorTracker.captureError(
                        "SMPPTransceiver.handleRequest",
                        e,
                        "handle-request-io-failed",
                        Map.of(
                            "operation", "handle_request",
                            "connection", me.toString()
                        )
                    );
                }
            }
        });
    }
}
