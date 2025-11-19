package com.telemessage.simulators.smpp;

import com.logica.smpp.Data;
import com.logica.smpp.pdu.SendMessageSM;
import com.logica.smpp.pdu.StandardSendMessageSM;
import com.logica.smpp.pdu.ValueNotSetException;
import com.logica.smpp.pdu.tlv.TLVInt;
import com.logica.smpp.pdu.tlv.TLVString;
import com.logica.smpp.pdu.tlv.WrongLengthException;
import com.logica.smpp.pdu.ShortMessage;
import com.telemessage.simulators.common.Utils;
import com.telemessage.simulators.common.conf.CombinedCharsetProvider;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.common.services.filemanager.SimFileManager;
import com.telemessage.simulators.smpp.concatenation.ConcatMessageContent;
import com.telemessage.simulators.smpp.concatenation.ConcatenationData;
import com.telemessage.simulators.smpp.concatenation.ConcatenationType;
import com.telemessage.simulators.smpp.conf.SMPPConnections;
import com.telemessage.qatools.error.ErrorTracker;
import lombok.extern.slf4j.Slf4j;
import net.freeutils.charset.CharsetProvider;
import net.freeutils.charset.gsm.GSMCharset;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.simpleframework.xml.core.Persister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.logica.smpp.util.ByteBuffer;
import static com.telemessage.simulators.smpp.SMPPConnection.isConvertToUnicode;

@Slf4j
@Configuration
public class SimUtils {

    private EnvConfiguration conf;
    private ErrorTracker errorTracker;

    @Autowired
    public SimUtils(EnvConfiguration conf, ErrorTracker errorTracker) {
        this.conf = conf;
        this.errorTracker = errorTracker;
    }

    public SMPPConnections readConfiguration(String CONN_FILE) throws Exception {
        String env = conf.getEnvCurrent();
        log.info("Using environment {}", env);
        Path filename = Paths.get(StringUtils.isEmpty(env) ? "" : env).resolve(CONN_FILE);
        log.info("SMPP conf file path :{}", filename);
        InputStream inputStream = SimFileManager.getResolvedResourcePath(filename.toString());
        return new Persister().read(SMPPConnections.class, inputStream);
    }

    protected static String cleanAddress(String address) {
        return StringUtils.remove(address, "+- \t");
    }

    // Add optional parameters (e.g., owner, message ID, message time)
    protected static void addOptionalParams(SMPPRequest req, SendMessageSM message) {
        if (req.getParams() != null) {
            req.getParams().forEach(param -> {
                try {
                    if ("owner".equals(param.get("tag"))) {
                        Integer ownerValue = Integer.valueOf(param.get("value"));
                        TLVInt tlv = new TLVInt();
                        tlv.setTag((short) 0x1926);
                        tlv.setValue(ownerValue);
                        message.setExtraOptional(tlv);
                    }
                    if ("messageid".equals(param.get("tag"))) {
                        String messageIdValue = param.get("value");
                        TLVString tlv = new TLVString();
                        tlv.setTag((short) 0x1927);
                        tlv.setValue(messageIdValue);
                        message.setExtraOptional(tlv);
                    }
                    if ("messagetime".equals(param.get("tag"))) {
                        String messageTimeValue = param.get("value");
                        TLVString tlv = new TLVString();
                        tlv.setTag((short) 0x1928);
                        tlv.setValue(messageTimeValue);
                        message.setExtraOptional(tlv);
                    }
                } catch (WrongLengthException e) {
                    log.error("Error setting optional parameters", e);
                }
            });
        }
    }


    /**
     * Checks if the text can be encoded in the specified charset.
     *
     * @param text        The text to be checked.
     * @param charsetName The charset name to check against.
     * @return True if the text can be encoded in the charset; false otherwise.
     */
    protected static boolean canBeDisplayedInCharset(String text, String charsetName) {
        try {
            Charset charset = Charset.forName(charsetName);
            return charset.newEncoder().canEncode(text);
        } catch (Exception e) {
            log.warn("Charset '{}' is not supported or cannot encode the text.", charsetName, e);
            return false;
        }
    }


    protected static boolean shouldConvertToUnicode(String text) {
        // Add logic to determine if text requires Unicode encoding
        return !StandardCharsets.US_ASCII.newEncoder().canEncode(text);
    }

