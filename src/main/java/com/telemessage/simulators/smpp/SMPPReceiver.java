package com.telemessage.simulators.smpp;

import com.logica.smpp.Data;
import com.logica.smpp.pdu.*;
import com.telemessage.simulators.EnvUtils;
import com.telemessage.simulators.common.Utils;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessageUtils;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.http.HttpSimulator;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@AllArgsConstructor
@NoArgsConstructor
@Component
public class SMPPReceiver extends SMPPConnection {

    // Lock manager for thread-safe concatenated message assembly
    private static final ConcurrentHashMap<String, Object> multipartLocks = new ConcurrentHashMap<>();

    EnvConfiguration conf;
    static SMPPSimulator smppSim;
    static HttpSimulator httpSim;
    private ErrorTracker errorTracker;

    @Autowired
    public SMPPReceiver(EnvConfiguration conf,
                        SMPPSimulator smppSim,
                        HttpSimulator httpSim,
                        ErrorTracker errorTracker) {
        super.setConf(conf);
        this.conf = conf;
        this.smppSim = smppSim;
        this.httpSim = httpSim;
        this.errorTracker = errorTracker;
    }

    @Override
    public void receive(Request request) throws IOException {
        final SMPPReceiver me = this;
        int commandID = request.getCommandId();
        Response response = null;
        SendMessageSM sm = null;
        String msgId = null;

        switch (commandID) {
            case Data.DELIVER_SM:
            case Data.SUBMIT_SM:
                try {
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
                                "SMPPReceiver.receive",
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
                                    "SMPPReceiver.receive",
                                    new RuntimeException(content.getError()),
                                    "extract-concat-message-failed",
                                    Map.of(
                                        "operation", "extract_concat_message",
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
                                // Try declared encoding first, then UTF-8, then others
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
                                        "SMPPReceiver.receive",
                                        e,
                                        "decode-full-message-failed",
                                        Map.of(
                                            "operation", "decode_full_message",
                                            "encoding", encoding
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
                                    log.info("Cached complete message with ID {}", msgId);
                                } else {
                                    log.debug("IN_FULL message for msgId {} already exists, skipping duplicate cache.", msgId);
                                }
                                
                                // Clean up lock after successful assembly
                                multipartLocks.remove("multipart_" + concatData.getConcatenatedMessageId());
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
                                "SMPPReceiver.receive",
                                new RuntimeException(content.getError()),
                                "extract-message-content-failed",
                                Map.of(
                                    "operation", "extract_message_content",
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
                        "SMPPReceiver.receive",
                        e,
                        "process-received-message-failed",
                        Map.of(
                            "operation", "process_received_message"
                        )
                    );
                }
                break;

            case Data.UNBIND:
                response = request.getResponse();
                break;

            case Data.BIND_TRANSCEIVER:
            case Data.BIND_RECEIVER:
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
                }
                break;
            default:
                response = new GenericNack(Data.ESME_RINVCMDID, request.getSequenceNumber());
        }

        connManager.respond(response);
        if (commandID == Data.UNBIND) {
            connManager.closeConnection(false);
            initConnection();
        }
        String dr = smppSim.get(this.getId()).getAutomaticDR();
        log.debug(String.format("Prepare to send DR if needed - mid %s conn %d dr %s", msgId, this.getId(), dr));
        if (sm != null && !StringUtils.isEmpty(dr)) {
            log.debug("ID: {} Sending DR for message: {}, DR: {}",this.getId(), sm.getShortMessage(), dr);
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
                log.debug(String.format("Trying to send DR %s ,mid %s, for connection %d from %s to %s",r.toString(), msgId, this.getId(), source, dest));
                boolean success = false;
                try {
                    success = smppSim.send(this.getId(), r, true);
                } catch (Exception ex){
                    log.error("Error sending DR", ex);
                    errorTracker.captureError(
                        "SMPPReceiver.receive",
                        ex,
                        "send-dr-failed",
                        Map.of(
                            "operation", "send_dr",
                            "messageId", String.valueOf(msgId)
                        )
                    );
                }
                log.debug(String.format("DR success? %s ,mid %s, for connection %d from %s to %s",success, msgId, this.getId(), source, dest));
                //if success cache the message
                if(success) {
                    try {
                        MessagesObject cachedMessage = smppSim.getMessagesCacheService().getMessageByID(msgId) != null ? smppSim.getMessagesCacheService().getMessageByID(msgId) : null;
                        if (cachedMessage != null && !cachedMessage.getDir().equals("OUT_dr")) {
                            log.info("found cachedMessage record, Updating for mid: {}, and dr: {}", msgId, dr);
                            cachedMessage.setDirectResponse(cachedMessage.getDirectResponse() + "\n\n" + "sent response=" + success + "request:" + String.valueOf(r));
                            cachedMessage.setDeliveryReceiptShortMessage(dr);
                            cachedMessage.setDeliveryReceiptTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()));
                            smppSim.getMessagesCacheService().addCacheRecord(msgId, cachedMessage);
                        } else {
                            log.info("Not found cachedMessage record, Adding new for mid: {} and dr: {}", msgId, dr);

                            MessagesObject drMessage = MessagesObject.builder()
                                    .dir("OUT_dr")
                                    .to(dest)
                                    .from(source)
                                    .id(msgId)
                                    .text(r.getText())
                                    .sendMessageSM(r.toString())
                                    .directResponse("DR attempt success:" + success + "\nDR: " + dr)
                                    .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                                    .deliveryReceiptShortMessage(dr)
                                    .deliveryReceiptTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                                    .providerId(monitor != null && monitor.connManager != null && StringUtils.isNotEmpty(monitor.connManager.getProviderId())
                                            ? monitor.connManager.getProviderId()
                                            : (connManager != null ? String.valueOf(connManager.getPort()) : ""))
                                    .build();
//                            MessagesObject newDrMsg = MessagesObject.builder()
//                                    .id(msgId)
//                                    .directResponse(String.valueOf(r))
//                                    .deliveryReceiptShortMessage(dr)
//                                    .deliveryReceiptTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
//                                    .providerId(monitor != null && monitor.connManager != null && StringUtils.isNotEmpty(monitor.connManager.getProviderId())
//                                            ? monitor.connManager.getProviderId()
//                                            : (connManager != null ? String.valueOf(connManager.getPort()) : ""))
//                                    .build();
                            if (smppSim.getMessagesCacheService() != null) {
                                boolean ok = smppSim.getMessagesCacheService().addCacheRecord(msgId, drMessage);;
                                if (!ok) {
                                    log.error("Failed to add outgoing DR message to cache for id {}", msgId);
                                    errorTracker.captureError(
                                        "SMPPReceiver.receive",
                                        new RuntimeException("Failed to add DR to cache"),
                                        "cache-dr-message-failed",
                                        Map.of(
                                            "operation", "cache_dr_message",
                                            "messageId", String.valueOf(msgId)
                                        )
                                    );
                                }
                            } else {
                                log.error("messagesCache is null! Cannot cache outgoing DR message id {}", msgId);
                                errorTracker.captureError(
                                    "SMPPReceiver.receive",
                                    new NullPointerException("messagesCache is null"),
                                    "messages-cache-null",
                                    Map.of(
                                        "operation", "cache_dr_message",
                                        "messageId", String.valueOf(msgId)
                                    )
                                );
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error caching smpp DR message record", e);
                        errorTracker.captureError(
                            "SMPPReceiver.receive",
                            e,
                            "cache-dr-record-failed",
                            Map.of(
                                "operation", "cache_dr_record",
                                "messageId", String.valueOf(msgId)
                            )
                        );
                    }
                } else {
                    log.error("Failed to send DR message: {}, for connection: {}, from: {}, to: {}", msgId, this.getId(), source, dest);
                    errorTracker.captureError(
                        "SMPPReceiver.receive",
                        new RuntimeException("Failed to send DR message"),
                        "send-dr-message-failed",
                        Map.of(
                            "operation", "send_dr_message",
                            "messageId", String.valueOf(msgId),
                            "connectionId", String.valueOf(this.getId())
                        )
                    );
                }
            } catch (Exception e) {
                log.error("", e);
                errorTracker.captureError(
                    "SMPPReceiver.receive",
                    e,
                    "dr-processing-failed",
                    Map.of(
                        "operation", "dr_processing"
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
     * Smart encoding detection - tries multiple encodings and picks the best one
     *
     * This method:
     * 1. Tries the declared encoding first
     * 2. Validates if the result looks reasonable (no excessive garbage)
     * 3. Falls back to UTF-8 for multilingual content
     * 4. Tries other common encodings
     * 5. Returns the decoded text and actual encoding used
     *
     * @param rawBytes The raw message bytes
     * @param declaredEncoding The encoding claimed by the sender
     * @return Pair of (decoded text, actual encoding name)
     */
    private static Pair<String, String> detectAndDecodeMessage(byte[] rawBytes, String declaredEncoding) {
        if (rawBytes == null || rawBytes.length == 0) {
            return Pair.of("", declaredEncoding != null ? declaredEncoding : "UTF-8");
        }

        // Build smart encoding priority list based on declared encoding
        // This handles common encoding confusion patterns
        String[] encodingsToTry = buildEncodingPriorityList(declaredEncoding);

        String bestText = null;
        String bestEncoding = declaredEncoding;
        double bestScore = -1;

        log.info("Starting smart encoding detection for {} bytes, declared encoding: {}",
            rawBytes.length, declaredEncoding);

        for (String encodingName : encodingsToTry) {
            if (encodingName == null || encodingName.isEmpty()) continue;

            try {
                Charset charset = getCharsetForEncoding(encodingName);
                String decoded = new String(rawBytes, charset);

                // Score this encoding attempt
                double score = scoreDecodedText(decoded, rawBytes.length);

                String preview = decoded.length() > 50 ? decoded.substring(0, 50) : decoded;
                log.info("Tried encoding {}: score={:.3f}, length={}, preview={}",
                    encodingName, score, decoded.length(), preview);

                if (score > bestScore) {
                    bestScore = score;
                    bestText = decoded;
                    bestEncoding = encodingName;
                }

                // If we got a perfect or near-perfect score, stop trying
                if (score >= 0.95) {
                    log.info("Found excellent match with {} (score={:.3f}), stopping search",
                        encodingName, score);
                    break;
                }
            } catch (Exception e) {
                log.warn("Failed to decode with {}: {}", encodingName, e.getMessage());
            }
        }

        log.info("Smart detection result: bestEncoding={}, bestScore={:.3f}", bestEncoding, bestScore);

        if (bestText == null) {
            log.warn("All encoding attempts failed, using ISO-8859-1 as last resort");
            bestText = new String(rawBytes, StandardCharsets.ISO_8859_1);
            bestEncoding = "ISO-8859-1";
        }

        return Pair.of(bestText, bestEncoding);
    }

    /**
     * Score a decoded text to determine if the encoding is likely correct
     *
     * Higher score = better encoding match
     * Score range: 0.0 (garbage) to 1.0 (perfect)
     *
     * Factors:
     * - Penalty for replacement characters (�)
     * - Penalty for excessive control characters
     * - Bonus for printable characters
     * - Bonus for common language patterns
     */
    private static double scoreDecodedText(String text, int originalByteLength) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        int length = text.length();
        int printableCount = 0;
        int replacementCount = 0;
        int controlCount = 0;
        int commonChars = 0;

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);

            // Count replacement characters (U+FFFD)
            if (c == '\uFFFD' || c == '�') {
                replacementCount++;
                continue;
            }

            // Count control characters (excluding common ones like \n, \r, \t)
            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                controlCount++;
                continue;
            }

            // Count printable characters
            if (c >= 32 && c <= 126) {
                printableCount++;
                // Bonus for very common ASCII characters
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == ',') {
                    commonChars++;
                }
            } else if (c > 126) {
                // Non-ASCII printable (like Chinese, Arabic, Hebrew, etc.)
                printableCount++;
            }
        }

