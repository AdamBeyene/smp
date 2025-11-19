package com.telemessage.simulators.controllers.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for message search requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageSearchRequest {
    private String messageText;
    private String httpText;
    private String smppText;
    private String source;
    private String destination;
    private String providerId;
    private String messageId;
    private Boolean includeDeliveryReceipts;
    private String direction;
    private String recipientType; // SRC, DST, BOTH
    private String textType;      // SMPP, HTTP, ANY
}
