package com.telemessage.simulators.http.modern;

import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

/**
 * Cellcom-specific HTTP provider handler.
 * Implements Cellcom's SMS gateway API requirements and behaviors.
 */
@Slf4j
public class CellcomHttpProviderHandler implements HttpProviderHandler {

    // Cellcom-specific configurations
    private static final int MAX_MESSAGE_LENGTH = 1530; // Cellcom supports longer messages
    private static final int MAX_CONCAT_PARTS = 10;
    private static final String MESSAGE_ID_PREFIX = "cellcom-";

    @Override
    public ModernHttpSimulator.HttpSendResult processMessage(ModernHttpSimulator.HttpSendRequest request) {
        try {
            // Cellcom-specific validation
            String to = request.getTo();
            if (to == null || to.isEmpty()) {
                return ModernHttpSimulator.HttpSendResult.error("Destination number is required");
            }

            // Validate Israeli phone number format for Cellcom
            if (!isValidIsraeliNumber(to)) {
                return ModernHttpSimulator.HttpSendResult.error("Invalid Israeli phone number format");
            }

            // Check message length
            String text = request.getText();
            if (text != null) {
                // Cellcom uses UTF-16 for Hebrew/mixed content
                int messageBytes = text.getBytes("UTF-16BE").length;
                if (messageBytes > MAX_MESSAGE_LENGTH) {
                    return ModernHttpSimulator.HttpSendResult.error("Message exceeds Cellcom size limit");
                }
            }

            // Check for concatenation support
            if (request.getMetadata() != null) {
                String concat = request.getMetadata().get("concatenation");
                if ("true".equalsIgnoreCase(concat)) {
                    // Cellcom supports concatenation
                    int parts = calculateParts(text);
                    if (parts > MAX_CONCAT_PARTS) {
                        return ModernHttpSimulator.HttpSendResult.error("Message requires " + parts +
                            " parts, exceeds Cellcom limit of " + MAX_CONCAT_PARTS);
                    }
                    log.debug("Message will be sent as {} concatenated parts", parts);
                }
            }

            // Generate Cellcom-specific message ID
            String messageId = MESSAGE_ID_PREFIX + UUID.randomUUID().toString();

            // Check for premium services
            if (request.getMetadata() != null) {
                String serviceType = request.getMetadata().get("service_type");
                if ("premium".equalsIgnoreCase(serviceType)) {
                    log.info("Processing premium SMS for Cellcom: {}", to);
                    // Premium SMS handling would go here
                }
            }

            log.info("Processing Cellcom message {}: to={}, length={}",
                    messageId, to, text != null ? text.length() : 0);

            // Simulate Cellcom API call
            // In real implementation, this would call actual Cellcom API
            return ModernHttpSimulator.HttpSendResult.success(messageId);

        } catch (Exception e) {
            log.error("Error processing Cellcom message", e);
            return ModernHttpSimulator.HttpSendResult.error("Cellcom processing failed: " + e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return "cellcom";
    }

    @Override
    public boolean supportsProvider(String provider) {
        return "cellcom".equalsIgnoreCase(provider) ||
               "cellcom-il".equalsIgnoreCase(provider);
    }

    /**
     * Validates Israeli phone number format.
     * Supports both local (05x-xxxxxxx) and international (+9725x-xxxxxxx) formats.
     */
    private boolean isValidIsraeliNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return false;
        }

        // Remove non-digits for validation
        String digits = phoneNumber.replaceAll("[^0-9+]", "");

        // Israeli mobile patterns
        // Local: 050-058, 052-055 (10 digits)
        // International: +97250-58, +97252-55 (13 digits with +972)
        if (digits.startsWith("+972")) {
            // International format
            return digits.length() == 13 &&
                   digits.substring(4, 6).matches("5[0-58]");
        } else if (digits.startsWith("05")) {
            // Local format
            return digits.length() == 10 &&
                   digits.substring(0, 3).matches("05[0-58]");
        }

        return false;
    }

    /**
     * Calculates number of SMS parts needed for concatenation.
     */
    private int calculateParts(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        try {
            // Cellcom uses UTF-16 for Hebrew
            byte[] bytes = text.getBytes("UTF-16BE");
            int singleSmsSize = 70; // UTF-16 single SMS
            int concatSmsSize = 67; // UTF-16 with concat headers

            if (bytes.length <= singleSmsSize) {
                return 1;
            }

            return (bytes.length + concatSmsSize - 1) / concatSmsSize;
        } catch (Exception e) {
            log.error("Error calculating message parts", e);
            return 1;
        }
    }
}