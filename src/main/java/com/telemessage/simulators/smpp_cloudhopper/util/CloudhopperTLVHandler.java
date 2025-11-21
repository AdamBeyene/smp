package com.telemessage.simulators.smpp_cloudhopper.util;

import com.cloudhopper.smpp.pdu.BaseSm;
import com.cloudhopper.smpp.tlv.Tlv;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom TLV (Tag-Length-Value) parameter handler for Cloudhopper SMPP.
 *
 * Manages custom TLV parameters matching Logica implementation:
 * - Owner (0x1926): Integer value for owner identification
 * - Extended Message ID (0x1927): String value for message tracking
 * - Message Time (0x1928): String value for timestamp
 *
 * Also handles standard SMPP TLVs:
 * - SAR parameters (0x020C, 0x020E, 0x020F)
 * - message_payload (0x0424)
 * - receipted_message_id (0x001E)
 * - And all other SMPP v3.4 standard TLVs
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-21
 */
@Slf4j
public class CloudhopperTLVHandler {

    // Custom TLV tags (matching Logica implementation)
    public static final short TLV_OWNER = 0x1926;           // Integer owner ID
    public static final short TLV_MESSAGE_ID = 0x1927;      // Extended message ID (String)
    public static final short TLV_MESSAGE_TIME = 0x1928;    // Message timestamp (String)

    // Standard SMPP TLV tags (commonly used)
    public static final short TLV_DEST_ADDR_SUBUNIT = 0x0005;
    public static final short TLV_SOURCE_ADDR_SUBUNIT = 0x000D;
    public static final short TLV_DEST_NETWORK_TYPE = 0x0006;
    public static final short TLV_SOURCE_NETWORK_TYPE = 0x000E;
    public static final short TLV_DEST_BEARER_TYPE = 0x0007;
    public static final short TLV_SOURCE_BEARER_TYPE = 0x000F;
    public static final short TLV_DEST_TELEMATICS_ID = 0x0008;
    public static final short TLV_SOURCE_TELEMATICS_ID = 0x0010;
    public static final short TLV_QOS_TIME_TO_LIVE = 0x0017;
    public static final short TLV_PAYLOAD_TYPE = 0x0019;
    public static final short TLV_ADDITIONAL_STATUS_INFO = 0x001D;
    public static final short TLV_RECEIPTED_MESSAGE_ID = 0x001E;
    public static final short TLV_MS_MSG_WAIT_FACILITIES = 0x0030;

    // Privacy and display TLVs
    public static final short TLV_PRIVACY_INDICATOR = 0x0201;
    public static final short TLV_SOURCE_SUBADDRESS = 0x0202;
    public static final short TLV_DEST_SUBADDRESS = 0x0203;
    public static final short TLV_USER_MESSAGE_REFERENCE = 0x0204;
    public static final short TLV_USER_RESPONSE_CODE = 0x0205;
    public static final short TLV_LANGUAGE_INDICATOR = 0x020D;
    public static final short TLV_SOURCE_PORT = 0x020A;
    public static final short TLV_DESTINATION_PORT = 0x020B;

    // Callback and USSD TLVs
    public static final short TLV_CALLBACK_NUM = 0x0381;
    public static final short TLV_CALLBACK_NUM_PRES_IND = 0x0302;
    public static final short TLV_CALLBACK_NUM_ATAG = 0x0303;
    public static final short TLV_NUMBER_OF_MESSAGES = 0x0304;
    public static final short TLV_SMS_SIGNAL = 0x1203;
    public static final short TLV_ALERT_ON_MESSAGE = 0x130C;
    public static final short TLV_ITS_REPLY_TYPE = 0x1380;
    public static final short TLV_ITS_SESSION_INFO = 0x1383;
    public static final short TLV_USSD_SERVICE_OP = 0x0501;

    // Delivery receipt and network TLVs
    public static final short TLV_DISPLAY_TIME = 0x1201;
    public static final short TLV_MS_VALIDITY = 0x1204;
    public static final short TLV_DPF_RESULT_VALIDATION = 0x0420;
    public static final short TLV_SET_DPF = 0x0421;
    public static final short TLV_MS_AVAILABILITY_STATUS = 0x0422;
    public static final short TLV_NETWORK_ERROR_CODE = 0x0423;
    public static final short TLV_DELIVERY_FAILURE_REASON = 0x0425;
    public static final short TLV_MORE_MESSAGES_TO_SEND = 0x0426;
    public static final short TLV_MESSAGE_STATE = 0x0427;
    public static final short TLV_CONGESTION_STATE = 0x0428;

    // Billing and carrier-specific TLVs
    public static final short TLV_BILLING_ID = 0x060B;
    public static final short TLV_DEST_ADDR_NP_RESOLUTION = 0x0610;
    public static final short TLV_DEST_ADDR_NP_INFO = 0x0611;
    public static final short TLV_DEST_ADDR_NP_COUNTRY = 0x0613;