    /**
     * Determines the SMPP Data Coding Scheme (DCS) based on encoding and Unicode requirements.
     *
     * @param isUnicode    Whether the text requires Unicode encoding.
     * @param inputEncoding The specified input encoding.
     * @param text          The text to be encoded (optional, for fallback logic).
     * @return The byte representing the DCS value.
     */
    protected static byte determineDataCoding(boolean isUnicode, String inputEncoding, String text) {
        if (isUnicode) {
            return (byte) 0x08; // UCS2 encoding for Unicode
        }

        switch (inputEncoding.toUpperCase()) {
            case "GSM7":
            case "SCGSM":
            case "GSM_DEFAULT":
                return (byte) 0x00; // GSM7 default alphabet
            case "ISO-8859-1":
                if (canBeDisplayedInCharset(text, "ISO-8859-1")) {
                    return (byte) 0x03; // Latin 1
                }
                break;
            case "ISO-8859-5":
                if (canBeDisplayedInCharset(text, "ISO-8859-5")) {
                    return (byte) 0x06; // Cyrillic
                }
                break;
            case "ISO-8859-8":
                if (canBeDisplayedInCharset(text, "ISO-8859-8")) {
                    return (byte) 0x07; // Hebrew
                }
                break;
            case "UTF-16BE":
            case "UTF-8":
                return (byte) 0x08; // UCS2 for Unicode
            case "ISO-8859-6-BIDI":
                return (byte) 0x06; // Arabic (handled same as ISO-8859-6)
            case "ISO-8859-8-BIDI":
                return (byte) 0x07; // Hebrew (handled same as ISO-8859-8)
            case "UTF-7":
            case "UTF-7-OPTIONAL":
                return (byte) 0x08; // Treat as UCS2 for compatibility
            case "KOI8-U":
                if (canBeDisplayedInCharset(text, "KOI8-U")) {
                    return (byte) 0x05; // KOI8-U (Russian/Ukrainian)
                }
                break;
            case "KZ-1048":
                if (canBeDisplayedInCharset(text, "KZ-1048")) {
                    return (byte) 0x06; // KZ-1048 (Kazakhstan)
                }
                break;
            case "MIK":
                if (canBeDisplayedInCharset(text, "MIK")) {
                    return (byte) 0x06; // MIK (Cyrillic)
                }
                break;
            default:
                if (canBeDisplayedInCharset(text, "US-ASCII")) {
                    return (byte) 0x00; // GSM7 default alphabet as a fallback
                }
        }

        // Default to GSM7 if nothing else matches
        return (byte) 0x00;
    }


