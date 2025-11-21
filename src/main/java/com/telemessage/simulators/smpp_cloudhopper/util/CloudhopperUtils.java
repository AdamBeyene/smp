package com.telemessage.simulators.smpp_cloudhopper.util;

import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.tlv.TlvConvertException;
import com.cloudhopper.smpp.type.Address;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import com.telemessage.simulators.smpp.SimUtils;
import com.telemessage.simulators.smpp.concatenation.ConcatenationData;
import com.telemessage.simulators.smpp_cloudhopper.concatenation.CloudhopperConcatenationType;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperEncodingHandler.EncodingResult;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperEncodingHandler.DecodingResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility class for Cloudhopper SMPP operations.
 *
 * <p>Provides helper methods for:</p>
 * <ul>
 *   <li>PDU creation and manipulation</li>
 *   <li>Message encoding and decoding</li>
 *   <li>Concatenation handling (UDHI, SAR, PAYLOAD)</li>
 *   <li>Address conversion</li>
 *   <li>Message ID generation</li>
 * </ul>
 *
 * <p>This class integrates with the existing SimUtils from Logica implementation
 * to reuse encoding and concatenation logic.</p>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 */
@Slf4j
public final class CloudhopperUtils {

    private static final AtomicInteger MESSAGE_ID_COUNTER = new AtomicInteger(1);

    // SMPP Constants
    public static final byte ESM_CLASS_DEFAULT = 0x00;
    public static final byte ESM_CLASS_UDHI = 0x40;  // User Data Header Indicator
    public static final byte ESM_CLASS_REPLY_PATH = 0x04;

    // Message length limits (from existing SimUtils constants)
    public static final int MAX_SINGLE_ASCII = 160;
    public static final int MAX_SINGLE_UNICODE = 70;
    public static final int MAX_CONCAT_ASCII = 153;  // 160 - 7 bytes UDH header
    public static final int MAX_CONCAT_UNICODE = 67; // 70 - 3 bytes UDH header

    // SAR TLV tags
    public static final short TLV_SAR_MSG_REF_NUM = 0x020C;
    public static final short TLV_SAR_TOTAL_SEGMENTS = 0x020E;
    public static final short TLV_SAR_SEGMENT_SEQNUM = 0x020F;
    public static final short TLV_MESSAGE_PAYLOAD = 0x0424;

    private CloudhopperUtils() {
        // Private constructor to prevent instantiation
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Generates a unique message ID.
     *
     * @return Unique message ID as String
     */
    public static String generateMessageId() {
        int id = MESSAGE_ID_COUNTER.getAndIncrement();
        if (id > 999999) {
            MESSAGE_ID_COUNTER.set(1);
        }
        return String.format("%06d", id);
    }

    /**
     * Generates a reference number for concatenated messages (1-255).
     *
     * @return Reference number
     */
    public static int generateReferenceNumber() {
        return (int) (System.currentTimeMillis() % 255) + 1;
    }

    /**
     * Creates an Address object from string parameters.
     *
     * @param ton Type of Number
     * @param npi Numbering Plan Indicator
     * @param address Address string
     * @return Cloudhopper Address object
     */
    public static Address createAddress(byte ton, byte npi, String address) {
        try {
            return new Address(ton, npi, address);
        } catch (Exception e) {
            log.error("Failed to create address: ton={}, npi={}, address={}", ton, npi, address, e);
            // Return default address on error
            return new Address((byte) 0, (byte) 0, address != null ? address : "");
        }
    }

    /**
     * Determines if a message needs concatenation based on text length and encoding.
     *
     * @param text Message text
     * @param encoding Character encoding (GSM7, UCS2, UTF-8, etc.)
     * @return true if concatenation needed
     */
    public static boolean needsConcatenation(String text, String encoding) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        int maxLength = isUnicodeEncoding(encoding) ? MAX_SINGLE_UNICODE : MAX_SINGLE_ASCII;

        // Use our own encoding to get actual byte length
        try {
            byte[] encoded = encodeMessage(text, encoding);
            return encoded.length > maxLength;
        } catch (Exception e) {
            // Fall back to character count
            return text.length() > maxLength;
        }
    }

