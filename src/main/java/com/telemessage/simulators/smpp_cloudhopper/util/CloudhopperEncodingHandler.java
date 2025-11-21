package com.telemessage.simulators.smpp_cloudhopper.util;

import com.cloudhopper.smpp.SmppConstants;
import com.telemessage.simulators.common.conf.CombinedCharsetProvider;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Advanced encoding handler for Cloudhopper SMPP implementation.
 * Provides full charset support matching the Logica implementation including:
 * - GSM7 variants (GSM7, SCGSM, CCGSM)
 * - Unicode (UTF-8, UTF-16BE/LE, UCS2)
 * - ISO-8859 variants (Latin, Cyrillic, Hebrew, Arabic)
 * - Windows codepages (Cp1252)
 * - Russian/Ukrainian (KOI8-U)
 * - Kazakhstan (KZ-1048)
 * - Cyrillic (MIK)
 *
 * Features:
 * - Automatic encoding detection
 * - Fallback chain support (GSM7 → ISO-8859-1 → UTF-8)
 * - Encoding mismatch detection and correction
 * - Raw byte preservation for exact reproduction
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-21
 */
@Slf4j
public class CloudhopperEncodingHandler {

    // Charset provider that includes GSM7 support
    private static final CombinedCharsetProvider CHARSET_PROVIDER = new CombinedCharsetProvider();

    // Cache for resolved charsets
    private static final Map<String, Charset> CHARSET_CACHE = new HashMap<>();

    // Encoding name mappings for consistency
    private static final Map<String, String> ENCODING_ALIASES = new HashMap<>();

    static {
        // Initialize encoding aliases
        ENCODING_ALIASES.put("GSM7", "X-Gsm7Bit");
        ENCODING_ALIASES.put("GSM_7BIT", "X-Gsm7Bit");
        ENCODING_ALIASES.put("GSM_DEFAULT", "X-Gsm7Bit");
        ENCODING_ALIASES.put("SCGSM", "SCGSM");
        ENCODING_ALIASES.put("CCGSM", "CCGSM");
        ENCODING_ALIASES.put("UCS2", "UTF-16BE");
        ENCODING_ALIASES.put("UTF16", "UTF-16BE");
        ENCODING_ALIASES.put("LATIN1", "ISO-8859-1");
        ENCODING_ALIASES.put("LATIN-1", "ISO-8859-1");
        ENCODING_ALIASES.put("CP1252", "Cp1252");
        ENCODING_ALIASES.put("WINDOWS-1252", "Cp1252");
        ENCODING_ALIASES.put("CYRILLIC", "ISO-8859-5");
        ENCODING_ALIASES.put("HEBREW", "ISO-8859-8");
        ENCODING_ALIASES.put("ARABIC", "ISO-8859-6");
    }

    /**
     * Result of encoding operation containing encoded bytes, actual encoding used,
     * and any fallback information.
     */
    public static class EncodingResult {
        public final byte[] bytes;
        public final String encoding;
        public final String originalEncoding;
        public final boolean usedFallback;
        public final byte dataCoding;

        public EncodingResult(byte[] bytes, String encoding, String originalEncoding,
                            boolean usedFallback, byte dataCoding) {
            this.bytes = bytes;
            this.encoding = encoding;
            this.originalEncoding = originalEncoding;
            this.usedFallback = usedFallback;
            this.dataCoding = dataCoding;
        }
    }

    /**
     * Result of decoding operation containing decoded text, detected encoding,
     * and confidence score.
     */
    public static class DecodingResult {
        public final String text;
        public final String encoding;
        public final String declaredEncoding;
        public final double confidence;

        public DecodingResult(String text, String encoding, String declaredEncoding, double confidence) {
            this.text = text;
            this.encoding = encoding;
            this.declaredEncoding = declaredEncoding;
            this.confidence = confidence;
        }
    }

