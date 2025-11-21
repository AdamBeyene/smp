package com.telemessage.simulators.smpp_cloudhopper.concatenation;

import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperEncodingHandler;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive concatenation handler for Cloudhopper SMPP implementation.
 * Supports all concatenation methods matching Logica implementation:
 *
 * 1. UDHI (User Data Header Indicator) - ESM class 0x40
 * 2. SAR (Segmentation and Reassembly) - TLV parameters
 * 3. PAYLOAD - message_payload TLV for large messages
 * 4. UDHI_PAYLOAD - Hybrid: UDH inside message_payload TLV
 * 5. TEXT_BASE - Pattern-based: "1/3 message text"
 *
 * Features:
 * - Automatic message splitting based on encoding
 * - Reference number generation and management
 * - Part assembly and validation
 * - Support for 8-bit and 16-bit references
 * - Thread-safe operations
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-21
 */
@Slf4j
@Component
public class CloudhopperConcatenationHandler {

    // Reference number generators
    private static final AtomicInteger REFERENCE_8BIT = new AtomicInteger(1);
    private static final AtomicInteger REFERENCE_16BIT = new AtomicInteger(1);

    // Maximum message sizes
    private static final int MAX_SINGLE_GSM7 = 160;
    private static final int MAX_SINGLE_UCS2 = 70;
    private static final int MAX_CONCAT_GSM7_UDHI = 153;  // 160 - 7 bytes UDH
    private static final int MAX_CONCAT_UCS2_UDHI = 67;   // 70 - 3 bytes UDH
    private static final int MAX_CONCAT_GSM7_SAR = 159;   // No UDH overhead with SAR
    private static final int MAX_CONCAT_UCS2_SAR = 69;    // No UDH overhead with SAR
    private static final int MAX_PAYLOAD_SIZE = 65536;    // 64KB max for message_payload

    // UDH IEI (Information Element Identifier) values
    private static final byte IEI_CONCAT_8BIT = 0x00;   // 8-bit reference
    private static final byte IEI_CONCAT_16BIT = 0x08;  // 16-bit reference

    /**
     * Splits a message into parts based on concatenation type and encoding.
     *
     * @param text Full message text
     * @param encoding Character encoding
     * @param concatenationType Type of concatenation to use
     * @return List of message parts ready for sending
     */
    public List<MessagePart> splitMessage(String text, String encoding,
                                         CloudhopperConcatenationType concatenationType) {

        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }

        // Check if concatenation is needed
        if (!needsConcatenation(text, encoding, concatenationType)) {
            // Single part message
            MessagePart single = new MessagePart();
            single.text = text;
            single.encoding = encoding;
            single.partNumber = 1;
            single.totalParts = 1;
            single.concatenationType = CloudhopperConcatenationType.DEFAULT;
            return List.of(single);
        }

