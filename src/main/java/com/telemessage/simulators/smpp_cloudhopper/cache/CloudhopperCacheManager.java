package com.telemessage.simulators.smpp_cloudhopper.cache;

import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperEncodingHandler;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperEncodingHandler.DecodingResult;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache manager for Cloudhopper SMPP implementation.
 *
 * Provides integration with MessagesCache service while preserving:
 * - Original raw bytes of messages
 * - Encoding information (original and actual used)
 * - Concatenation metadata
 * - Delivery receipt correlation
 *
 * Key features:
 * - Stores both encoded bytes and decoded text
 * - Tracks encoding fallback information
 * - Handles multi-part message caching
 * - Thread-safe operations
 * - Automatic cache cleanup (via MessagesCache)
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-21
 */
@Slf4j
@Component
public class CloudhopperCacheManager {

    private final MessagesCache messagesCache;

    // Local cache for concatenated message assembly
    private final Map<String, ConcatenatedMessageState> concatenationStates = new ConcurrentHashMap<>();

    @Autowired
    public CloudhopperCacheManager(MessagesCache messagesCache) {
        this.messagesCache = messagesCache;
    }

    /**
     * State tracking for multi-part concatenated messages.
     */
    private static class ConcatenatedMessageState {
        final String reference;
        final int totalParts;
        final Map<Integer, MessagePart> parts = new ConcurrentHashMap<>();
        final long firstPartTime = System.currentTimeMillis();
        volatile String assembledText = null;
        volatile byte[] assembledBytes = null;
        volatile String encoding = null;

        ConcatenatedMessageState(String reference, int totalParts) {
            this.reference = reference;
            this.totalParts = totalParts;
        }

        boolean isComplete() {
            return parts.size() == totalParts;
        }

        void assembleParts() {
            if (assembledText != null) {
                return; // Already assembled
            }

            StringBuilder textBuilder = new StringBuilder();
            int totalBytes = 0;

            // Calculate total size for byte array
            for (int i = 1; i <= totalParts; i++) {
                MessagePart part = parts.get(i);
                if (part != null && part.content != null) {
                    totalBytes += part.content.length;
                }
            }

            byte[] fullBytes = new byte[totalBytes];
            int position = 0;

            // Assemble in order
            for (int i = 1; i <= totalParts; i++) {
                MessagePart part = parts.get(i);
                if (part != null) {
                    textBuilder.append(part.text);
                    if (part.content != null && part.content.length > 0) {
                        System.arraycopy(part.content, 0, fullBytes, position, part.content.length);
                        position += part.content.length;
                    }
                    // Use encoding from first part
                    if (encoding == null && part.encoding != null) {
                        encoding = part.encoding;
                    }
                }
            }

            assembledText = textBuilder.toString();
            assembledBytes = fullBytes;
        }
    }

    /**
     * Individual message part data.
     */
    private static class MessagePart {
        final int partNumber;
        final String text;
        final byte[] content;
        final String encoding;

        MessagePart(int partNumber, String text, byte[] content, String encoding) {
            this.partNumber = partNumber;
            this.text = text;
            this.content = content;
            this.encoding = encoding;
        }
    }