    /**
     * Adds custom owner TLV to a PDU.
     *
     * @param pdu The PDU to modify
     * @param owner Owner ID value
     */
    public static void addOwnerTLV(BaseSm pdu, int owner) {
        try {
            byte[] value = ByteBuffer.allocate(4).putInt(owner).array();
            pdu.addOptionalParameter(new Tlv(TLV_OWNER, value));
            log.debug("Added owner TLV: {}", owner);
        } catch (Exception e) {
            log.error("Failed to add owner TLV", e);
        }
    }

    /**
     * Adds custom message ID TLV to a PDU.
     *
     * @param pdu The PDU to modify
     * @param messageId Extended message ID
     */
    public static void addMessageIdTLV(BaseSm pdu, String messageId) {
        if (messageId == null || messageId.isEmpty()) {
            return;
        }

        try {
            byte[] value = messageId.getBytes(StandardCharsets.UTF_8);
            pdu.addOptionalParameter(new Tlv(TLV_MESSAGE_ID, value));
            log.debug("Added message ID TLV: {}", messageId);
        } catch (Exception e) {
            log.error("Failed to add message ID TLV", e);
        }
    }

    /**
     * Adds custom message time TLV to a PDU.
     *
     * @param pdu The PDU to modify
     * @param messageTime Timestamp string
     */
    public static void addMessageTimeTLV(BaseSm pdu, String messageTime) {
        if (messageTime == null || messageTime.isEmpty()) {
            return;
        }

        try {
            byte[] value = messageTime.getBytes(StandardCharsets.UTF_8);
            pdu.addOptionalParameter(new Tlv(TLV_MESSAGE_TIME, value));
            log.debug("Added message time TLV: {}", messageTime);
        } catch (Exception e) {
            log.error("Failed to add message time TLV", e);
        }
    }