    /**
     * Checks if encoding is Unicode-based.
     *
     * @param encoding Encoding name
     * @return true if Unicode encoding
     */
    public static boolean isUnicodeEncoding(String encoding) {
        if (encoding == null) {
            return false;
        }
        String enc = encoding.toUpperCase();
        return enc.contains("UCS2") ||
               enc.contains("UTF-16") ||
               enc.contains("UNICODE");
    }

    /**
     * Gets the data coding byte for a given encoding.
     * Delegates to CloudhopperEncodingHandler for full encoding support.
     *
     * @param encoding Encoding name (GSM7, UCS2, UTF-8, etc.)
     * @return Data coding byte
     */
    public static byte getDataCoding(String encoding) {
        return CloudhopperEncodingHandler.getDataCodingForEncoding(encoding);
    }

    /**
     * Splits a long message into parts for concatenation.
     *
     * @param text Full message text
     * @param encoding Character encoding
     * @param concatenationType Concatenation type (UDHI, SAR, etc.)
     * @return List of message parts
     */
    public static List<String> splitMessage(String text, String encoding,
            CloudhopperConcatenationType concatenationType) {
        List<String> parts = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return parts;
        }

        boolean isUnicode = isUnicodeEncoding(encoding);
        int maxPartLength = isUnicode ? MAX_CONCAT_UNICODE : MAX_CONCAT_ASCII;

        // For PAYLOAD type, send as single part with message_payload TLV
        if (concatenationType == CloudhopperConcatenationType.PAYLOAD) {
            parts.add(text);
            return parts;
        }

        // Split message into chunks
        int length = text.length();
        for (int i = 0; i < length; i += maxPartLength) {
            int end = Math.min(i + maxPartLength, length);
            parts.add(text.substring(i, end));
        }