    /**
     * Encodes text using specified encoding with automatic fallback.
     *
     * @param text Text to encode
     * @param requestedEncoding Requested encoding
     * @return EncodingResult with encoded bytes and actual encoding used
     */
    public static EncodingResult encodeWithFallback(String text, String requestedEncoding) {
        if (text == null || text.isEmpty()) {
            return new EncodingResult(new byte[0], requestedEncoding, requestedEncoding, false, (byte)0x00);
        }

        String normalizedEncoding = normalizeEncodingName(requestedEncoding);

        // Try requested encoding first
        try {
            Charset charset = resolveCharset(normalizedEncoding);
            if (charset != null && canEncode(text, charset)) {
                byte[] bytes = encode(text, charset);
                byte dataCoding = getDataCodingForEncoding(normalizedEncoding);
                log.debug("Successfully encoded with requested encoding: {}", normalizedEncoding);
                return new EncodingResult(bytes, normalizedEncoding, requestedEncoding, false, dataCoding);
            }
        } catch (Exception e) {
            log.debug("Failed to encode with {}: {}", normalizedEncoding, e.getMessage());
        }

        // Fallback chain
        String[] fallbackEncodings = getFallbackChain(normalizedEncoding);
        for (String fallback : fallbackEncodings) {
            try {
                Charset charset = resolveCharset(fallback);
                if (charset != null && canEncode(text, charset)) {
                    byte[] bytes = encode(text, charset);
                    byte dataCoding = getDataCodingForEncoding(fallback);
                    log.info("Used fallback encoding {} instead of {}", fallback, requestedEncoding);
                    return new EncodingResult(bytes, fallback, requestedEncoding, true, dataCoding);
                }
            } catch (Exception e) {
                log.debug("Fallback {} failed: {}", fallback, e.getMessage());
            }
        }

        // Last resort: UTF-8
        log.warn("All encodings failed, using UTF-8 for text: {}",
                text.length() > 50 ? text.substring(0, 50) + "..." : text);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        return new EncodingResult(bytes, "UTF-8", requestedEncoding, true, (byte)0x08);
    }

