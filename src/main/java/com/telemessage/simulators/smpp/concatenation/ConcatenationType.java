package com.telemessage.simulators.smpp.concatenation;


import com.logica.smpp.pdu.StandardSendMessageSM;
import com.logica.smpp.pdu.ValueNotSetException;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * @author Grinfeld Mikhail
 * @since 23.08.2016
 */
@Slf4j
public enum ConcatenationType {
    DEFAULT {//No concatenation or something went wrong getting the concatenation data

        @Override
        public ConcatenationData extractConcatenationData(StandardSendMessageSM<?> sm) {
            return DEFAULT_CONCATENATION_DATA;
        }

        @Override
        public boolean isOfType(StandardSendMessageSM<?> sm) {
            return true;
        }
    },
    TEXT_BASE {
        private static final String CONCAT_PATTERN = "^(\\d+)/(\\d+)\\s+(.*)$";

        private String getMessageText(StandardSendMessageSM<?> sm) {
            try {
                String messageText = sm.getShortMessage();
                if (messageText != null && !messageText.isEmpty()) {
                    return messageText;
                }

                // If short message is empty/null, try message payload
                com.logica.smpp.util.ByteBuffer payloadBuffer = sm.getMessagePayload();
                if (payloadBuffer != null && payloadBuffer.length() > 0) {
                    byte[] payload = payloadBuffer.getBuffer();
                    // Use the same encoding logic as getShortMessage() would use
                    String encoding = getEncodingFromDataCoding(sm.getDataCoding());
                    return new String(payload, 0, payloadBuffer.length(), encoding);
                }


                return null;
            } catch (Exception e) {
                log.debug("Error retrieving message text", e);
                return null;
            }
        }

        private String getEncodingFromDataCoding(byte dataCoding) {
            switch (dataCoding & 0xFF) {
                case 0x00: // GSM 7-bit default alphabet
                    return "ISO-8859-1"; // Close approximation for GSM 7-bit
                case 0x08: // UCS2 (UTF-16)
                    return "UTF-16BE";
                case 0x03: // Latin-1
                    return "ISO-8859-1";
                default:
                    return "UTF-8"; // Default fallback
            }
        }

        @Override
        public ConcatenationData extractConcatenationData(StandardSendMessageSM<?> sm) throws Exception {
            try {
                String messageText = getMessageText(sm);
                if (messageText == null || messageText.isEmpty()) {
                    throw new IllegalArgumentException("Empty message text");
                }

                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(CONCAT_PATTERN);
                java.util.regex.Matcher matcher = pattern.matcher(messageText);

                if (!matcher.matches()) {
                    throw new IllegalArgumentException("Message doesn't match text concatenation pattern");
                }

                int partNum = Integer.parseInt(matcher.group(1));
                int totalParts = Integer.parseInt(matcher.group(2));
                String actualMessage = matcher.group(3);

                // Create a unique reference based on sender, receiver, total parts, and first chars of message
                // This reduces collision risk while keeping the same ref for all parts of same message
                String refKey = sm.getSourceAddr().getAddress() +
                        "|" + sm.getDestAddr().getAddress() +
                        "|" + totalParts +
                        "|" + (actualMessage.length() > 20 ? actualMessage.substring(0, 20) : actualMessage);
                
                // Use a CRC-like approach for better distribution than simple hashCode
                int hash = 0;
                for (int i = 0; i < refKey.length(); i++) {
                    hash = ((hash << 5) - hash) + refKey.charAt(i);
                    hash = hash & hash; // Convert to 32bit integer
                }
                int refNum = Math.abs(hash) & 0xFFFF; // Keep it within 16-bit range
                
                log.debug("TEXT_BASE reference generation: refKey={}, hash={}, refNum={}", 
                    refKey, hash, refNum);

                return new ConcatenationData(ConcatenationType.TEXT_BASE,
                        refNum,
                        totalParts,
                        partNum);

            } catch (Exception e) {
                throw new Exception("Problem handling text-based concatenation", e);
            }
        }

        @Override
        public boolean isOfType(StandardSendMessageSM<?> sm) {
            try {
                String messageText = getMessageText(sm);
                if (messageText == null) return false;
                return messageText.matches(CONCAT_PATTERN);
            } catch (Exception e) {
                log.debug("Error checking text-based concatenation pattern", e);
                return false;
            }
        }
    },
    SAR {
        public ConcatenationData extractConcatenationData(StandardSendMessageSM<?> sm) throws Exception {

            ConcatenationData concatenationData;
            try {
                concatenationData = new ConcatenationData(ConcatenationType.SAR,
                        sm.getSarMsgRefNum(),
                        sm.getSarTotalSegments(),
                        sm.getSarSegmentSeqnum());
                return concatenationData;
            } catch (ValueNotSetException | RuntimeException e) {
                final String msg = "Problem handling SM as SAR segment";
                throw new Exception(msg, e);
            }
        }

        @Override
        public boolean isOfType(StandardSendMessageSM<?> sm) {
            return sm.hasSarMsgRefNum();
        }
    },
    UDHI {
        @Override
        public ConcatenationData extractConcatenationData(StandardSendMessageSM<?> sm) throws Exception {
            try {
                ConcatenationData concatenationData;

                byte[] udhi = sm.getUdhiData();

                if (udhi != null && udhi.length >= 6) {
                    byte splitMessageId = udhi[3];
                    byte splitMessageSize = udhi[4];
                    byte partIndex = udhi[5];
                    concatenationData = new ConcatenationData(ConcatenationType.UDHI,
                            splitMessageId,
                            splitMessageSize,
                            partIndex);
                } else {
                    throw new IllegalArgumentException("SM argument is not SAR concatenated");
                }
                return concatenationData;
            } catch (RuntimeException e) {
                final String msg = "Problem handling SM as UDHI segment";
                throw new Exception(msg, e);
            }
        }

        @Override
        public boolean isOfType(StandardSendMessageSM<?> sm) {
            try {
                sm.getShortMessage(null);//This call is required since this is where udhiData is initialized.
            } catch (UnsupportedEncodingException e) {
                log.error("ConcatenationType.UDHI",
                        "isOfType Unexpected exception at this stage", e);
            }
            return sm.getUdhiData() != null;
        }
    };

    public static final ConcatenationData DEFAULT_CONCATENATION_DATA =
            new ConcatenationData(DEFAULT, 0, 0, 0);

    public abstract ConcatenationData extractConcatenationData(StandardSendMessageSM<?> sm) throws Exception;

    public abstract boolean isOfType(StandardSendMessageSM<?> sm);

    public static ConcatenationData extractSmConcatenationData(StandardSendMessageSM<?> sm) {
        log.info("ConcatenationType",
                "extractAnyConcatenationData");
        ConcatenationData concatenationData = DEFAULT_CONCATENATION_DATA;

        // Try TEXT_BASE first, then other types
        if (TEXT_BASE.isOfType(sm)) {
            try {
                concatenationData = TEXT_BASE.extractConcatenationData(sm);
                log.debug("Detected text-based concatenation: {}", concatenationData);
                return concatenationData;
            } catch (Exception e) {
                log.warn("Failed to extract text-based concatenation data", e);
            }
        }

        // Try other concatenation types if text-based failed
        final ConcatenationType concatenationType = Arrays.stream(values())
                .filter(val -> val != DEFAULT && val != TEXT_BASE && val.isOfType(sm))
                .findFirst()
                .orElse(DEFAULT);

        try {
            concatenationData = concatenationType.extractConcatenationData(sm);
        } catch (Exception e) {
            log.error("Failed to extract concatenation data", e);
        }
        return concatenationData;
    }

}