    /**
     * Caches a submitted message (outgoing).
     *
     * @param submitSm The submit SM PDU
     * @param response The submit response with message ID
     * @param source Source address
     * @param destination Destination address
     * @param connectionName Connection name
     */
    public void cacheSubmittedMessage(SubmitSm submitSm, SubmitSmResp response,
                                     String source, String destination,
                                     String connectionName) {
        try {
            String messageId = response.getMessageId();
            if (messageId == null || messageId.isEmpty()) {
                messageId = CloudhopperUtils.generateMessageId();
            }

            // Get message content and encoding info
            byte[] messageBytes = submitSm.getShortMessage();
            if (messageBytes == null || messageBytes.length == 0) {
                // Check for message_payload TLV
                messageBytes = getMessagePayload(submitSm);
            }

            // Determine encoding
            byte dataCoding = submitSm.getDataCoding();
            String declaredEncoding = CloudhopperEncodingHandler.getEncodingFromDataCoding(dataCoding);

            // Decode with detection
            DecodingResult decodingResult = CloudhopperEncodingHandler.decodeWithDetection(
                messageBytes, declaredEncoding);

            // Create cache entry
            MessagesObject messageObj = new MessagesObject();
            messageObj.setFrom(source);
            messageObj.setTo(destination);
            messageObj.setProviderId(connectionName);
            messageObj.setText(decodingResult.text);
            messageObj.setMessageEncoding(decodingResult.encoding);
            messageObj.setId(messageId);
            messageObj.setMessageTime(String.valueOf(System.currentTimeMillis()));
            messageObj.setDir("OUT");
            messageObj.setImplementationType("Cloudhopper");

            // Store additional encoding metadata
            messageObj.setDeclaredEncoding(decodingResult.declaredEncoding);
            messageObj.setDetectedEncoding(decodingResult.encoding);
            messageObj.setEncodingConfidence(decodingResult.confidence);
            messageObj.setDataCoding(dataCoding);
            messageObj.setEncodingCorrected(!decodingResult.declaredEncoding.equals(decodingResult.encoding));

            // Store raw bytes
            if (messageBytes != null && messageBytes.length > 0) {
                messageObj.setRawMessageBytes(messageBytes);
            }

            // Check for concatenation
            CloudhopperUtils.ConcatPart concatInfo = extractConcatenationInfo(submitSm);
            if (concatInfo != null) {
                messageObj.setReferenceNumber(concatInfo.reference);
                messageObj.setTotalParts(concatInfo.totalParts);
                messageObj.setPartNumber(concatInfo.partNumber);
                messageObj.setConcatenationType(concatInfo.type.toString());

                // Handle concatenated message assembly
                handleConcatenatedMessage(messageId, concatInfo, decodingResult.text,
                    messageBytes, decodingResult.encoding, messageObj);
            }

            // Store in cache
            messagesCache.getMap().put(messageId, messageObj);
            messagesCache.setDirty(true);

            log.debug("Cached submitted message: id={}, encoding={}, size={} bytes",
                messageId, decodingResult.encoding, messageBytes != null ? messageBytes.length : 0);

        } catch (Exception e) {
            log.error("Failed to cache submitted message", e);
        }
    }