    /**
     * Decodes bytes with automatic encoding detection.
     *
     * @param bytes Raw bytes to decode
     * @param declaredEncoding Declared encoding (from data_coding)
     * @return DecodingResult with decoded text and detected encoding
     */
    public static DecodingResult decodeWithDetection(byte[] bytes, String declaredEncoding) {
        if (bytes == null || bytes.length == 0) {
            return new DecodingResult("", declaredEncoding, declaredEncoding, 1.0);
        }

        String normalizedEncoding = normalizeEncodingName(declaredEncoding);

        // Try declared encoding first
        try {
            Charset charset = resolveCharset(normalizedEncoding);
            if (charset != null) {
                String text = decode(bytes, charset);
                double score = scoreDecodedText(text, bytes.length);
                if (score >= 0.9) {
                    return new DecodingResult(text, normalizedEncoding, declaredEncoding, score);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to decode with declared encoding {}: {}", normalizedEncoding, e.getMessage());
        }

        // Try detection
        String[] candidateEncodings = getDetectionCandidates(normalizedEncoding);
        String bestText = null;
        String bestEncoding = normalizedEncoding;
        double bestScore = -1;

        for (String candidate : candidateEncodings) {
            try {
                Charset charset = resolveCharset(candidate);
                if (charset != null) {
                    String text = decode(bytes, charset);
                    double score = scoreDecodedText(text, bytes.length);

                    if (score > bestScore) {
                        bestScore = score;
                        bestText = text;
                        bestEncoding = candidate;
                    }

                    // Good enough
                    if (score >= 0.95) {
                        break;
                    }
                }
            } catch (Exception e) {
                log.trace("Detection failed for {}: {}", candidate, e.getMessage());
            }
        }

        if (!bestEncoding.equals(normalizedEncoding)) {
            log.warn("Encoding corrected: declared={}, detected={}, confidence={}",
                    declaredEncoding, bestEncoding, bestScore);
        }

        return new DecodingResult(
            bestText != null ? bestText : new String(bytes, StandardCharsets.UTF_8),
            bestEncoding,
            declaredEncoding,
            bestScore
        );
    }

    /**
     * Gets the SMPP data coding byte for an encoding.
     *
     * @param encoding Encoding name
     * @return Data coding byte value
     */
    public static byte getDataCodingForEncoding(String encoding) {
        if (encoding == null) {
            return SmppConstants.DATA_CODING_DEFAULT;
        }

        String normalized = normalizeEncodingName(encoding).toUpperCase();

        return switch (normalized) {
            case "X-GSM7BIT", "GSM7", "SCGSM", "CCGSM" -> (byte) 0x00;  // GSM 7-bit
            case "ISO-8859-1" -> (byte) 0x03;  // Latin-1
            case "BINARY", "8BIT" -> (byte) 0x04;  // Binary
            case "JIS_X0208", "JIS" -> (byte) 0x05;  // JIS
            case "ISO-8859-5", "CYRILLIC" -> (byte) 0x06;  // Cyrillic
            case "ISO-8859-8", "HEBREW" -> (byte) 0x07;  // Hebrew
            case "UTF-16BE", "UCS2", "UTF-16", "UTF-8" -> (byte) 0x08;  // UCS2/Unicode
            case "PICTOGRAM" -> (byte) 0x09;  // Pictogram
            case "ISO-2022-JP" -> (byte) 0x0A;  // ISO-2022-JP (Music Codes)
            case "ISO-8859-6", "ARABIC" -> (byte) 0x0B;  // Arabic (reserved)
            case "KS_C_5601", "EUC-KR" -> (byte) 0x0E;  // Korean
            default -> SmppConstants.DATA_CODING_DEFAULT;
        };
    }

    /**
     * Resolves encoding name from data coding byte.
     *
     * @param dataCoding Data coding byte
     * @return Encoding name
     */
    public static String getEncodingFromDataCoding(byte dataCoding) {
        return switch (dataCoding & 0xFF) {
            case 0x00 -> "GSM7";  // GSM 7-bit default
            case 0x01 -> "GSM7";  // GSM 7-bit (IA5/ASCII)
            case 0x02 -> "BINARY";  // 8-bit binary
            case 0x03 -> "ISO-8859-1";  // Latin-1
            case 0x04 -> "BINARY";  // 8-bit binary
            case 0x05 -> "JIS_X0208";  // JIS
            case 0x06 -> "ISO-8859-5";  // Cyrillic
            case 0x07 -> "ISO-8859-8";  // Hebrew
            case 0x08 -> "UTF-16BE";  // UCS2
            case 0x09 -> "PICTOGRAM";  // Pictogram
            case 0x0A -> "ISO-2022-JP";  // ISO-2022-JP
            case 0x0B -> "ISO-8859-6";  // Arabic (reserved)
            case 0x0D -> "JIS_X0212";  // Extended Kanji
            case 0x0E -> "EUC-KR";  // Korean
            default -> "GSM7";  // Default to GSM7
        };
    }

    /**
     * Checks if text needs Unicode encoding.
     *
     * @param text Text to check
     * @return true if Unicode encoding is required
     */
    public static boolean needsUnicodeEncoding(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        // Check if GSM7 can handle it
        try {
            Charset gsm7 = resolveCharset("GSM7");
            if (gsm7 != null && canEncode(text, gsm7)) {
                return false;
            }
        } catch (Exception e) {
            log.debug("GSM7 check failed", e);
        }

        // Check if Latin-1 can handle it
        try {
            if (StandardCharsets.ISO_8859_1.newEncoder().canEncode(text)) {
                return false;
            }
        } catch (Exception e) {
            log.debug("Latin-1 check failed", e);
        }

        // Needs Unicode
        return true;
    }

    // Private helper methods

    private static String normalizeEncodingName(String encoding) {
        if (encoding == null) {
            return "GSM7";
        }

        String upper = encoding.toUpperCase().trim();
        return ENCODING_ALIASES.getOrDefault(upper, encoding);
    }

    private static Charset resolveCharset(String encoding) {
        if (encoding == null) {
            return null;
        }

        // Check cache first
        Charset cached = CHARSET_CACHE.get(encoding);
        if (cached != null) {
            return cached;
        }

        // Try standard charsets
        try {
            if (Charset.isSupported(encoding)) {
                Charset charset = Charset.forName(encoding);
                CHARSET_CACHE.put(encoding, charset);
                return charset;
            }
        } catch (Exception e) {
            log.trace("Standard charset lookup failed for {}", encoding);
        }

        // Try custom provider
        try {
            Charset charset = CHARSET_PROVIDER.charsetForName(encoding);
            if (charset != null) {
                CHARSET_CACHE.put(encoding, charset);
                return charset;
            }
        } catch (Exception e) {
            log.trace("Custom charset lookup failed for {}", encoding);
        }

        log.warn("Charset not found: {}", encoding);
        return null;
    }

    private static boolean canEncode(String text, Charset charset) {
        try {
            CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
            return encoder.canEncode(text);
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] encode(String text, Charset charset) {
        try {
            CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

            ByteBuffer buffer = encoder.encode(CharBuffer.wrap(text));
            byte[] result = new byte[buffer.remaining()];
            buffer.get(result);
            return result;
        } catch (Exception e) {
            log.error("Encoding failed with {}", charset.name(), e);
            return text.getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String decode(byte[] bytes, Charset charset) {
        try {
            CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(bytes));
            return buffer.toString();
        } catch (Exception e) {
            log.error("Decoding failed with {}", charset.name(), e);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static String[] getFallbackChain(String encoding) {
        String normalized = normalizeEncodingName(encoding).toUpperCase();

        // Custom fallback chains based on encoding type
        if (normalized.contains("GSM")) {
            return new String[]{"ISO-8859-1", "UTF-8", "UTF-16BE"};
        } else if (normalized.contains("UTF") || normalized.contains("UCS")) {
            return new String[]{"UTF-8", "UTF-16BE", "ISO-8859-1"};
        } else if (normalized.contains("8859-5") || normalized.contains("CYRILLIC")) {
            return new String[]{"KOI8-U", "Cp1251", "UTF-8"};
        } else if (normalized.contains("8859-8") || normalized.contains("HEBREW")) {
            return new String[]{"Cp1255", "UTF-8", "UTF-16BE"};
        } else if (normalized.contains("8859-6") || normalized.contains("ARABIC")) {
            return new String[]{"Cp1256", "UTF-8", "UTF-16BE"};
        } else {
            return new String[]{"ISO-8859-1", "UTF-8", "UTF-16BE"};
        }
    }

    private static String[] getDetectionCandidates(String declaredEncoding) {
        if (declaredEncoding == null) {
            return new String[]{"GSM7", "UTF-8", "UTF-16BE", "ISO-8859-1", "Cp1252"};
        }

        String normalized = normalizeEncodingName(declaredEncoding).toUpperCase();

        // Smart candidate selection based on common confusions
        if (normalized.contains("UTF-16BE") || normalized.equals("UCS2")) {
            return new String[]{declaredEncoding, "UTF-16LE", "UTF-8", "ISO-8859-1"};
        } else if (normalized.contains("UTF-8")) {
            return new String[]{declaredEncoding, "ISO-8859-1", "Cp1252", "UTF-16BE"};
        } else if (normalized.contains("8859-1")) {
            return new String[]{declaredEncoding, "UTF-8", "Cp1252", "GSM7"};
        } else if (normalized.contains("GSM")) {
            return new String[]{declaredEncoding, "ISO-8859-1", "UTF-8", "Cp1252"};
        } else {
            return new String[]{declaredEncoding, "UTF-8", "UTF-16BE", "ISO-8859-1", "GSM7"};
        }
    }

    private static double scoreDecodedText(String text, int originalByteLength) {
        if (text == null || text.isEmpty()) {
            return 0.0;
        }

        int length = text.length();
        int replacementChars = 0;
        int controlChars = 0;
        int printableChars = 0;
        int nullChars = 0;

        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);

            // Check for replacement character
            if (c == '\uFFFD') {
                replacementChars++;
            }
            // Check for null
            else if (c == '\0') {
                nullChars++;
            }
            // Check for control characters (except common ones)
            else if (c < 32 && c != '\n' && c != '\r' && c != '\t') {
                controlChars++;
            }
            // Count printable characters
            else if (c >= 32) {
                printableChars++;
            }
        }

        // Calculate score
        double score = 1.0;

        // Heavy penalty for replacement characters (indicates wrong encoding)
        score -= (double) replacementChars / length * 2.0;

        // Penalty for control characters
        score -= (double) controlChars / length * 1.5;

        // Penalty for null characters
        score -= (double) nullChars / length * 2.0;

        // Reward for printable content
        double printableRatio = (double) printableChars / length;
        score *= printableRatio;

        // Check byte-to-char ratio for encoding mismatch detection
        if (originalByteLength > 0) {
            double ratio = (double) length / originalByteLength;

            // Suspicious ratios indicate encoding problems
            if (ratio < 0.5 || ratio > 2.0) {
                score *= 0.5;
            }
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Validates if an encoding name is supported.
     *
     * @param encoding Encoding name to validate
     * @return true if encoding is supported
     */
    public static boolean isEncodingSupported(String encoding) {
        if (encoding == null) {
            return false;
        }

        String normalized = normalizeEncodingName(encoding);
        Charset charset = resolveCharset(normalized);
        return charset != null;
    }

    /**
     * Gets a human-readable description of an encoding.
     *
     * @param encoding Encoding name
     * @return Description of the encoding
     */
    public static String getEncodingDescription(String encoding) {
        if (encoding == null) {
            return "Unknown encoding";
        }

        String normalized = normalizeEncodingName(encoding).toUpperCase();

        return switch (normalized) {
            case "X-GSM7BIT", "GSM7" -> "GSM 7-bit default alphabet";
            case "SCGSM" -> "GSM 7-bit with Spanish single shift";
            case "CCGSM" -> "GSM 7-bit with Portuguese single shift";
            case "UTF-8" -> "UTF-8 Unicode";
            case "UTF-16BE", "UCS2" -> "UTF-16 Big Endian (UCS2)";
            case "UTF-16LE" -> "UTF-16 Little Endian";
            case "ISO-8859-1" -> "ISO-8859-1 (Latin-1)";
            case "ISO-8859-5" -> "ISO-8859-5 (Cyrillic)";
            case "ISO-8859-6" -> "ISO-8859-6 (Arabic)";
            case "ISO-8859-7" -> "ISO-8859-7 (Greek)";
            case "ISO-8859-8" -> "ISO-8859-8 (Hebrew)";
            case "CP1252" -> "Windows-1252 (Western European)";
            case "CP1251" -> "Windows-1251 (Cyrillic)";
            case "CP1255" -> "Windows-1255 (Hebrew)";
            case "CP1256" -> "Windows-1256 (Arabic)";
            case "KOI8-U" -> "KOI8-U (Russian/Ukrainian)";
            case "KZ-1048" -> "KZ-1048 (Kazakhstan)";
            case "MIK" -> "MIK (Cyrillic)";
            default -> "Encoding: " + encoding;
        };
    }
}