    /**
     * Adds all custom TLVs based on a parameter map.
     *
     * @param pdu The PDU to modify
     * @param params Map of parameter names to values
     */
    public static void addCustomTLVs(BaseSm pdu, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> param : params.entrySet()) {
            String tag = param.getKey().toLowerCase();
            String value = param.getValue();

            switch (tag) {
                case "owner":
                    try {
                        int ownerValue = Integer.parseInt(value);
                        addOwnerTLV(pdu, ownerValue);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid owner value: {}", value);
                    }
                    break;

                case "messageid":
                    addMessageIdTLV(pdu, value);
                    break;

                case "messagetime":
                    addMessageTimeTLV(pdu, value);
                    break;

                default:
                    // Try to handle as generic TLV if it's a hex tag
                    if (tag.startsWith("0x")) {
                        try {
                            short tlvTag = Short.parseShort(tag.substring(2), 16);
                            addGenericTLV(pdu, tlvTag, value);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid TLV tag: {}", tag);
                        }
                    }
                    break;
            }
        }
    }

    /**
     * Adds a generic TLV with string value.
     *
     * @param pdu The PDU to modify
     * @param tag TLV tag number
     * @param value String value
     */
    public static void addGenericTLV(BaseSm pdu, short tag, String value) {
        if (value == null) {
            return;
        }

        try {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            pdu.addOptionalParameter(new Tlv(tag, bytes));
            log.debug("Added generic TLV: tag=0x{}, value={}",
                    Integer.toHexString(tag & 0xFFFF), value);
        } catch (Exception e) {
            log.error("Failed to add generic TLV", e);
        }
    }

    /**
     * Extracts custom TLVs from a PDU.
     *
     * @param pdu The PDU to extract from
     * @return Map of TLV tag to value
     */
    public static Map<String, Object> extractCustomTLVs(BaseSm pdu) {
        Map<String, Object> tlvMap = new HashMap<>();

        if (pdu.getOptionalParameters() == null) {
            return tlvMap;
        }

        for (Tlv tlv : pdu.getOptionalParameters()) {
            try {
                switch (tlv.getTag()) {
                    case TLV_OWNER:
                        if (tlv.getLength() >= 4) {
                            int owner = ByteBuffer.wrap(tlv.getValue()).getInt();
                            tlvMap.put("owner", owner);
                        }
                        break;

                    case TLV_MESSAGE_ID:
                        String messageId = new String(tlv.getValue(), StandardCharsets.UTF_8);
                        tlvMap.put("messageId", messageId);
                        break;

                    case TLV_MESSAGE_TIME:
                        String messageTime = new String(tlv.getValue(), StandardCharsets.UTF_8);
                        tlvMap.put("messageTime", messageTime);
                        break;

                    default:
                        // Store other TLVs with hex tag as key
                        String hexTag = String.format("0x%04X", tlv.getTag() & 0xFFFF);
                        tlvMap.put(hexTag, tlv.getValue());
                        break;
                }
            } catch (Exception e) {
                log.debug("Failed to extract TLV tag 0x{}: {}",
                        Integer.toHexString(tlv.getTag() & 0xFFFF), e.getMessage());
            }
        }

        return tlvMap;
    }

    /**
     * Gets owner value from PDU TLVs.
     *
     * @param pdu The PDU to check
     * @return Owner value or null if not present
     */
    public static Integer getOwnerFromTLV(BaseSm pdu) {
        try {
            Tlv ownerTlv = pdu.getOptionalParameter(TLV_OWNER);
            if (ownerTlv != null && ownerTlv.getLength() >= 4) {
                return ByteBuffer.wrap(ownerTlv.getValue()).getInt();
            }
        } catch (Exception e) {
            log.debug("No owner TLV found");
        }
        return null;
    }

    /**
     * Gets extended message ID from PDU TLVs.
     *
     * @param pdu The PDU to check
     * @return Message ID or null if not present
     */
    public static String getMessageIdFromTLV(BaseSm pdu) {
        try {
            Tlv messageIdTlv = pdu.getOptionalParameter(TLV_MESSAGE_ID);
            if (messageIdTlv != null) {
                return new String(messageIdTlv.getValue(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("No message ID TLV found");
        }
        return null;
    }

    /**
     * Gets message time from PDU TLVs.
     *
     * @param pdu The PDU to check
     * @return Message time or null if not present
     */
    public static String getMessageTimeFromTLV(BaseSm pdu) {
        try {
            Tlv messageTimeTlv = pdu.getOptionalParameter(TLV_MESSAGE_TIME);
            if (messageTimeTlv != null) {
                return new String(messageTimeTlv.getValue(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.debug("No message time TLV found");
        }
        return null;
    }

    /**
     * Copies all TLVs from source PDU to destination PDU.
     *
     * @param source Source PDU
     * @param destination Destination PDU
     */
    public static void copyTLVs(BaseSm source, BaseSm destination) {
        if (source.getOptionalParameters() == null) {
            return;
        }

        for (Tlv tlv : source.getOptionalParameters()) {
            try {
                destination.addOptionalParameter(new Tlv(tlv.getTag(), tlv.getValue()));
            } catch (Exception e) {
                log.warn("Failed to copy TLV tag 0x{}",
                        Integer.toHexString(tlv.getTag() & 0xFFFF));
            }
        }
    }

    /**
     * Removes a TLV from a PDU.
     *
     * @param pdu The PDU to modify
     * @param tag TLV tag to remove
     */
    public static void removeTLV(BaseSm pdu, short tag) {
        if (pdu.getOptionalParameters() == null) {
            return;
        }

        pdu.getOptionalParameters().removeIf(tlv -> tlv.getTag() == tag);
    }

    /**
     * Checks if a PDU has a specific TLV.
     *
     * @param pdu The PDU to check
     * @param tag TLV tag to check for
     * @return true if TLV is present
     */
    public static boolean hasTLV(BaseSm pdu, short tag) {
        try {
            return pdu.getOptionalParameter(tag) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets a human-readable description of a TLV tag.
     *
     * @param tag TLV tag number
     * @return Description of the TLV
     */
    public static String getTLVDescription(short tag) {
        switch (tag) {
            // Custom TLVs
            case TLV_OWNER: return "Owner ID (Custom)";
            case TLV_MESSAGE_ID: return "Extended Message ID (Custom)";
            case TLV_MESSAGE_TIME: return "Message Time (Custom)";

            // SAR TLVs
            case CloudhopperUtils.TLV_SAR_MSG_REF_NUM: return "SAR Reference Number";
            case CloudhopperUtils.TLV_SAR_TOTAL_SEGMENTS: return "SAR Total Segments";
            case CloudhopperUtils.TLV_SAR_SEGMENT_SEQNUM: return "SAR Segment Sequence";

            // Message TLVs
            case CloudhopperUtils.TLV_MESSAGE_PAYLOAD: return "Message Payload";
            case TLV_RECEIPTED_MESSAGE_ID: return "Receipted Message ID";

            // Standard TLVs
            case TLV_DEST_ADDR_SUBUNIT: return "Destination Address Subunit";
            case TLV_SOURCE_ADDR_SUBUNIT: return "Source Address Subunit";
            case TLV_DEST_NETWORK_TYPE: return "Destination Network Type";
            case TLV_SOURCE_NETWORK_TYPE: return "Source Network Type";
            case TLV_QOS_TIME_TO_LIVE: return "QoS Time to Live";
            case TLV_PAYLOAD_TYPE: return "Payload Type";
            case TLV_ADDITIONAL_STATUS_INFO: return "Additional Status Info";
            case TLV_MS_MSG_WAIT_FACILITIES: return "MS Message Wait Facilities";
            case TLV_PRIVACY_INDICATOR: return "Privacy Indicator";
            case TLV_USER_MESSAGE_REFERENCE: return "User Message Reference";
            case TLV_USER_RESPONSE_CODE: return "User Response Code";
            case TLV_LANGUAGE_INDICATOR: return "Language Indicator";
            case TLV_SOURCE_PORT: return "Source Port";
            case TLV_DESTINATION_PORT: return "Destination Port";
            case TLV_CALLBACK_NUM: return "Callback Number";
            case TLV_NUMBER_OF_MESSAGES: return "Number of Messages";
            case TLV_MESSAGE_STATE: return "Message State";
            case TLV_NETWORK_ERROR_CODE: return "Network Error Code";
            case TLV_DELIVERY_FAILURE_REASON: return "Delivery Failure Reason";

            default:
                return String.format("Unknown TLV (0x%04X)", tag & 0xFFFF);
        }
    }
}