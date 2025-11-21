package com.telemessage.simulators.smpp_cloudhopper.util;

import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.pdu.DeliverSm;
import com.cloudhopper.smpp.pdu.SubmitSm;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import com.cloudhopper.smpp.tlv.Tlv;
import com.cloudhopper.smpp.type.Address;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delivery receipt generator for Cloudhopper SMPP implementation.
 *
 * Generates standard SMPP delivery receipts matching Logica implementation:
 * - Full status mapping (DELIVRD, UNDELIV, EXPIRED, etc.)
 * - Standard DR format as per SMPP specification
 * - Error code generation
 * - DR correlation with original messages
 * - Configurable automatic DR generation
 *
 * Format:
 * "id:IIIIIIIIII sub:SSS dlvrd:DDD submit date:YYMMDDhhmm done date:YYMMDDhhmm stat:SSSSSSS err:EEE text:TTTTTTTTTTTTTTTTTTTT"
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-21
 */
@Slf4j
@Component
public class CloudhopperDeliveryReceiptGenerator {

    // Delivery receipt states
    public static final String STATE_ENROUTE = "ENROUTE";
    public static final String STATE_DELIVERED = "DELIVRD";
    public static final String STATE_EXPIRED = "EXPIRED";
    public static final String STATE_DELETED = "DELETED";
    public static final String STATE_UNDELIVERABLE = "UNDELIV";
    public static final String STATE_ACCEPTED = "ACCEPTD";
    public static final String STATE_UNKNOWN = "UNKNOWN";
    public static final String STATE_REJECTED = "REJECTD";
    public static final String STATE_SKIPPED = "SKIPPED";

    // Common error codes
    public static final String ERR_NONE = "000";
    public static final String ERR_UNKNOWN = "001";
    public static final String ERR_NETWORK_ERROR = "002";
    public static final String ERR_INVALID_DEST = "003";
    public static final String ERR_EXPIRED = "004";
    public static final String ERR_DELETED = "005";
    public static final String ERR_UNDELIVERABLE = "006";
    public static final String ERR_REJECTED = "007";
    public static final String ERR_SYSTEM_ERROR = "008";
    public static final String ERR_INVALID_SOURCE = "009";
    public static final String ERR_QUOTA_EXCEEDED = "010";

    // Date format for delivery receipts
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyMMddHHmm");

    // Counter for submitted/delivered messages
    private static final AtomicInteger SUBMITTED_COUNTER = new AtomicInteger(1);
    private static final AtomicInteger DELIVERED_COUNTER = new AtomicInteger(1);

    // Random for simulating delivery outcomes
    private static final Random RANDOM = new Random();

    /**
     * Generates a delivery receipt for a submitted message.
     *
     * @param originalSubmit Original SubmitSm PDU
     * @param submitResponse Submit response with message ID
     * @param status Delivery status
     * @param errorCode Error code (null for success)
     * @return Formatted delivery receipt text
     */
    public static String generateDeliveryReceipt(
            SubmitSm originalSubmit,
            SubmitSmResp submitResponse,
            String status,
            String errorCode) {

        String messageId = submitResponse.getMessageId();
        if (messageId == null || messageId.isEmpty()) {
            messageId = CloudhopperUtils.generateMessageId();
        }

        // Get message text preview (first 20 chars)
        String textPreview = getTextPreview(originalSubmit);

        // Generate dates
        Date submitDate = new Date();
        Date doneDate = new Date(System.currentTimeMillis() + getDeliveryDelay(status));

        // Format dates
        String submitDateStr = DATE_FORMAT.format(submitDate);
        String doneDateStr = DATE_FORMAT.format(doneDate);

        // Determine counts
        int submitted = SUBMITTED_COUNTER.get();
        int delivered = STATE_DELIVERED.equals(status) ? DELIVERED_COUNTER.getAndIncrement() : 0;

        // Determine error code
        if (errorCode == null) {
            errorCode = getDefaultErrorCode(status);
        }

        // Build delivery receipt
        return String.format(
            "id:%s sub:%03d dlvrd:%03d submit date:%s done date:%s stat:%s err:%s text:%s",
            messageId,
            submitted,
            delivered,
            submitDateStr,
            doneDateStr,
            status,
            errorCode,
            textPreview
        );
    }