    /**
     * Caches a received/delivered message (incoming).
     *
     * @param deliverSm The deliver SM PDU
     * @param source Source address
     * @param destination Destination address
     * @param connectionName Connection name
     */
    public void cacheReceivedMessage(DeliverSm deliverSm, String source, String destination,
                                    String connectionName) {
        try {
            // Generate or extract message ID
            String messageId = extractMessageId(deliverSm);
            if (messageId == null || messageId.isEmpty()) {
                messageId = CloudhopperUtils.generateMessageId();
            }

            // Get message content
            byte[] messageBytes = deliverSm.getShortMessage();
            if (messageBytes == null || messageBytes.length == 0) {
                // Check for message_payload TLV
                messageBytes = getMessagePayload(deliverSm);
            }

            // Determine encoding
            byte dataCoding = deliverSm.getDataCoding();
            String declaredEncoding = CloudhopperEncodingHandler.getEncodingFromDataCoding(dataCoding);

            // Decode with detection
            DecodingResult decodingResult = CloudhopperEncodingHandler.decodeWithDetection(
                messageBytes, declaredEncoding);

            // Create cache entry
            MessagesObject messageObj = new MessagesObject();
            messageObj.setFrom(source);
            messageObj.setTo(destination);
            messageObj.setProviderId(connectionName);
            messageObj.setText(decodingResult.text);
            messageObj.setMessageEncoding(decodingResult.encoding);
            messageObj.setId(messageId);
            messageObj.setMessageTime(String.valueOf(System.currentTimeMillis()));
            messageObj.setDir("IN");
            messageObj.setImplementationType("Cloudhopper");

            // Store additional encoding metadata
            messageObj.setDeclaredEncoding(decodingResult.declaredEncoding);
            messageObj.setDetectedEncoding(decodingResult.encoding);
            messageObj.setEncodingConfidence(decodingResult.confidence);
            messageObj.setDataCoding(dataCoding);
            messageObj.setEncodingCorrected(!decodingResult.declaredEncoding.equals(decodingResult.encoding));

            // Store raw bytes
            if (messageBytes != null && messageBytes.length > 0) {
                messageObj.setRawMessageBytes(messageBytes);
            }

            // Check for concatenation
            CloudhopperUtils.ConcatPart concatInfo = CloudhopperUtils.extractConcatenationData(deliverSm);
            if (concatInfo != null) {
                messageObj.setReferenceNumber(concatInfo.reference);
                messageObj.setTotalParts(concatInfo.totalParts);
                messageObj.setPartNumber(concatInfo.partNumber);
                messageObj.setConcatenationType(concatInfo.type.toString());

                // Handle concatenated message assembly
                handleConcatenatedMessage(messageId, concatInfo, decodingResult.text,
                    messageBytes, decodingResult.encoding, messageObj);
            }

            // Check if it's a delivery receipt
            if (isDeliveryReceipt(decodingResult.text)) {
                messageObj.setDeliveryReceiptShortMessage(decodingResult.text);
                messageObj.setDeliveryReceiptTime(String.valueOf(System.currentTimeMillis()));
            }

            // Store in cache
            messagesCache.getMap().put(messageId, messageObj);
            messagesCache.setDirty(true);

            log.debug("Cached received message: id={}, encoding={}, size={} bytes",
                messageId, decodingResult.encoding, messageBytes != null ? messageBytes.length : 0);

        } catch (Exception e) {
            log.error("Failed to cache received message", e);
        }
    }

    /**
     * Handles concatenated message assembly and caching.
     */
    private void handleConcatenatedMessage(String messageId, CloudhopperUtils.ConcatPart concatInfo,
                                          String text, byte[] content, String encoding,
                                          MessagesObject messageObj) {

        String concatKey = generateConcatKey(messageObj.getFrom(), messageObj.getTo(),
                                            concatInfo.reference);

        ConcatenatedMessageState state = concatenationStates.computeIfAbsent(concatKey,
            k -> new ConcatenatedMessageState(String.valueOf(concatInfo.reference), concatInfo.totalParts));

        // Add part
        state.parts.put(concatInfo.partNumber,
            new MessagePart(concatInfo.partNumber, text, content, encoding));

        // Check if complete
        if (state.isComplete()) {
            state.assembleParts();

            // Create assembled message cache entry
            String assembledId = messageId + "_FULL";
            MessagesObject assembledObj = new MessagesObject();
            assembledObj.setFrom(messageObj.getFrom());
            assembledObj.setTo(messageObj.getTo());
            assembledObj.setProviderId(messageObj.getProviderId());
            assembledObj.setText(state.assembledText);
            assembledObj.setMessageEncoding(state.encoding != null ? state.encoding : encoding);
            assembledObj.setId(assembledId);
            assembledObj.setMessageTime(String.valueOf(System.currentTimeMillis()));
            assembledObj.setDir(messageObj.getDir());
            assembledObj.setImplementationType("Cloudhopper");

            // Set concatenation info for assembled message
            assembledObj.setConcatenationType("ASSEMBLED");
            assembledObj.setTotalParts(concatInfo.totalParts);
            assembledObj.setReferenceNumber(concatInfo.reference);

            if (state.assembledBytes != null) {
                assembledObj.setRawMessageBytes(state.assembledBytes);
            }

            // Store assembled message
            messagesCache.getMap().put(assembledId, assembledObj);
            messagesCache.setDirty(true);

            log.info("Assembled concatenated message: {} parts, {} bytes, reference={}",
                concatInfo.totalParts, state.assembledBytes != null ? state.assembledBytes.length : 0,
                concatInfo.reference);

            // Clean up state
            concatenationStates.remove(concatKey);
        }
    }