        return parts;
    }

    /**
     * Creates UDHI (User Data Header) for concatenated messages.
     *
     * @param referenceNumber Reference number (1-255)
     * @param totalParts Total number of parts
     * @param partNumber Current part number (1-based)
     * @return UDH byte array
     */
    public static byte[] createUdhiHeader(int referenceNumber, int totalParts, int partNumber) {
        return new byte[]{
            0x05,  // UDH Length (5 bytes following)
            0x00,  // IEI: Concatenated short messages, 8-bit reference
            0x03,  // IEDL: Length of data
            (byte) (referenceNumber & 0xFF),  // Reference number
            (byte) (totalParts & 0xFF),       // Total parts
            (byte) (partNumber & 0xFF)        // Part number
        };
    }

    /**
     * Internal wrapper for concatenation data with message content.
     */
    public static class ConcatPart {
        public final CloudhopperConcatenationType type;
        public final int reference;
        public final int totalParts;
        public final int partNumber;
        public final byte[] content;

        public ConcatPart(CloudhopperConcatenationType type, int reference, int totalParts, int partNumber, byte[] content) {
            this.type = type;
            this.reference = reference;
            this.totalParts = totalParts;
            this.partNumber = partNumber;
            this.content = content;
        }

        // Getters for compatibility
        public int getReference() { return reference; }
        public int getTotalParts() { return totalParts; }
        public int getPartNumber() { return partNumber; }
        public byte[] getContent() { return content; }
    }

    /**
     * Extracts concatenation data from a DeliverSm PDU.
     *
     * @param deliverSm DeliverSm PDU
     * @return ConcatPart or null if not concatenated
     */
    public static ConcatPart extractConcatenationData(DeliverSm deliverSm) {
        // Check for UDHI in esm_class
        byte esmClass = deliverSm.getEsmClass();
        if ((esmClass & ESM_CLASS_UDHI) == ESM_CLASS_UDHI) {
            return extractUdhiConcatenationData(deliverSm.getShortMessage());
        }

        // Check for SAR TLVs
        if (deliverSm.getOptionalParameters() != null && !deliverSm.getOptionalParameters().isEmpty()) {
            return extractSarConcatenationData(deliverSm);
        }

        // Check for message_payload TLV
        try {
            Tlv payloadTlv = deliverSm.getOptionalParameter(TLV_MESSAGE_PAYLOAD);
            if (payloadTlv != null) {
                return new ConcatPart(
                    CloudhopperConcatenationType.PAYLOAD,
                    0, // No reference for payload
                    1, // Single part
                    1, // Part 1
                    null
                );
            }
        } catch (Exception e) {
            log.debug("No message_payload TLV found");
        }

        // Check for TEXT_BASE pattern ("1/3 message text")
        ConcatPart textBasePart = extractTextBaseConcatenationData(deliverSm);
        if (textBasePart != null) {
            return textBasePart;
        }

        return null; // Not a concatenated message
    }

    /**
     * Extracts TEXT_BASE concatenation data from message text.
     * Pattern: "part/total message text" (e.g., "1/3 Hello World")
     *
     * @param deliverSm DeliverSm PDU
     * @return ConcatPart or null if not TEXT_BASE pattern
     */
    private static ConcatPart extractTextBaseConcatenationData(DeliverSm deliverSm) {
        try {
            // Decode message text
            String text = decodeMessage(deliverSm.getShortMessage(), deliverSm.getDataCoding());
            if (text == null || text.isEmpty()) {
                return null;
            }

            // Pattern: "1/3 message text"
            Pattern pattern = Pattern.compile("^(\\d+)/(\\d+)\\s+(.*)$");
            Matcher matcher = pattern.matcher(text);

            if (matcher.matches()) {
                int partNumber = Integer.parseInt(matcher.group(1));
                int totalParts = Integer.parseInt(matcher.group(2));
                String actualText = matcher.group(3);

                // Validate part numbers
                if (partNumber < 1 || partNumber > totalParts || totalParts > 255) {
                    log.warn("Invalid TEXT_BASE pattern: part={}/{}", partNumber, totalParts);
                    return null;
                }

                // Generate reference number (consistent across all parts)
                int referenceNumber = generateTextBaseReference(deliverSm, totalParts, actualText);

                log.debug("TEXT_BASE detected: part {}/{}, ref={}, text='{}'",
                    partNumber, totalParts, referenceNumber,
                    actualText.length() > 50 ? actualText.substring(0, 50) + "..." : actualText);

                return new ConcatPart(
                    CloudhopperConcatenationType.TEXT_BASE,
                    referenceNumber,
                    totalParts,
                    partNumber,
                    actualText.getBytes(StandardCharsets.UTF_8)
                );
            }

            return null;
        } catch (Exception e) {
            log.debug("TEXT_BASE extraction failed", e);
            return null;
        }
    }

    /**
     * Generates a consistent reference number for TEXT_BASE concatenation.
     * Uses hash of source|destination|totalParts|textPreview to ensure
     * all parts of the same message get the same reference number.
     *
     * @param deliverSm DeliverSm PDU
     * @param totalParts Total number of parts
     * @param text Message text
     * @return Reference number (0-65535)
     */
    private static int generateTextBaseReference(DeliverSm deliverSm, int totalParts, String text) {
        String sourceAddr = deliverSm.getSourceAddress() != null
            ? deliverSm.getSourceAddress().getAddress() : "";
        String destAddr = deliverSm.getDestAddress() != null
            ? deliverSm.getDestAddress().getAddress() : "";

        // Create reference key: source|dest|totalParts|textPreview(first20chars)
        String textPreview = text.length() > 20 ? text.substring(0, 20) : text;
        String referenceKey = sourceAddr + "|" + destAddr + "|" + totalParts + "|" + textPreview;

        // Hash and convert to positive 16-bit value
        int hash = referenceKey.hashCode();
        return Math.abs(hash) & 0xFFFF;  // Keep only lower 16 bits, ensure positive
    }

    /**
     * Extracts UDHI concatenation data from message bytes.
     *
     * @param messageBytes Message bytes with UDH header
     * @return ConcatPart or null
     */
    private static ConcatPart extractUdhiConcatenationData(byte[] messageBytes) {
        if (messageBytes == null || messageBytes.length < 6) {
            return null;
        }

        try {
            int udhLength = messageBytes[0] & 0xFF;
            if (udhLength < 5 || messageBytes.length < udhLength + 1) {
                return null;
            }

            int iei = messageBytes[1] & 0xFF;
            if (iei != 0x00) { // Check for concat IEI
                return null;
            }

            int iedl = messageBytes[2] & 0xFF;
            if (iedl != 0x03) {
                return null;
            }

            int reference = messageBytes[3] & 0xFF;
            int totalParts = messageBytes[4] & 0xFF;
            int partNumber = messageBytes[5] & 0xFF;

            // Extract message content (skip UDH)
            byte[] content = new byte[messageBytes.length - udhLength - 1];
            System.arraycopy(messageBytes, udhLength + 1, content, 0, content.length);

            return new ConcatPart(
                CloudhopperConcatenationType.UDHI,
                reference,
                totalParts,
                partNumber,
                content
            );
        } catch (Exception e) {
            log.error("Failed to extract UDHI concatenation data", e);
            return null;
        }
    }

    /**
     * Extracts SAR concatenation data from TLV parameters.
     *
     * @param deliverSm DeliverSm PDU with TLV parameters
     * @return ConcatPart or null
     */
    private static ConcatPart extractSarConcatenationData(DeliverSm deliverSm) {
        try {
            Tlv refTlv = deliverSm.getOptionalParameter(TLV_SAR_MSG_REF_NUM);
            Tlv totalTlv = deliverSm.getOptionalParameter(TLV_SAR_TOTAL_SEGMENTS);
            Tlv seqTlv = deliverSm.getOptionalParameter(TLV_SAR_SEGMENT_SEQNUM);

            if (refTlv == null || totalTlv == null || seqTlv == null) {
                return null;
            }

            int reference = refTlv.getValueAsUnsignedShort();
            int totalParts = totalTlv.getValueAsUnsignedByte();
            int partNumber = seqTlv.getValueAsUnsignedByte();

            return new ConcatPart(
                CloudhopperConcatenationType.SAR,
                reference,
                totalParts,
                partNumber,
                deliverSm.getShortMessage()
            );
        } catch (TlvConvertException e) {
            log.error("Failed to extract SAR concatenation data", e);
            return null;
        }
    }

    /**
     * Decodes message text from bytes using specified encoding.
     * Uses CloudhopperEncodingHandler for advanced decoding with auto-detection.
     *
     * @param messageBytes Message bytes
     * @param dataCoding Data coding byte
     * @return Decoded message text
     */
    public static String decodeMessage(byte[] messageBytes, byte dataCoding) {
        if (messageBytes == null || messageBytes.length == 0) {
            return "";
        }

        // Get encoding from data coding
        String declaredEncoding = CloudhopperEncodingHandler.getEncodingFromDataCoding(dataCoding);

        // Decode with detection and correction
        DecodingResult result = CloudhopperEncodingHandler.decodeWithDetection(messageBytes, declaredEncoding);

        if (result.confidence < 0.9 && !result.encoding.equals(result.declaredEncoding)) {
            log.warn("Encoding mismatch detected. Declared: {}, Actual: {}, Confidence: {}",
                    result.declaredEncoding, result.encoding, result.confidence);
        }

        return result.text;
    }

    /**
     * Encodes message text to bytes using specified encoding.
     * Uses CloudhopperEncodingHandler for full charset support with fallback.
     *
     * @param text Message text
     * @param encoding Encoding name
     * @return Encoded message bytes
     */
    public static byte[] encodeMessage(String text, String encoding) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }

        // Encode with fallback support
        EncodingResult result = CloudhopperEncodingHandler.encodeWithFallback(text, encoding);

        if (result.usedFallback) {
            log.info("Used fallback encoding {} instead of requested {}",
                    result.encoding, result.originalEncoding);
        }

        return result.bytes;
    }

    /**
     * Smart encoding detection - delegates to CloudhopperEncodingHandler.
     * Kept for backward compatibility.
     *
     * @param rawBytes Raw message bytes
     * @param declaredEncoding Declared encoding (from data_coding)
     * @return Pair of (decoded text, actual encoding)
     * @deprecated Use CloudhopperEncodingHandler.decodeWithDetection directly
     */
    @Deprecated
    public static org.apache.commons.lang3.tuple.Pair<String, String> detectAndDecodeMessage(
            byte[] rawBytes, String declaredEncoding) {

        DecodingResult result = CloudhopperEncodingHandler.decodeWithDetection(rawBytes, declaredEncoding);
        return org.apache.commons.lang3.tuple.Pair.of(result.text, result.encoding);
    }

    /**
     * Creates a complete SubmitSm PDU for a message part.
     *
     * @param source Source address
     * @param destination Destination address
     * @param text Message text
     * @param encoding Character encoding
     * @param esmClass ESM class (including UDHI if needed)
     * @return Configured SubmitSm PDU
     */
    public static SubmitSm createSubmitSm(
            Address source,
            Address destination,
            String text,
            String encoding,
            byte esmClass) throws SmppInvalidArgumentException {

        SubmitSm submitSm = new SubmitSm();

        // Set addresses
        submitSm.setSourceAddress(source);
        submitSm.setDestAddress(destination);

        // Set data coding
        submitSm.setDataCoding(getDataCoding(encoding));

        // Set ESM class
        submitSm.setEsmClass(esmClass);

        // Encode and set message
        byte[] messageBytes = encodeMessage(text, encoding);
        submitSm.setShortMessage(messageBytes);

        // Set default values
        submitSm.setRegisteredDelivery((byte) 0x01); // Request delivery receipt

        return submitSm;
    }

    /**
     * Formats a delivery receipt in standard SMPP format.
     *
     * @param messageId Original message ID
     * @param status Delivery status (DELIVRD, EXPIRED, etc.)
     * @param submitDate Submit date (YYMMDDHHmm)
     * @param doneDate Done date (YYMMDDHHmm)
     * @param text Optional text (first 20 chars of original message)
     * @return Formatted delivery receipt
     */
    public static String formatDeliveryReceipt(
            String messageId,
            String status,
            String submitDate,
            String doneDate,
            String text) {

        String textPart = (text != null && !text.isEmpty())
            ? text.substring(0, Math.min(20, text.length()))
            : "";

        return String.format(
            "id:%s sub:001 dlvrd:001 submit date:%s done date:%s stat:%s err:000 text:%s",
            messageId, submitDate, doneDate, status, textPart
        );
    }

    /**
     * Validates if a session is in a state that allows message submission.
     *
     * @param state Session state
     * @return true if can submit messages
     */
    public static boolean canSubmitMessages(SessionState state) {
        return state != null &&
               (state == SessionState.BOUND ||
                state == SessionState.BOUND_TRX ||
                state == SessionState.BOUND_TX);
    }

    /**
     * Session state enumeration.
     */
    public enum SessionState {
        UNBOUND,
        BINDING,
        BOUND,
        BOUND_TX,
        BOUND_RX,
        BOUND_TRX,
        UNBINDING,
        CLOSED,
        FAILED
    }
}
