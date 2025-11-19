package com.telemessage.simulators.web.wrappers;



import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@Slf4j
public class ShortMessage extends AbstractMessage {

    @Schema(description = "Message text to send", example = "my text")
    String text;
    @Schema(description = "Message source device", example = "17810000001")
    String src;
    @Schema(description = "Message destination device", example = "17810000002")
    String dst;
    @Schema(description = "An optional field to provide a callback number.<br>" +
            "This is commonly used to allow the recipient to reply to the message or send a response,<br>" +
            "often seen in SMS services that involve two-way messaging.", example = "17810000003")
    String clb;
    @Schema(description = "Message type of service for the message [default SMS]", example = "SMS/EMS...")
    String serviceType;
    @Schema(description = "Associate messages that belong to the same conversation or transaction [max val=65535]", example = "516")
    Short userMessageRef;
    @Schema(description = " used in situations where messages need to be directed to a particular endpoint,<br>" +
            " like a specific application or virtual service within a system", example = "Extension101")
    String srcSubAddress;
    @Schema(description = " used in situations where messages need to be directed to a particular endpoint,<br>" +
            " like a specific application or virtual service within a system", example = "Extension101")
    String dstSubAddress;
    @Schema(description = "Allows a message to be scheduled for delivery at a specific time in the future", example = "251015100000")
    String scheduleDeliveryTime;
    List<Map<String, String>> params = new ArrayList<>();
    @Schema(description = "Allows to send messages parts in delay [millis]<br>" +
            "if parts size are bigger then delay list, first parts will be sent immediately " +
            "in case delay == 999999999  PART will be skipped", example = "[1000,2000,6000]")

    List<Long> partsDelay = new ArrayList<>();

    ShortMessage.Message_state_enum messageState;

    @Override
    public String getText() {
        // Return the original text without filtering
        return text != null ? text : "";
    }

    @Schema(description = "message states are used to track the status of a message")
    public enum Message_state_enum {
        NONE(-1),
        SCHEDULED(0),
        ENROUTE(1),
        DELIVERED(2),
        EXPIRED(3),
        DELETED(4),
        UNDELIVERABLE(5),
        ACCEPTED(6),
        UNKNOWN(7),
        REJECTED(8),
        SKIPPED(9),
        FAKE_FOR_TEST(100);

        private byte value;

        Message_state_enum(int value) {
            this.value = (byte) value;
        }

        public byte getValue() {
            return value;
        }
    }


    public enum ServiceType {
        CMT("CMT", "Commercial Message Type"),
        SMS("SMS", "Short Message Service"),
        MO("MO", "Mobile Originated"),
        MT("MT", "Mobile Terminated"),
        BULK("BULK", "Bulk Messaging"),
        WAP("WAP", "Wireless Application Protocol"),
        VMS("VMS", "Voice Mail Service"),
        RCS("RCS", "Rich Communication Services"),
        EMS("EMS", "Enhanced Messaging Service"),
        MMS("MMS", "Multimedia Messaging Service"),
        OTP("OTP", "One-Time Password"),
        ALERT("ALERT", "Alert Message"),
        P2P("P2P", "Person-to-Person"),
        A2P("A2P", "Application-to-Person"),
        TEMPLATE("TEMPLATE", "Template Message");

        private final String code;
        private final String description;