    /**
     * Creates a DeliverSm PDU containing a delivery receipt.
     *
     * @param originalSubmit Original SubmitSm PDU
     * @param submitResponse Submit response with message ID
     * @param status Delivery status
     * @param errorCode Error code (optional)
     * @return DeliverSm PDU with delivery receipt
     */
    public static DeliverSm createDeliveryReceiptPDU(
            SubmitSm originalSubmit,
            SubmitSmResp submitResponse,
            String status,
            String errorCode) {

        DeliverSm deliverSm = new DeliverSm();

        try {
            // Swap source and destination (DR comes from original destination)
            deliverSm.setSourceAddress(originalSubmit.getDestAddress());
            deliverSm.setDestAddress(originalSubmit.getSourceAddress());

            // Set ESM class to indicate delivery receipt
            deliverSm.setEsmClass(SmppConstants.ESM_CLASS_MT_SMSC_DELIVERY_RECEIPT);

            // Generate delivery receipt text
            String drText = generateDeliveryReceipt(originalSubmit, submitResponse, status, errorCode);

            // Encode and set message
            byte[] drBytes = drText.getBytes();
            deliverSm.setShortMessage(drBytes);

            // Set data coding (default ASCII)
            deliverSm.setDataCoding((byte) 0x00);

            // Add receipted_message_id TLV
            String messageId = submitResponse.getMessageId();
            if (messageId != null && !messageId.isEmpty()) {
                deliverSm.addOptionalParameter(new Tlv(
                    CloudhopperTLVHandler.TLV_RECEIPTED_MESSAGE_ID,
                    messageId.getBytes()
                ));
            }

            // Add message_state TLV
            byte messageState = getMessageStateValue(status);
            deliverSm.addOptionalParameter(new Tlv(
                CloudhopperTLVHandler.TLV_MESSAGE_STATE,
                new byte[]{messageState}
            ));

            // Add network_error_code if error occurred
            if (!STATE_DELIVERED.equals(status) && errorCode != null && !ERR_NONE.equals(errorCode)) {
                deliverSm.addOptionalParameter(new Tlv(
                    CloudhopperTLVHandler.TLV_NETWORK_ERROR_CODE,
                    errorCode.getBytes()
                ));
            }

            log.debug("Created delivery receipt PDU: messageId={}, status={}, error={}",
                    messageId, status, errorCode);

        } catch (Exception e) {
            log.error("Failed to create delivery receipt PDU", e);
        }

        return deliverSm;
    }

    /**
     * Generates automatic delivery receipt based on configuration.
     *
     * @param originalSubmit Original SubmitSm PDU
     * @param submitResponse Submit response
     * @param automaticDr Automatic DR configuration
     * @return DeliverSm PDU or null if DR not needed
     */
    public static DeliverSm generateAutomaticDeliveryReceipt(
            SubmitSm originalSubmit,
            SubmitSmResp submitResponse,
            boolean automaticDr) {

        if (!automaticDr) {
            return null;
        }

        // Check if DR was requested
        byte registeredDelivery = originalSubmit.getRegisteredDelivery();
        if ((registeredDelivery & 0x01) == 0) {
            log.debug("Delivery receipt not requested (registered_delivery={})", registeredDelivery);
            return null;
        }

        // Simulate delivery outcome (90% success rate)
        String status;
        String errorCode;

        int outcome = RANDOM.nextInt(100);
        if (outcome < 90) {
            // Success
            status = STATE_DELIVERED;
            errorCode = ERR_NONE;
        } else if (outcome < 95) {
            // Expired
            status = STATE_EXPIRED;
            errorCode = ERR_EXPIRED;
        } else {
            // Undeliverable
            status = STATE_UNDELIVERABLE;
            errorCode = ERR_UNDELIVERABLE;
        }

        return createDeliveryReceiptPDU(originalSubmit, submitResponse, status, errorCode);
    }