    public static Pair<Byte, String> prepareDataCodingAndEnc(boolean isConvertToUnicode, String enc, byte dataCoding, String text) {
        if (isConvertToUnicode) {
            dataCoding = (byte) 8; // 8==UCS2
            enc = Data.ENC_UTF16_BE;
        } else if (enc.equals("Cp1252")) {
            dataCoding = (byte) 0;
        } else if (enc.equals("ISO-8859-5")) {
            dataCoding = (byte) 6;
        } else if (enc.equals("ISO-8859-8")) {
            dataCoding = (byte) 7;
        } else if (enc.equals("UTF-16BE") || enc.equals("UTF-8")) {
            dataCoding = (byte) 8;
        } else if (enc.equals("GSM7")||enc.equals("SCGSM") || enc.equals("CCGSM")) {
            log.warn("On enc.equals(\"GSM7\")||enc.equals(\"SCGSM\") || enc.equals(\"CCGSM\") using ISO-8859-1 and datacoding 3");
            /*dataCoding = (byte) 0;
            enc = "CCGSM";*/
            try {
                if (!isConvertToUnicode(text, "US-ASCII") && com.telemessage.simulators.common.Utils.canBeDisplayedInEnc(text, "ISO-8859-1")) {
                    dataCoding = (byte) 3; // 3==ISO-8859-1
                    enc = "ISO-8859-1";
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        } else {
            try {
                if (!isConvertToUnicode(text, "US-ASCII") && com.telemessage.simulators.common.Utils.canBeDisplayedInEnc(text, "ISO-8859-1")) {
                    dataCoding = (byte) 3; // 3==ISO-8859-1
                    enc = "ISO-8859-1";
                }
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
        return Pair.of(dataCoding , enc);
    }


    public static byte determineTONByAddressSuffix(String address) {
        if (address.endsWith("9991")) return (byte) 1;
        if (address.endsWith("9992")) return (byte) 2;
        return -1;
    }



    /**
     * Converts a byte array to its hexadecimal string representation.
     *
     * @param bytes The byte array to convert.
     * @return The hexadecimal string, or "null" if the input is null.
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }



    // Helper method to attempt decoding message content
    private static String tryDecode(SendMessageSM msg, String encodingName, boolean isFallback, SMPPConnection connectionContext) {
        // Try short message first
        try {
            // Attempt to decode the short message using the specified encoding.
            // This might throw if the internal byte representation is null or if decoding fails.
            String text = msg.getShortMessage(encodingName);
            // Check if decoding produced a non-empty, non-null string
            if (text != null && !text.isEmpty()) {
                if (isFallback) {
                    log.debug("Decoded short message with fallback encoding: {}", encodingName);
                } else {
                    log.debug("Decoded short message with encoding: {}", encodingName);
                }
                return text;
            }
        } catch (UnsupportedEncodingException e) { // More specific exceptions
            if (isFallback) {
                log.trace("Failed to decode short message with fallback encoding '{}': {}", encodingName, e.getMessage());
            } else {
                log.warn("Failed to decode short message with encoding '{}': {}. Will try payload or other fallbacks.", encodingName, e.getMessage());
            }
        } catch (Exception e) { // Catch-all for other unexpected issues from getShortMessage
            if (isFallback) {
                log.trace("Unexpected error decoding short message with fallback encoding '{}': {}", encodingName, e.getMessage());
            } else {
                log.warn("Unexpected error decoding short message with encoding '{}': {}. Will try payload or other fallbacks.", encodingName, e.getMessage());
            }
        }


        // Try payload if short message decoding failed, was empty, or short message part was not present/empty
        if (msg.hasMessagePayload()) { // Checks if the message_payload TLV is present
            try {
                ByteBuffer payload = msg.getMessagePayload(); // Gets the TLV value
                if (payload != null && payload.getBuffer() != null && payload.length() > 0) {
                    String text = new String(payload.getBuffer(), encodingName);
                    if (isFallback) {
                        log.debug("Decoded payload with fallback encoding: {}", encodingName);
                    } else {
                        log.debug("Decoded payload with encoding: {}", encodingName);
                    }
                    return text; // Assuming successful decoding if no exception and text is non-null
                }
            } catch (ValueNotSetException vnse) {
                log.trace("Message payload TLV present but value not set when trying encoding '{}'", encodingName);
            }
            catch (UnsupportedEncodingException uee) {
                if (isFallback) {
                    log.trace("Failed to decode payload with fallback encoding '{}': {}", encodingName, uee.getMessage());
                } else {
                    log.warn("Failed to decode payload with encoding '{}': {}. Will try other fallbacks.", encodingName, uee.getMessage());
                }
            }
            catch (Exception e) { // Catch-all for other unexpected issues
                if (isFallback) {
                    log.trace("Unexpected error decoding payload with fallback encoding '{}': {}", encodingName, e.getMessage());
                } else {
                    log.warn("Unexpected error decoding payload with encoding '{}': {}. Will try other fallbacks.", encodingName, e.getMessage());
                }
            }
        }
        return null; // Return null if decoding failed or no suitable content found with this encoding
    }

    public static String getMessageTextForCaching(SendMessageSM msg, SMPPConnection connectionContext) {
        String decodedText = null;
        Set<String> triedEncodings = new HashSet<>();

        // 1. Data coding hint (UCS2/UTF-16BE)
        if (msg.getDataCoding() == (byte)0x08) {
            String dcsHintEncoding = Data.ENC_UTF16_BE;
            decodedText = tryDecode(msg, dcsHintEncoding, false, connectionContext);
            triedEncodings.add(dcsHintEncoding.toUpperCase());
            if (decodedText != null) return decodedText;
        }

        // 2. UTF-8
        String utf8Encoding = StandardCharsets.UTF_8.name();
        if (!triedEncodings.contains(utf8Encoding.toUpperCase())) {
            decodedText = tryDecode(msg, utf8Encoding, false, connectionContext);
            triedEncodings.add(utf8Encoding.toUpperCase());
            if (decodedText != null) return decodedText;
        }

        // 3. Connection encoding
        if (connectionContext != null) {
            String primaryEncoding = connectionContext.getEncoding();
            if (primaryEncoding != null && !triedEncodings.contains(primaryEncoding.toUpperCase())) {
                decodedText = tryDecode(msg, primaryEncoding, false, connectionContext);
                triedEncodings.add(primaryEncoding.toUpperCase());
                if (decodedText != null) return decodedText;
            }
        }

        // 4. Cp1252
        String cp1252 = "Cp1252";
        if (!triedEncodings.contains(cp1252.toUpperCase())) {
            decodedText = tryDecode(msg, cp1252, false, connectionContext);
            triedEncodings.add(cp1252.toUpperCase());
            if (decodedText != null) return decodedText;
        }

        // 5. GSM7/SCGSM/CCGSM (using GSMCharset if available)
//        String[] gsmEncodings = {"GSM7", "SCGSM", "CCGSM"};
        List<String> gsmEncodings = List.of("GSM7", "SCGSM", "CCGSM");
        for (String gsmEnc : gsmEncodings) {
            if (!triedEncodings.contains(gsmEnc.toUpperCase())) {
                try {
                    // Try using GSMCharset if available
                    java.nio.charset.Charset cs = null;
                    try {
                        cs = new net.freeutils.charset.CharsetProvider().charsetForName(gsmEnc);
                    } catch (Exception e) {
                        // fallback: try Java's built-in
                        try {
                            cs = java.nio.charset.Charset.forName(gsmEnc);
                        } catch (Exception ignore) {}
                    }
                    if (cs != null) {
                        // Try short message first
                        String text = null;
                        try {
                            byte[] bytes = msg.getShortMessage() != null ? msg.getShortMessage().getBytes(cs) : null;
                            if (bytes != null) text = new String(bytes, cs);
                        } catch (Exception e) {}
                        if ((text == null || text.isEmpty()) && msg.hasMessagePayload()) {
                            try {
                                ByteBuffer payload = msg.getMessagePayload();
                                if (payload != null && payload.getBuffer() != null && payload.length() > 0) {
                                    text = new String(payload.getBuffer(), cs);
                                }
                            } catch (Exception e) {}
                        }
                        if (text != null && !text.isEmpty()) {
                            decodedText = text;
                            triedEncodings.add(gsmEnc.toUpperCase());
                            return decodedText;
                        }
                    }
                } catch (Exception e) {
                    // ignore and try next
                }
                triedEncodings.add(gsmEnc.toUpperCase());
            }
        }

        // 6. ISO-8859-1
        String iso88591 = StandardCharsets.ISO_8859_1.name();
        if (!triedEncodings.contains(iso88591.toUpperCase())) {
            decodedText = tryDecode(msg, iso88591, true, connectionContext);
            triedEncodings.add(iso88591.toUpperCase());
            if (decodedText != null) return decodedText;
        }

        // 7. UTF-16LE
        String utf16le = "UTF-16LE";
        if (!triedEncodings.contains(utf16le.toUpperCase())) {
            decodedText = tryDecode(msg, utf16le, true, connectionContext);
            triedEncodings.add(utf16le.toUpperCase());
            if (decodedText != null) return decodedText;
        }

        // 8. US-ASCII
        String ascii = StandardCharsets.US_ASCII.name();
        if (!triedEncodings.contains(ascii.toUpperCase())) {
            decodedText = tryDecode(msg, ascii, true, connectionContext);
            triedEncodings.add(ascii.toUpperCase());
            if (decodedText != null) return decodedText;
        }

        // If all decoding attempts fail, and there's a payload, return its hex representation
        if (msg.hasMessagePayload()) {
            try {
                ByteBuffer payloadBB = msg.getMessagePayload();
                if (payloadBB != null && payloadBB.getBuffer() != null && payloadBB.length() > 0) {
                    byte[] payloadBytes = payloadBB.getBuffer();
                    log.warn("All decoding attempts failed for message. Returning hex representation of payload.");
                    return "[MESSAGE_CONTENT_UNREADABLE:PAYLOAD_HEX:" + SimUtils.bytesToHex(payloadBytes) + "]";
                }
            } catch (ValueNotSetException vnse) {
                log.warn("Message payload TLV present but value not set when attempting final hex representation.");
            }
            catch (Exception e) {
                log.error("Error accessing payload buffer for final hex representation: {}", e.getMessage());
            }
        }

        log.warn("All decoding attempts failed for the message. No readable text could be extracted.");
        return "[MESSAGE_CONTENT_UNREADABLE]";
    }

    /**
     * Determines the correct encoding based on data coding scheme and message content
     */
    public static String determineEncoding(byte dataCoding, String messageContent) {
        // Check specific data coding values
        switch (dataCoding & 0x0F) {
            case 0x00: // GSM 7-bit default alphabet
                // Check if encoding is specified as CCGSM
                try {
                    String encoding = "CCGSM"; // Custom GSM7 encoding
                    if (messageContent != null && Utils.canBeDisplayedInEnc(messageContent, "ISO-8859-1")) {
                        return encoding;
                    }
                } catch (Exception e) {
                    log.debug("Failed to use CCGSM encoding, falling back to ISO-8859-1", e);
                }
                return "ISO-8859-1";
            case 0x08: // UCS2 (16-bit)
                return "UTF-16BE";
            case 0x03: // Latin-1
                return "ISO-8859-1";
            default:
                // For other cases, check if content needs Unicode
                try {
                    if (!Utils.canBeDisplayedInEnc(messageContent, "ISO-8859-1")) {
                        return "UTF-16BE";
                    }
                } catch (Exception e) {
                    log.warn("Error checking encoding, defaulting to ISO-8859-1", e);
                }
                return "ISO-8859-1";
        }
    }

    /**
     * Thread-safe method to extract message content based on concatenation type
     */
    public static synchronized ConcatMessageContent extractConcatMessageContent(
            StandardSendMessageSM<?> sm,
            ConcatenationType concatType,
            String encoding,
            ConcatenationData concatData,
            SMPPConnection me) {

        log.debug("Extracting message content for\nconcatenation type: {}\nencoding: {}\ndataCoding: 0x{}\nconcatData: {}",
                concatType, encoding, String.format("%02X", sm.getDataCoding()), concatData);
        try {
            Charset cs;
            List<String> gsmEncodings = List.of("GSM7", "SCGSM", "CCGSM");
            if(gsmEncodings.contains(encoding)){
                log.debug("Using CCGSM encoding");
                cs = new CharsetProvider().charsetForName("CCGSM");
            } else {
                log.debug("Using {} encoding", encoding);
                cs = Charset.forName(encoding);
            }
            String messageText;
            byte[] rawContent;
            byte[] messageBytes = null;
            int messageLength = 0;

            // Get RAW message bytes (CRITICAL: must get raw bytes, not decoded string)
            // The sm.getShortMessage() returns decoded string, we need raw bytes from PDU
            if (sm.hasMessagePayload()) {
                try {
                    ByteBuffer messagePayload = sm.getMessagePayload();
                    if (messagePayload != null && messagePayload.getBuffer() != null) {
                        messageBytes = messagePayload.getBuffer();
                        messageLength = messagePayload.length();
                        log.debug("Using message_payload (raw bytes), length: {}", messageBytes.length);
                    } else {
                        log.warn("Message payload is empty or not set");
                        return ConcatMessageContent.builder()
                                .success(false)
                                .error("Message payload is empty")
                                .build();
                    }
                } catch (ValueNotSetException ex) {
                    log.error("Failed to get message_payload", ex);
                    return ConcatMessageContent.builder()
                            .success(false)
                            .error("Failed to get message_payload: " + ex.getMessage())
                            .build();
                }
            } else {
                // CRITICAL FIX: Use reflection to get RAW bytes BEFORE SMPP library decodes them
                // The SMPP library decodes short_message using ASCII encoding by default,
                // which CORRUPTS all bytes > 0x7F (converts them to � U+FFFD).
                // We MUST access the raw byte[] BEFORE any String conversion happens.
                try {
                    ShortMessage shortMessageObj = null;

                    // Try multiple possible field names and search through class hierarchy
                    String[] possibleFieldNames = {"message", "shortMessage", "msg", "data"};
                    Class<?> currentClass = sm.getClass();

                    // Search through class hierarchy
                    while (currentClass != null && shortMessageObj == null) {
                        for (String fieldName : possibleFieldNames) {
                            try {
                                Field field = currentClass.getDeclaredField(fieldName);
                                field.setAccessible(true);
                                Object fieldValue = field.get(sm);

                                // Check if it's a ShortMessage object
                                if (fieldValue instanceof ShortMessage) {
                                    shortMessageObj = (ShortMessage) fieldValue;
                                    log.debug("Found ShortMessage in field '{}' of class {}",
                                        fieldName, currentClass.getSimpleName());
                                    break;
                                }
                            } catch (NoSuchFieldException e) {
                                // Try next field name
                            }
                        }
                        currentClass = currentClass.getSuperclass();
                    }

                    if (shortMessageObj != null) {
                        // Get the raw bytes from the ShortMessage object using getData()
                        ByteBuffer rawData = shortMessageObj.getData();
                        if (rawData != null && rawData.getBuffer() != null) {
                            messageBytes = rawData.getBuffer();
                            messageLength = rawData.length();
                            log.debug("Using short_message RAW bytes via reflection, length: {}", messageBytes.length);
                        } else {
                            log.error("ShortMessage getData() returned null");
                            return ConcatMessageContent.builder()
                                    .success(false)
                                    .error("No message data in ShortMessage")
                                    .build();
                        }
                    } else {
                        log.warn("Could not find ShortMessage field via reflection, using fallback");
                        throw new NoSuchFieldException("No ShortMessage field found");
                    }
                } catch (NoSuchFieldException | IllegalAccessException ex) {
                    // Fallback to old method if reflection fails
                    log.warn("Reflection failed, falling back to getShortMessage() with ISO-8859-1", ex);
                    try {
                        String shortMessage = sm.getShortMessage();
                        if (shortMessage != null) {
                            messageBytes = shortMessage.getBytes(StandardCharsets.ISO_8859_1);
                            messageLength = messageBytes.length;
                            log.debug("FALLBACK: Using short_message via ISO-8859-1, length: {}", messageBytes.length);
                        } else {
                            return ConcatMessageContent.builder()
                                    .success(false)
                                    .error("No message content available")
                                    .build();
                        }
                    } catch (Exception fallbackEx) {
                        log.error("Fallback also failed", fallbackEx);
                        return ConcatMessageContent.builder()
                                .success(false)
                                .error("Failed to get message bytes: " + fallbackEx.getMessage())
                                .build();
                    }
                } catch (Exception ex) {
                    log.error("Unexpected error getting short_message bytes", ex);
                    return ConcatMessageContent.builder()
                            .success(false)
                            .error("Failed to get message bytes: " + ex.getMessage())
                            .build();
                }
            }

            if (messageLength <= 0) {
                String error = "Message length after UDH calculation is negative Or 0: " + messageLength;
                log.error(error);
                return ConcatMessageContent.builder()
                        .messageText("")
                        .rawContent(new byte[0])
                        .success(false)
                        .error(error)
                        .build();
            }

            switch (concatType) {
                case UDHI:
                    int udhLen = messageBytes[0] & 0xFF;
                    rawContent = new byte[messageBytes.length - (udhLen + 1)];
                    System.arraycopy(messageBytes, udhLen + 1, rawContent, 0, rawContent.length);

                    // CRITICAL FIX: Use smart encoding detection instead of blindly using declared encoding
                    // This ensures each part is decoded correctly, even if not all parts arrive
                    Pair<String, String> udhiResult = detectAndDecodeMessage(rawContent, encoding);
                    messageText = udhiResult.getLeft();
                    String actualEncoding = udhiResult.getRight();

                    if (!actualEncoding.equals(encoding)) {
                        log.warn("UDHI part encoding corrected: Declared={}, Actual={}", encoding, actualEncoding);
                    }
                    log.debug("UDHI extracted text: {}, rawContent length: {}, encoding: {}",
                        messageText, rawContent.length, actualEncoding);
                    break;
                case SAR:
                    // Use the pre-extracted SAR parameters from concatData
                    log.debug("Processing SAR segment: ref={}, total={}, seq={}",
                            concatData.getConcatenatedMessageId(),
                            concatData.getConcatenatedMessageSize(),
                            concatData.getSegmentIndex());
                    if (concatData.getSegmentIndex() > concatData.getConcatenatedMessageSize()) {
                        log.warn("SAR segment sequence number {} exceeds total segments {}",
                                concatData.getSegmentIndex(),
                                concatData.getConcatenatedMessageSize());
                        return ConcatMessageContent.builder()
                                .success(false)
                                .error("Invalid SAR parameters: sequence number exceeds total")
                                .build();
                    }

                    // CRITICAL FIX: Use smart encoding detection for SAR parts too
                    rawContent = messageBytes;
                    Pair<String, String> sarResult = detectAndDecodeMessage(rawContent, encoding);
                    messageText = sarResult.getLeft();
                    String sarActualEncoding = sarResult.getRight();

                    if (!sarActualEncoding.equals(encoding)) {
                        log.warn("SAR part encoding corrected: Declared={}, Actual={}", encoding, sarActualEncoding);
                    }
                    log.debug("SAR extracted text: {}, rawContent length: {}, encoding: {}",
                        messageText, rawContent.length, sarActualEncoding);
                    break;
                case TEXT_BASE:
                    // For TEXT_BASE, extract the actual message part from the pattern group
                    // Pattern: "^(\\d+)/(\\d+)\\s+(.*)$"
                    String msg = sm.getShortMessage();
                    String partText = "";
                    if (msg != null) {
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+)/(\\d+)\\s+(.*)$");
                        java.util.regex.Matcher matcher = pattern.matcher(msg);
                        if (matcher.matches()) {
                            partText = matcher.group(3);
                        }
                    }
                    messageText = partText;
                    rawContent = partText.getBytes(cs);
                    break;
                case DEFAULT:
                    // For non-concatenated messages, use the full message
                    rawContent = messageBytes;
                    messageText = new String(rawContent, cs);
                    log.debug("DEFAULT extracted text: {}, rawContent length: {}", messageText, rawContent.length);
                    break;
                default:
                    return ConcatMessageContent.builder()
                            .success(false)
                            .error("Unknown concatenation type: " + concatType)
                            .build();
            }

            return ConcatMessageContent.builder()
                    .messageText(messageText)
                    .rawContent(rawContent)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Error extracting concat message content", e);
            return ConcatMessageContent.builder()
                    .success(false)
                    .error("Error extracting message content: " + e.getMessage())
                    .build();
        }
    }


    protected static String createString(ByteBuffer udh, String encoding) {
        Charset actualCharset;
        List<String> gsmEncodings = List.of("GSM7", "SCGSM", "CCGSM");
        if(gsmEncodings.contains(encoding)){
            CombinedCharsetProvider provider = new CombinedCharsetProvider();
            actualCharset = provider.charsetForName("CCGSM");
        } else {
            actualCharset = Charset.forName(encoding);
        }
        return new String(udh.getBuffer(), actualCharset);
    }

    /**
     * Build smart encoding priority list based on declared encoding
     *
     * This handles common encoding confusion patterns for ANY encoding.
     * Same implementation as in SMPPReceiver and SMPPTransceiver.
     */
    private static String[] buildEncodingPriorityList(String declaredEncoding) {
        if (declaredEncoding == null || declaredEncoding.isEmpty()) {
            return new String[]{"UTF-8", "UTF-16BE", "UTF-16LE", "ISO-8859-1", "Cp1252"};
        }

        String normalized = declaredEncoding.toUpperCase();

        if (normalized.contains("UTF-16BE") || normalized.equals("UCS2")) {
            return new String[]{declaredEncoding, "UTF-16LE", "UTF-8", "ISO-8859-1", "Cp1252"};
        }
        if (normalized.contains("UTF-16LE")) {
            return new String[]{declaredEncoding, "UTF-16BE", "UTF-8", "ISO-8859-1", "Cp1252"};
        }
        if (normalized.contains("UTF-8") || normalized.equals("UTF8")) {
            return new String[]{declaredEncoding, "ISO-8859-1", "Cp1252", "UTF-16BE", "UTF-16LE"};
        }
        if (normalized.contains("ISO-8859-1") || normalized.equals("LATIN1")) {
            return new String[]{declaredEncoding, "UTF-8", "Cp1252", "UTF-16BE", "UTF-16LE"};
        }
        if (normalized.contains("1252") || normalized.equals("CP1252")) {
            return new String[]{declaredEncoding, "ISO-8859-1", "UTF-8", "UTF-16BE", "UTF-16LE"};
        }
        if (normalized.contains("GSM") || normalized.contains("CCGSM") || normalized.contains("SCGSM")) {
            return new String[]{declaredEncoding, "ISO-8859-1", "UTF-8", "Cp1252", "UTF-16BE"};
        }

        return new String[]{declaredEncoding, "UTF-8", "UTF-16BE", "UTF-16LE", "ISO-8859-1", "Cp1252"};
    }

    /**
     * Smart encoding detection and decoding for message parts
     * Same implementation as in SMPPReceiver and SMPPTransceiver.
     */
    private static Pair<String, String> detectAndDecodeMessage(byte[] rawBytes, String declaredEncoding) {
        if (rawBytes == null || rawBytes.length == 0) {
            return Pair.of("", declaredEncoding != null ? declaredEncoding : "UTF-8");
        }

        // CRITICAL: Check for UTF-16 endianness by analyzing null byte positions AND surrogate pairs
        // UTF-16LE ASCII: [83, 0, 84, 0, 65, 0, ...] - nulls at ODD positions (1, 3, 5, ...)
        // UTF-16BE ASCII: [0, 83, 0, 84, 0, 65, ...] - nulls at EVEN positions (0, 2, 4, ...)
        // UTF-16BE emojis: [D8, 3D, DE, 05, ...] - surrogate pairs (D800-DFFF range)
        if ("UTF-16BE".equalsIgnoreCase(declaredEncoding) || "UCS2".equalsIgnoreCase(declaredEncoding)) {
            int nullsAtOddPositions = 0;
            int nullsAtEvenPositions = 0;
            int surrogatePairsBE = 0;
            int surrogatePairsLE = 0;
            int totalBytes = Math.min(rawBytes.length, 100); // Check first 100 bytes

            for (int i = 0; i < totalBytes; i++) {
                if (rawBytes[i] == 0) {
                    if (i % 2 == 1) {
                        nullsAtOddPositions++;
                    } else {
                        nullsAtEvenPositions++;
                    }
                }

                // Check for UTF-16 surrogate pairs (emojis, rare characters)
                // High surrogate range: 0xD800-0xDBFF, Low surrogate: 0xDC00-0xDFFF
                if (i + 3 < rawBytes.length) {
                    // Check UTF-16BE pattern: [D8-DB, xx, DC-DF, xx]
                    int highBE = (rawBytes[i] & 0xFF);
                    int lowBE = (rawBytes[i+2] & 0xFF);
                    if (highBE >= 0xD8 && highBE <= 0xDB && lowBE >= 0xDC && lowBE <= 0xDF) {
                        surrogatePairsBE++;
                    }

                    // Check UTF-16LE pattern: [xx, D8-DB, xx, DC-DF]
                    int highLE = (rawBytes[i+1] & 0xFF);
                    int lowLE = (rawBytes[i+3] & 0xFF);
                    if (highLE >= 0xD8 && highLE <= 0xDB && lowLE >= 0xDC && lowLE <= 0xDF) {
                        surrogatePairsLE++;
                    }
                }
            }

            // PRIORITY 1: If we find surrogate pairs, they're the strongest indicator
            if (surrogatePairsBE > 0 || surrogatePairsLE > 0) {
                if (surrogatePairsBE > surrogatePairsLE) {
                    log.info("Detected UTF-16BE via surrogate pairs (BE: {}, LE: {}) - confirming declared {}",
                        surrogatePairsBE, surrogatePairsLE, declaredEncoding);
                    try {
                        String decoded = new String(rawBytes, Charset.forName("UTF-16BE"));
                        return Pair.of(decoded, "UTF-16BE");
                    } catch (Exception e) {
                        log.warn("Failed to decode as UTF-16BE despite surrogate pair detection", e);
                    }
                } else if (surrogatePairsLE > surrogatePairsBE) {
                    log.warn("Detected UTF-16LE via surrogate pairs (LE: {}, BE: {}) - correcting from declared {}",
                        surrogatePairsLE, surrogatePairsBE, declaredEncoding);
                    try {
                        String decoded = new String(rawBytes, Charset.forName("UTF-16LE"));
                        return Pair.of(decoded, "UTF-16LE");
                    } catch (Exception e) {
                        log.warn("Failed to decode as UTF-16LE despite surrogate pair detection", e);
                    }
                }
            }

            // PRIORITY 2: Check null byte positions (for ASCII content)
            // If significantly more nulls at odd positions, it's UTF-16LE not BE!
            if (nullsAtOddPositions > nullsAtEvenPositions * 2 && nullsAtOddPositions > 5) {
                log.warn("Detected UTF-16LE pattern (nulls at odd positions: {}, even: {}) - correcting from declared {}",
                    nullsAtOddPositions, nullsAtEvenPositions, declaredEncoding);
                try {
                    String decoded = new String(rawBytes, Charset.forName("UTF-16LE"));
                    return Pair.of(decoded, "UTF-16LE");
                } catch (Exception e) {
                    log.warn("Failed to decode as UTF-16LE despite pattern match", e);
                }
            }
            // If significantly more nulls at even positions, it's actually UTF-16BE
            else if (nullsAtEvenPositions > nullsAtOddPositions * 2 && nullsAtEvenPositions > 5) {
                log.info("Confirmed UTF-16BE pattern (nulls at even positions: {}, odd: {})",
                    nullsAtEvenPositions, nullsAtOddPositions);
            }
            // If no strong null byte pattern but declared UTF-16BE, trust the declaration
            else if (nullsAtOddPositions <= 5 && nullsAtEvenPositions <= 5) {
                log.info("No strong null pattern (emoji-heavy content?), trusting declared encoding: {}", declaredEncoding);
                try {
                    String decoded = new String(rawBytes, Charset.forName("UTF-16BE"));
                    return Pair.of(decoded, "UTF-16BE");
                } catch (Exception e) {
                    log.warn("Failed to decode as declared UTF-16BE", e);
                }
            }
        }

        String[] encodingsToTry = buildEncodingPriorityList(declaredEncoding);

        String bestText = null;
        String bestEncoding = declaredEncoding;
        double bestScore = -1;

        for (String encodingName : encodingsToTry) {
            if (encodingName == null || encodingName.isEmpty()) continue;

            try {
                Charset charset;
                List<String> gsmEncodings = List.of("GSM7", "SCGSM", "CCGSM", "GSM_DEFAULT");
                if (gsmEncodings.contains(encodingName.toUpperCase())) {
                    charset = new CombinedCharsetProvider().charsetForName(encodingName);
                } else {
                    charset = Charset.forName(encodingName);
                }

                String decoded = new String(rawBytes, charset);
                double score = scoreDecodedText(decoded, rawBytes.length);

                if (score > bestScore) {
                    bestScore = score;
                    bestText = decoded;
                    bestEncoding = encodingName;
                }

                // If excellent match found, stop searching
                if (score > 0.95) {
                    break;
                }
            } catch (Exception e) {
                log.debug("Failed to decode with {}: {}", encodingName, e.getMessage());
            }
        }

        if (bestText == null) {
            bestText = new String(rawBytes, StandardCharsets.ISO_8859_1);
            bestEncoding = "ISO-8859-1";
        }

        return Pair.of(bestText, bestEncoding);
    }

    /**
     * Score decoded text - same implementation as in SMPPReceiver and SMPPTransceiver
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

            if (c == '\uFFFD' || c == '�') {
                replacementCount++;
                continue;
            }

            if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                controlCount++;
                continue;
            }

            if (c >= 32 && c <= 126) {
                printableCount++;
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == ' ' || c == '.' || c == ',') {
                    commonChars++;
                }
            } else if (c > 126) {
                printableCount++;
            }
        }

        double score = 1.0;
        double replacementRatio = (double) replacementCount / length;
        score -= (replacementRatio * 2.0);

        double controlRatio = (double) controlCount / length;
        score -= (controlRatio * 1.5);

        double printableRatio = (double) printableCount / length;
        score *= printableRatio;

        if (commonChars > length * 0.3) {
            score *= 1.1;
        }

        if (originalByteLength > 0) {
            double charToByteRatio = (double) length / originalByteLength;

            if (charToByteRatio < 0.55 && charToByteRatio > 0.45) {
                int unicodeBlockChanges = countUnicodeBlockChanges(text);
                double blockChangeRatio = (double) unicodeBlockChanges / Math.max(1, length);

                if (blockChangeRatio > 0.5) {
                    score *= 0.15;
                }
            }

            if (charToByteRatio < 0.4) {
                score *= 0.2;
            }

            if (charToByteRatio > 3.0) {
                score *= 0.5;
            }
        }

        int nullCount = 0;
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) == '\0') nullCount++;
        }
        if (nullCount > 0) {
            double nullRatio = (double) nullCount / length;
            score -= (nullRatio * 2.0);
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Count Unicode block changes - same implementation as in SMPPReceiver and SMPPTransceiver
     */
    private static int countUnicodeBlockChanges(String text) {
        if (text == null || text.length() <= 1) {
            return 0;
        }

        int changes = 0;
        Character.UnicodeBlock previousBlock = null;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

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
}