        ServiceType(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public String getCode() {
            return code;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return code + ": " + description;
        }

        public static ServiceType fromCode(String code) {
            for (ServiceType type : ServiceType.values()) {
                if (type.getCode().equalsIgnoreCase(code)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown service type code: " + code);
        }
    }


    /**
     * 1. serviceType (String)
     * Description: Specifies the type of service for the message (e.g., commercial SMS, mobile-originated messages). It helps identify the type of service to be used for the message, typically defined by the SMPP system or application.
     * 2. sourceAddr (Address)
     * Description: The address of the sender. This is a representation of the source of the message, typically in the form of an MSISDN (Mobile Station International Subscriber Directory Number) or short code. The class Address handles the formatting and length constraints of this field.
     * 3. destAddr (Address)
     * Description: The address of the recipient. Similar to sourceAddr, it specifies the recipient’s number or short code.
     * 4. esmClass (byte)
     * Description: Specifies the message type. It defines how the message should be handled, for example, whether it is a deliver message, a status report, etc. The esmClass field defines message-specific settings like delivery notification or message mode.
     * 5. registeredDelivery (byte)
     * Description: Defines the delivery receipt options. It indicates whether a delivery receipt should be generated upon successful delivery of the message (i.e., "acknowledgment" or "receipt"). The byte value determines the delivery receipt mechanism.
     * 6. dataCoding (byte)
     * Description: Specifies the encoding scheme used for the message payload (i.e., the message text). This could refer to GSM 7-bit encoding, UCS2 encoding, or other data coding schemes used in SMS.
     * 7. userMessageReference (TLVShort)
     * Description: A unique reference for the message, typically used to associate messages that belong to the same conversation or transaction. It's often used for concatenated SMS messages to track all parts of a multi-part message.
     * 8. sourcePort (TLVShort)
     * Description: The port number of the sending application or system. It can be used to distinguish messages from different applications or services within the same operator network.
     * 9. destinationPort (TLVShort)
     * Description: The port number of the receiving application or system. It allows the recipient system to route the message correctly within its internal network.
     * 10. sarMsgRefNum (TLVShort)
     * Description: The reference number for a segmented SMS. This field is used when the message is part of a multi-part (concatenated) message. It helps group all parts together by using a common reference number across all message segments.
     * 11. sarTotalSegments (TLVUByte)
     * Description: Specifies the total number of parts or segments in a segmented SMS message. This is used to indicate how many parts are required to deliver the entire message.
     * 12. sarSegmentSeqnum (TLVUByte)
     * Description: Specifies the sequence number of the current segment of a multi-part message. This allows the recipient system to assemble all parts of a segmented message in the correct order.
     * 13. payloadType (TLVByte)
     * Description: Indicates the type of the payload (message content). This field could specify whether the payload contains an SMS or some other type of message content (e.g., binary data, a URL, etc.).
     * 14. messagePayload (TLVOctets)
     * Description: The actual message content. This field contains the binary data or text of the SMS message. The TLVOctets class handles the size and encoding constraints of the message payload.
     * 15. privacyIndicator (TLVByte)
     * Description: This field controls whether the message content is encrypted or requires special handling for privacy, such as ensuring confidentiality or marking it as private.
     * 16. callbackNum (TLVOctets)
     * Description: An optional field to provide a callback number. This is commonly used to allow the recipient to reply to the message or send a response, often seen in SMS services that involve two-way messaging.
     * 17. sourceSubaddress (TLVOctets)
     * Description: A subaddress associated with the sender’s address, often used in more complex messaging systems. It can be used to specify a department or specific application within the sender's organization.
     * 18. destSubaddress (TLVOctets)
     * Description: A subaddress associated with the recipient's address. It functions similarly to sourceSubaddress but for the recipient. It can be used to route messages to specific endpoints within a destination system.
     * 19. userResponseCode (TLVByte)
     * Description: Used to store a response code from the recipient, often used in systems where the message might trigger a service or request that requires a response code (e.g., a request for user input).
     * 20. languageIndicator (TLVByte)
     * Description: Specifies the language in which the message is written. This can be used to guide message formatting or help decide how to encode the message if it is multilingual.
     * 21. itsSessionInfo (TLVShort)
     * Description: Typically used to store session-specific information for the SMPP protocol. It can be used in certain applications to manage session-specific data across multiple message exchanges.
     * TLV (Tag-Length-Value) Format:
     * Many of the fields are encapsulated in TLV classes (e.g., TLVShort, TLVUByte, TLVByte, TLVOctets). TLV is a common encoding method used in protocols like SMPP to carry variable-length parameters. Each TLV object stores:
     *
     * Tag: A unique identifier for the parameter.
     * Length: The size of the value.
     * Value: The actual data or content associated with the tag.
     */
}