        // Calculate score
        double score = 1.0;

        // Heavy penalty for replacement characters (indicates wrong encoding)
        double replacementRatio = (double) replacementCount / length;
        score -= (replacementRatio * 2.0); // 2x penalty

        // Penalty for excessive control characters
        double controlRatio = (double) controlCount / length;
        score -= (controlRatio * 1.5);

        // Bonus for printable content
        double printableRatio = (double) printableCount / length;
        score *= printableRatio;

        // Bonus for common ASCII characters (suggests correct encoding)
        if (commonChars > length * 0.3) { // More than 30% common chars
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

        // Ensure score is in valid range
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
                break;

            case Data.BIND_RECEIVER_RESP:
            case Data.BIND_TRANSCEIVER_RESP:
                break;

            case Data.SUBMIT_SM_RESP:
            case Data.DELIVER_SM_RESP:
                break;

            default:
                break;
        }
    }

    @Override
    public void handleRequest(final Request request, SMPPRequestManager requestManager) throws IOException {
        final SMPPConnection me = this;
        this.service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    receive(request);
                } catch (IOException e) {
                    log.error(me.toString(), e);
                    errorTracker.captureError(
                        "SMPPReceiver.handleRequest",
                        e,
                        "handle-request-failed",
                        Map.of(
                            "operation", "handle_request"
                        )
                    );
                }
            }
        });
    }

    @Override
    public String getName() {
        return smppSim.getName() + "_" + bindType.name() + "-Receiver";
    }
}