    /**
     * Parses a delivery receipt text to extract fields.
     *
     * @param drText Delivery receipt text
     * @return DeliveryReceiptInfo with parsed fields
     */
    public static DeliveryReceiptInfo parseDeliveryReceipt(String drText) {
        DeliveryReceiptInfo info = new DeliveryReceiptInfo();

        if (drText == null || drText.isEmpty()) {
            return info;
        }

        try {
            // Parse id field
            int idStart = drText.indexOf("id:");
            if (idStart >= 0) {
                int idEnd = drText.indexOf(" ", idStart);
                if (idEnd > idStart + 3) {
                    info.messageId = drText.substring(idStart + 3, idEnd);
                }
            }

            // Parse sub field
            int subStart = drText.indexOf("sub:");
            if (subStart >= 0) {
                int subEnd = drText.indexOf(" ", subStart);
                if (subEnd > subStart + 4) {
                    try {
                        info.submitted = Integer.parseInt(drText.substring(subStart + 4, subEnd));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }

            // Parse dlvrd field
            int dlvrdStart = drText.indexOf("dlvrd:");
            if (dlvrdStart >= 0) {
                int dlvrdEnd = drText.indexOf(" ", dlvrdStart);
                if (dlvrdEnd > dlvrdStart + 6) {
                    try {
                        info.delivered = Integer.parseInt(drText.substring(dlvrdStart + 6, dlvrdEnd));
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
            }

            // Parse stat field
            int statStart = drText.indexOf("stat:");
            if (statStart >= 0) {
                int statEnd = drText.indexOf(" ", statStart);
                if (statEnd < 0) {
                    statEnd = drText.length();
                }
                if (statEnd > statStart + 5) {
                    info.status = drText.substring(statStart + 5, statEnd);
                }
            }

            // Parse err field
            int errStart = drText.indexOf("err:");
            if (errStart >= 0) {
                int errEnd = drText.indexOf(" ", errStart);
                if (errEnd < 0) {
                    errEnd = drText.length();
                }
                if (errEnd > errStart + 4) {
                    info.errorCode = drText.substring(errStart + 4, errEnd);
                }
            }

            // Parse text field
            int textStart = drText.indexOf("text:");
            if (textStart >= 0) {
                info.text = drText.substring(textStart + 5);
            }

        } catch (Exception e) {
            log.warn("Failed to parse delivery receipt: {}", drText, e);
        }

        return info;
    }

    // Helper methods

    private static String getTextPreview(SubmitSm submitSm) {
        try {
            byte[] message = submitSm.getShortMessage();
            if (message == null || message.length == 0) {
                // Check for message_payload TLV
                Tlv payload = submitSm.getOptionalParameter(CloudhopperUtils.TLV_MESSAGE_PAYLOAD);
                if (payload != null) {
                    message = payload.getValue();
                }
            }

            if (message != null && message.length > 0) {
                // Decode message
                String text = CloudhopperUtils.decodeMessage(message, submitSm.getDataCoding());

                // Return first 20 chars
                if (text.length() > 20) {
                    return text.substring(0, 20);
                }
                return text;
            }
        } catch (Exception e) {
            log.debug("Failed to get text preview", e);
        }

        return "";
    }

    private static String getDefaultErrorCode(String status) {
        return switch (status) {
            case STATE_DELIVERED -> ERR_NONE;
            case STATE_EXPIRED -> ERR_EXPIRED;
            case STATE_DELETED -> ERR_DELETED;
            case STATE_UNDELIVERABLE -> ERR_UNDELIVERABLE;
            case STATE_REJECTED -> ERR_REJECTED;
            default -> ERR_UNKNOWN;
        };
    }

    private static long getDeliveryDelay(String status) {
        // Simulate realistic delivery times
        return switch (status) {
            case STATE_DELIVERED -> 2000L + RANDOM.nextInt(3000); // 2-5 seconds
            case STATE_EXPIRED -> 3600000L; // 1 hour
            case STATE_UNDELIVERABLE -> 5000L + RANDOM.nextInt(5000); // 5-10 seconds
            default -> 1000L; // 1 second
        };
    }

    private static byte getMessageStateValue(String status) {
        return switch (status) {
            case STATE_ENROUTE -> SmppConstants.STATE_ENROUTE;
            case STATE_DELIVERED -> SmppConstants.STATE_DELIVERED;
            case STATE_EXPIRED -> SmppConstants.STATE_EXPIRED;
            case STATE_DELETED -> SmppConstants.STATE_DELETED;
            case STATE_UNDELIVERABLE -> SmppConstants.STATE_UNDELIVERABLE;
            case STATE_ACCEPTED -> SmppConstants.STATE_ACCEPTED;
            case STATE_REJECTED -> SmppConstants.STATE_REJECTED;
            case STATE_SKIPPED -> SmppConstants.STATE_UNKNOWN; // No SKIPPED state in Cloudhopper
            default -> SmppConstants.STATE_UNKNOWN;
        };
    }

    /**
     * Delivery receipt information container.
     */
    public static class DeliveryReceiptInfo {
        public String messageId;
        public int submitted;
        public int delivered;
        public String status;
        public String errorCode;
        public String submitDate;
        public String doneDate;
        public String text;

        @Override
        public String toString() {
            return String.format("DR[id=%s, sub=%d, dlvrd=%d, stat=%s, err=%s]",
                    messageId, submitted, delivered, status, errorCode);
        }
    }

    /**
     * Configuration for automatic delivery receipt generation.
     */
    public static class DeliveryReceiptConfig {
        private boolean enableAutomaticDr = true;
        private int successRate = 90; // Percentage
        private long minDelay = 1000; // Milliseconds
        private long maxDelay = 5000; // Milliseconds
        private boolean includeOriginalText = true;
        private boolean addTlvs = true;

        // Getters and setters
        public boolean isEnableAutomaticDr() {
            return enableAutomaticDr;
        }

        public void setEnableAutomaticDr(boolean enableAutomaticDr) {
            this.enableAutomaticDr = enableAutomaticDr;
        }

        public int getSuccessRate() {
            return successRate;
        }

        public void setSuccessRate(int successRate) {
            this.successRate = Math.max(0, Math.min(100, successRate));
        }

        public long getMinDelay() {
            return minDelay;
        }

        public void setMinDelay(long minDelay) {
            this.minDelay = Math.max(0, minDelay);
        }

        public long getMaxDelay() {
            return maxDelay;
        }

        public void setMaxDelay(long maxDelay) {
            this.maxDelay = Math.max(minDelay, maxDelay);
        }

        public boolean isIncludeOriginalText() {
            return includeOriginalText;
        }

        public void setIncludeOriginalText(boolean includeOriginalText) {
            this.includeOriginalText = includeOriginalText;
        }

        public boolean isAddTlvs() {
            return addTlvs;
        }

        public void setAddTlvs(boolean addTlvs) {
            this.addTlvs = addTlvs;
        }
    }
}