        // Split based on concatenation type
        return switch (concatenationType) {
            case UDHI -> splitUDHI(text, encoding, false);
            case SAR -> splitSAR(text, encoding);
            case PAYLOAD -> splitPayload(text, encoding);
            case UDHI_PAYLOAD -> splitUDHIPayload(text, encoding);
            case TEXT_BASE -> splitTextBase(text, encoding);
            default -> splitUDHI(text, encoding, false); // Default to UDHI
        };
    }

    /**
     * Creates UDHI concatenated message parts.
     *
     * @param text Full message text
     * @param encoding Character encoding
     * @param use16BitRef Use 16-bit reference (IEI 0x08) instead of 8-bit (IEI 0x00)
     * @return List of UDHI message parts
     */
    private List<MessagePart> splitUDHI(String text, String encoding, boolean use16BitRef) {
        List<MessagePart> parts = new ArrayList<>();

        boolean isUnicode = CloudhopperEncodingHandler.needsUnicodeEncoding(text);
        int maxPartLength = isUnicode ? MAX_CONCAT_UCS2_UDHI : MAX_CONCAT_GSM7_UDHI;

        // Generate reference number
        int reference = use16BitRef ? generate16BitReference() : generate8BitReference();

        // Split text into parts
        List<String> textParts = splitTextIntoChunks(text, maxPartLength);
        int totalParts = textParts.size();

        if (totalParts > 255) {
            log.error("Message too long: {} parts exceed maximum of 255", totalParts);
            throw new IllegalArgumentException("Message requires " + totalParts + " parts, maximum is 255");
        }

        // Create parts with UDH headers
        for (int i = 0; i < totalParts; i++) {
            MessagePart part = new MessagePart();
            part.text = textParts.get(i);
            part.encoding = encoding;
            part.partNumber = i + 1;
            part.totalParts = totalParts;
            part.reference = reference;
            part.concatenationType = CloudhopperConcatenationType.UDHI;
            part.use16BitRef = use16BitRef;

            // Create UDH header
            part.udhHeader = createUDHHeader(reference, totalParts, i + 1, use16BitRef);

            parts.add(part);
        }

        log.debug("Split message into {} UDHI parts, reference={}, 16-bit={}",
                totalParts, reference, use16BitRef);

        return parts;
    }

    /**
     * Creates SAR concatenated message parts.
     *
     * @param text Full message text
     * @param encoding Character encoding
     * @return List of SAR message parts
     */
    private List<MessagePart> splitSAR(String text, String encoding) {
        List<MessagePart> parts = new ArrayList<>();

        boolean isUnicode = CloudhopperEncodingHandler.needsUnicodeEncoding(text);
        int maxPartLength = isUnicode ? MAX_CONCAT_UCS2_SAR : MAX_CONCAT_GSM7_SAR;

        // Generate 16-bit reference for SAR
        int reference = generate16BitReference();

        // Split text into parts
        List<String> textParts = splitTextIntoChunks(text, maxPartLength);
        int totalParts = textParts.size();

        if (totalParts > 255) {
            log.error("Message too long: {} parts exceed maximum of 255", totalParts);
            throw new IllegalArgumentException("Message requires " + totalParts + " parts, maximum is 255");
        }

        // Create parts with SAR TLVs
        for (int i = 0; i < totalParts; i++) {
            MessagePart part = new MessagePart();
            part.text = textParts.get(i);
            part.encoding = encoding;
            part.partNumber = i + 1;
            part.totalParts = totalParts;
            part.reference = reference;
            part.concatenationType = CloudhopperConcatenationType.SAR;

            parts.add(part);
        }

        log.debug("Split message into {} SAR parts, reference={}", totalParts, reference);

        return parts;
    }

    /**
     * Creates PAYLOAD message (single part, uses message_payload TLV).
     *
     * @param text Full message text
     * @param encoding Character encoding
     * @return List with single PAYLOAD message part
     */
    private List<MessagePart> splitPayload(String text, String encoding) {
        MessagePart part = new MessagePart();
        part.text = text;
        part.encoding = encoding;
        part.partNumber = 1;
        part.totalParts = 1;
        part.concatenationType = CloudhopperConcatenationType.PAYLOAD;

        // Check size limit
        byte[] encoded = CloudhopperEncodingHandler.encodeWithFallback(text, encoding).bytes;
        if (encoded.length > MAX_PAYLOAD_SIZE) {
            log.error("Message too large for PAYLOAD: {} bytes > {} max",
                    encoded.length, MAX_PAYLOAD_SIZE);
            throw new IllegalArgumentException("Message size " + encoded.length +
                    " exceeds maximum payload size of " + MAX_PAYLOAD_SIZE);
        }

        log.debug("Created PAYLOAD message, size={} bytes", encoded.length);

        return List.of(part);
    }

    /**
     * Creates UDHI_PAYLOAD concatenated message parts.
     * This is a hybrid approach: UDH headers inside message_payload TLV.
     *
     * @param text Full message text
     * @param encoding Character encoding
     * @return List of UDHI_PAYLOAD message parts
     */
    private List<MessagePart> splitUDHIPayload(String text, String encoding) {
        // Similar to UDHI but will be sent via message_payload TLV
        List<MessagePart> udhiParts = splitUDHI(text, encoding, false);

        // Mark as UDHI_PAYLOAD type
        for (MessagePart part : udhiParts) {
            part.concatenationType = CloudhopperConcatenationType.UDHI_PAYLOAD;
        }

        log.debug("Created {} UDHI_PAYLOAD parts", udhiParts.size());

        return udhiParts;
    }

    /**
     * Creates TEXT_BASE concatenated message parts.
     * Format: "1/3 message text"
     *
     * @param text Full message text
     * @param encoding Character encoding
     * @return List of TEXT_BASE message parts
     */
    private List<MessagePart> splitTextBase(String text, String encoding) {
        List<MessagePart> parts = new ArrayList<>();

        boolean isUnicode = CloudhopperEncodingHandler.needsUnicodeEncoding(text);

        // Account for pattern overhead "255/255 " (8 chars max)
        int patternOverhead = 8;
        int maxPartLength = (isUnicode ? MAX_SINGLE_UCS2 : MAX_SINGLE_GSM7) - patternOverhead;

        // Split text into parts
        List<String> textParts = splitTextIntoChunks(text, maxPartLength);
        int totalParts = textParts.size();

        if (totalParts > 255) {
            log.error("Message too long: {} parts exceed maximum of 255", totalParts);
            throw new IllegalArgumentException("Message requires " + totalParts + " parts, maximum is 255");
        }

        // Generate consistent reference for all parts
        int reference = text.hashCode() & 0xFFFF;

        // Create parts with TEXT_BASE pattern
        for (int i = 0; i < totalParts; i++) {
            MessagePart part = new MessagePart();
            // Format: "1/3 message text"
            part.text = String.format("%d/%d %s", i + 1, totalParts, textParts.get(i));
            part.encoding = encoding;
            part.partNumber = i + 1;
            part.totalParts = totalParts;
            part.reference = reference;
            part.concatenationType = CloudhopperConcatenationType.TEXT_BASE;

            parts.add(part);
        }

        log.debug("Split message into {} TEXT_BASE parts, reference={}", totalParts, reference);

        return parts;
    }

    /**
     * Applies concatenation to a SubmitSm PDU.
     *
     * @param submitSm The PDU to modify
     * @param part The message part with concatenation info
     * @throws SmppInvalidArgumentException if PDU modification fails
     */
    public void applyConcatenation(SubmitSm submitSm, MessagePart part)
            throws SmppInvalidArgumentException {

        if (part.concatenationType == null || part.concatenationType == CloudhopperConcatenationType.DEFAULT) {
            return; // No concatenation needed
        }

        switch (part.concatenationType) {
            case UDHI:
                applyUDHI(submitSm, part);
                break;
            case SAR:
                applySAR(submitSm, part);
                break;
            case PAYLOAD:
                applyPayload(submitSm, part);
                break;
            case UDHI_PAYLOAD:
                applyUDHIPayload(submitSm, part);
                break;
            case TEXT_BASE:
                // TEXT_BASE doesn't need special handling, text already includes pattern
                break;
            default:
                log.warn("Unknown concatenation type: {}", part.concatenationType);
        }
    }

    /**
     * Applies UDHI concatenation to SubmitSm.
     */
    private void applyUDHI(SubmitSm submitSm, MessagePart part)
            throws SmppInvalidArgumentException {

        // Set ESM class to indicate UDHI
        byte esmClass = submitSm.getEsmClass();
        esmClass |= CloudhopperUtils.ESM_CLASS_UDHI;
        submitSm.setEsmClass(esmClass);

        // Encode text
        byte[] textBytes = CloudhopperEncodingHandler.encodeWithFallback(part.text, part.encoding).bytes;

        // Combine UDH header with text
        byte[] messageWithUdh = new byte[part.udhHeader.length + textBytes.length];
        System.arraycopy(part.udhHeader, 0, messageWithUdh, 0, part.udhHeader.length);
        System.arraycopy(textBytes, 0, messageWithUdh, part.udhHeader.length, textBytes.length);

        // Set the complete message
        submitSm.setShortMessage(messageWithUdh);

        log.trace("Applied UDHI: part {}/{}, UDH={} bytes, text={} bytes",
                part.partNumber, part.totalParts, part.udhHeader.length, textBytes.length);
    }

    /**
     * Applies SAR concatenation to SubmitSm.
     */
    private void applySAR(SubmitSm submitSm, MessagePart part)
            throws SmppInvalidArgumentException {

        // Add SAR TLVs
        submitSm.addOptionalParameter(new Tlv(
            CloudhopperUtils.TLV_SAR_MSG_REF_NUM,
            ByteBuffer.allocate(2).putShort((short)part.reference).array()
        ));

        submitSm.addOptionalParameter(new Tlv(
            CloudhopperUtils.TLV_SAR_TOTAL_SEGMENTS,
            new byte[]{(byte)part.totalParts}
        ));

        submitSm.addOptionalParameter(new Tlv(
            CloudhopperUtils.TLV_SAR_SEGMENT_SEQNUM,
            new byte[]{(byte)part.partNumber}
        ));

        // Set message text (no UDH)
        byte[] textBytes = CloudhopperEncodingHandler.encodeWithFallback(part.text, part.encoding).bytes;
        submitSm.setShortMessage(textBytes);

        log.trace("Applied SAR: part {}/{}, reference={}, size={} bytes",
                part.partNumber, part.totalParts, part.reference, textBytes.length);
    }

    /**
     * Applies PAYLOAD concatenation to SubmitSm.
     */
    private void applyPayload(SubmitSm submitSm, MessagePart part)
            throws SmppInvalidArgumentException {

        // Encode text
        byte[] textBytes = CloudhopperEncodingHandler.encodeWithFallback(part.text, part.encoding).bytes;

        // Add message_payload TLV
        submitSm.addOptionalParameter(new Tlv(
            CloudhopperUtils.TLV_MESSAGE_PAYLOAD,
            textBytes
        ));

        // Clear short_message field (must be empty when using message_payload)
        submitSm.setShortMessage(new byte[0]);

        log.trace("Applied PAYLOAD: size={} bytes", textBytes.length);
    }

    /**
     * Applies UDHI_PAYLOAD concatenation to SubmitSm.
     * Combines UDH with message_payload TLV.
     */
    private void applyUDHIPayload(SubmitSm submitSm, MessagePart part)
            throws SmppInvalidArgumentException {

        // Set ESM class to indicate UDHI
        byte esmClass = submitSm.getEsmClass();
        esmClass |= CloudhopperUtils.ESM_CLASS_UDHI;
        submitSm.setEsmClass(esmClass);

        // Encode text
        byte[] textBytes = CloudhopperEncodingHandler.encodeWithFallback(part.text, part.encoding).bytes;

        // Combine UDH header with text
        byte[] messageWithUdh = new byte[part.udhHeader.length + textBytes.length];
        System.arraycopy(part.udhHeader, 0, messageWithUdh, 0, part.udhHeader.length);
        System.arraycopy(textBytes, 0, messageWithUdh, part.udhHeader.length, textBytes.length);

        // Add as message_payload TLV
        submitSm.addOptionalParameter(new Tlv(
            CloudhopperUtils.TLV_MESSAGE_PAYLOAD,
            messageWithUdh
        ));

        // Clear short_message field
        submitSm.setShortMessage(new byte[0]);

        log.trace("Applied UDHI_PAYLOAD: part {}/{}, UDH={} bytes, total={} bytes",
                part.partNumber, part.totalParts, part.udhHeader.length, messageWithUdh.length);
    }

    // Helper methods

    /**
     * Checks if a message needs concatenation.
     */
    private boolean needsConcatenation(String text, String encoding,
                                      CloudhopperConcatenationType type) {

        if (text == null || text.isEmpty()) {
            return false;
        }

        // PAYLOAD type can handle large messages without splitting
        if (type == CloudhopperConcatenationType.PAYLOAD) {
            byte[] encoded = CloudhopperEncodingHandler.encodeWithFallback(text, encoding).bytes;
            return encoded.length > 140; // Use PAYLOAD for messages > 140 bytes
        }

        // Check based on encoding
        boolean isUnicode = CloudhopperEncodingHandler.needsUnicodeEncoding(text);
        int maxSingle = isUnicode ? MAX_SINGLE_UCS2 : MAX_SINGLE_GSM7;

        byte[] encoded = CloudhopperEncodingHandler.encodeWithFallback(text, encoding).bytes;
        return encoded.length > maxSingle;
    }

    /**
     * Splits text into chunks of specified length.
     */
    private List<String> splitTextIntoChunks(String text, int maxLength) {
        List<String> chunks = new ArrayList<>();

        for (int i = 0; i < text.length(); i += maxLength) {
            int end = Math.min(i + maxLength, text.length());
            chunks.add(text.substring(i, end));
        }

        return chunks;
    }

    /**
     * Creates UDH header for concatenated message.
     */
    private byte[] createUDHHeader(int reference, int totalParts, int partNumber, boolean use16Bit) {
        if (use16Bit) {
            // 16-bit reference header (8 bytes total)
            return new byte[]{
                0x07,  // UDH Length (7 bytes following)
                IEI_CONCAT_16BIT,  // IEI for 16-bit concat
                0x04,  // IEDL: Length of data (4 bytes)
                (byte)(reference >> 8),    // Reference high byte
                (byte)(reference & 0xFF),   // Reference low byte
                (byte)totalParts,          // Total parts
                (byte)partNumber           // Part number
            };
        } else {
            // 8-bit reference header (6 bytes total)
            return new byte[]{
                0x05,  // UDH Length (5 bytes following)
                IEI_CONCAT_8BIT,   // IEI for 8-bit concat
                0x03,  // IEDL: Length of data (3 bytes)
                (byte)(reference & 0xFF),  // Reference number
                (byte)totalParts,          // Total parts
                (byte)partNumber           // Part number
            };
        }
    }

    /**
     * Generates 8-bit reference number (1-255).
     */
    private int generate8BitReference() {
        int ref = REFERENCE_8BIT.getAndIncrement();
        if (ref > 255) {
            REFERENCE_8BIT.set(1);
            ref = 1;
        }
        return ref;
    }

    /**
     * Generates 16-bit reference number (1-65535).
     */
    private int generate16BitReference() {
        int ref = REFERENCE_16BIT.getAndIncrement();
        if (ref > 65535) {
            REFERENCE_16BIT.set(1);
            ref = 1;
        }
        return ref;
    }

    /**
     * Message part data structure.
     */
    public static class MessagePart {
        public String text;
        public String encoding;
        public int partNumber;
        public int totalParts;
        public int reference;
        public CloudhopperConcatenationType concatenationType;
        public byte[] udhHeader;
        public boolean use16BitRef;

        @Override
        public String toString() {
            return String.format("Part[%d/%d, type=%s, ref=%d, size=%d]",
                partNumber, totalParts, concatenationType, reference,
                text != null ? text.length() : 0);
        }
    }
}