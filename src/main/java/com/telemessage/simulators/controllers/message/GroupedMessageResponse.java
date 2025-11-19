package com.telemessage.simulators.controllers.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for grouped messages (supports both single messages and concatenated message groups)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupedMessageResponse {
    /**
     * Type of message entry: "single" or "concat"
     */
    private String type;

    /**
     * For single messages, contains the message object
     * For concat messages, contains the assembled full message
     */
    private MessagesObject message;

    /**
     * For concat messages only: metadata about the concatenation
     */
    private ConcatMetadata metadata;

    /**
     * For concat messages only: array of individual parts
     */
    private MessagesObject[] parts;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ConcatMetadata {
        private Integer referenceNumber;
        private Integer totalParts;
        private Integer receivedParts;
        private boolean complete;
        private String assembledText;
    }
}
