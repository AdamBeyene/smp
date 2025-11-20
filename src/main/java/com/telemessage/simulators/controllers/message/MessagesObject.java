package com.telemessage.simulators.controllers.message;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Base64;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessagesObject {
    private long simId;
    private String id;
    private String providerId;
    private String text;
    private String from;
    private String to;
    private String dir;
    private String sendMessageSM;
    private String messageTime;
    private String deliveryReceiptShortMessage;
    private String httpMessage;
    private String deliveryReceiptHttpMessage;
    private String deliveryReceiptTime;
    private String directResponse;
    private String messageEncoding;

    // Multipart message support (optional, backward compatible)
    private Integer partNumber;
    private Integer totalParts;
    private Integer referenceNumber;

    // Cloudhopper-specific concatenation metadata (optional, backward compatible)
    private String concatenationType;      // "UDHI", "SAR", "PAYLOAD", "TEXT_BASE", "UDHI_PAYLOAD", "DEFAULT"
    private Boolean encodingCorrected;     // true if smart detection corrected encoding
    private String declaredEncoding;       // Original declared encoding from data_coding
    private String detectedEncoding;       // Actual detected encoding (may differ from declared)

    // Cloudhopper-specific SMPP protocol details (optional, backward compatible)
    private Byte esmClass;                 // ESM class byte (0x00=default, 0x40=UDHI)
    private Byte dataCoding;               // Data coding byte from PDU
    private String smppVersion;            // "3.3", "3.4", "5.0"
    private String implementationType;     // "Logica" or "Cloudhopper"

    // Cloudhopper-specific quality metrics (optional, backward compatible)
    private Double encodingConfidence;     // 0.0-1.0 score from smart detection
    private Integer unicodeBlockChanges;   // Unicode block changes (coherence metric)

    //TODO add Provider Result
    //TODO break http message received and set src dest and text fields // report issue
    //TODO text of DR message should be set to the text of status
    //

    // To store the raw binary message data
    @JsonIgnore
    private byte[] rawMessageBytes;

    // For JSON serialization/deserialization
    public String getRawMessageBytesBase64() {
        return rawMessageBytes != null ? Base64.getEncoder().encodeToString(rawMessageBytes) : null;
    }

    public void setRawMessageBytesBase64(String base64) {
        if (base64 != null && !base64.isEmpty()) {
            this.rawMessageBytes = Base64.getDecoder().decode(base64);
        }
    }

    // Helper method to determine if message has binary content
    public boolean hasBinaryContent() {
        return rawMessageBytes != null && rawMessageBytes.length > 0;
    }

    // Add methods to better handle binary data

    /**
     * Determines if the message contains binary data
     */
    public boolean hasBinaryData() {
        return rawMessageBytes != null;
    }

    /**
     * Gets a safe display representation of the message content
     * even if it contains binary or non-displayable characters
     */
    public String getSafeDisplayText() {
        if (text == null) return "";

        // Replace non-displayable characters with placeholders for UI
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 32 && c <= 126 || c == '\r' || c == '\n') {
                sb.append(c);
            } else {
                // Show unicode code point for non-displayable chars
                sb.append("\\u").append(String.format("%04x", (int)c));
            }
        }
        return sb.toString();
    }
}