    // Helper methods

    private CloudhopperUtils.ConcatPart extractConcatenationInfo(SubmitSm submitSm) {
        // Check ESM class for UDHI
        byte esmClass = submitSm.getEsmClass();
        if ((esmClass & CloudhopperUtils.ESM_CLASS_UDHI) == CloudhopperUtils.ESM_CLASS_UDHI) {
            // UDHI concatenation - would need to parse UDH from message bytes
            // For now, return null as this needs more complex parsing
            return null;
        }

        // Check for SAR TLVs
        // Would need to check optional parameters for SAR tags
        // For now, return null
        return null;
    }

    private byte[] getMessagePayload(SubmitSm submitSm) {
        try {
            // Check for message_payload TLV (0x0424)
            if (submitSm.getOptionalParameters() != null) {
                for (var tlv : submitSm.getOptionalParameters()) {
                    if (tlv.getTag() == CloudhopperUtils.TLV_MESSAGE_PAYLOAD) {
                        return tlv.getValue();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No message_payload TLV found", e);
        }
        return null;
    }

    private byte[] getMessagePayload(DeliverSm deliverSm) {
        try {
            // Check for message_payload TLV (0x0424)
            if (deliverSm.getOptionalParameters() != null) {
                for (var tlv : deliverSm.getOptionalParameters()) {
                    if (tlv.getTag() == CloudhopperUtils.TLV_MESSAGE_PAYLOAD) {
                        return tlv.getValue();
                    }
                }
            }
        } catch (Exception e) {
            log.debug("No message_payload TLV found", e);
        }
        return null;
    }

    private String extractMessageId(DeliverSm deliverSm) {
        // Try to extract message ID from receipted_message_id TLV or generate new
        try {
            if (deliverSm.getOptionalParameters() != null) {
                for (var tlv : deliverSm.getOptionalParameters()) {
                    if (tlv.getTag() == 0x001E) { // receipted_message_id
                        return new String(tlv.getValue(), StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract message ID from TLV", e);
        }
        return null;
    }

    private boolean isDeliveryReceipt(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // Check for delivery receipt pattern
        return text.contains("id:") && text.contains("stat:") &&
               (text.contains("sub:") || text.contains("dlvrd:"));
    }

    private String extractOriginalMessageId(String deliveryReceiptText) {
        if (deliveryReceiptText == null) {
            return null;
        }
        // Extract message ID from delivery receipt text
        // Format: "id:123456 sub:001 dlvrd:001..."
        int idIndex = deliveryReceiptText.indexOf("id:");
        if (idIndex >= 0) {
            int spaceIndex = deliveryReceiptText.indexOf(' ', idIndex);
            if (spaceIndex > idIndex + 3) {
                return deliveryReceiptText.substring(idIndex + 3, spaceIndex);
            } else if (idIndex + 3 < deliveryReceiptText.length()) {
                return deliveryReceiptText.substring(idIndex + 3);
            }
        }
        return null;
    }

    private String generateConcatKey(String source, String dest, int reference) {
        return source + "|" + dest + "|" + reference;
    }

    /**
     * Retrieves a cached message by ID.
     *
     * @param messageId Message ID
     * @return Cached message object or null
     */
    public MessagesObject getCachedMessage(String messageId) {
        return messagesCache.getMap().get(messageId);
    }

    /**
     * Clears expired concatenation states (cleanup).
     * Called periodically to prevent memory leaks.
     */
    public void cleanupExpiredConcatenationStates() {
        long now = System.currentTimeMillis();
        long maxAge = 3600000; // 1 hour

        concatenationStates.entrySet().removeIf(entry -> {
            ConcatenatedMessageState state = entry.getValue();
            return (now - state.firstPartTime) > maxAge;
        });
    }
}