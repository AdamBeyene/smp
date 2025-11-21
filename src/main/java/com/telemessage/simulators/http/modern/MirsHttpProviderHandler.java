package com.telemessage.simulators.http.modern;

import lombok.extern.slf4j.Slf4j;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * MIRS (Mobile Internet Radio System) HTTP provider handler.
 * Implements MIRS-specific push-to-talk and messaging requirements.
 */
@Slf4j
public class MirsHttpProviderHandler implements HttpProviderHandler {

    // MIRS-specific configurations
    private static final int MAX_MESSAGE_LENGTH = 500; // MIRS has shorter limit
    private static final String MESSAGE_ID_PREFIX = "mirs-";
    private static final String DATE_FORMAT = "yyyyMMddHHmmss";

    @Override
    public ModernHttpSimulator.HttpSendResult processMessage(ModernHttpSimulator.HttpSendRequest request) {
        try {
            // MIRS-specific validation
            String to = request.getTo();
            if (to == null || to.isEmpty()) {
                return ModernHttpSimulator.HttpSendResult.error("MIRS subscriber ID is required");
            }

            // Validate MIRS subscriber format
            if (!isValidMirsSubscriber(to)) {
                return ModernHttpSimulator.HttpSendResult.error("Invalid MIRS subscriber ID format");
            }

            // Check message type (text vs PTT notification)
            String messageType = "text"; // default
            if (request.getMetadata() != null) {
                messageType = request.getMetadata().getOrDefault("message_type", "text");
            }

            // Handle different message types
            switch (messageType.toLowerCase()) {
                case "ptt":
                    return handlePttNotification(request);
                case "location":
                    return handleLocationMessage(request);
                case "emergency":
                    return handleEmergencyMessage(request);
                default:
                    return handleTextMessage(request);
            }

        } catch (Exception e) {
            log.error("Error processing MIRS message", e);
            return ModernHttpSimulator.HttpSendResult.error("MIRS processing failed: " + e.getMessage());
        }
    }

    /**
     * Handles regular text message.
     */
    private ModernHttpSimulator.HttpSendResult handleTextMessage(ModernHttpSimulator.HttpSendRequest request) {
        String text = request.getText();
        if (text == null || text.isEmpty()) {
            return ModernHttpSimulator.HttpSendResult.error("Message text is required for MIRS");
        }

        // MIRS has strict length limitations
        if (text.length() > MAX_MESSAGE_LENGTH) {
            return ModernHttpSimulator.HttpSendResult.error("Message exceeds MIRS limit of " +
                MAX_MESSAGE_LENGTH + " characters");
        }

        // Check for group messaging
        boolean isGroupMessage = false;
        if (request.getMetadata() != null) {
            isGroupMessage = "true".equalsIgnoreCase(
                request.getMetadata().get("group_message"));
        }

        // Generate MIRS message ID with timestamp
        String timestamp = new SimpleDateFormat(DATE_FORMAT).format(new Date());
        String messageId = MESSAGE_ID_PREFIX + timestamp + "-" +
            UUID.randomUUID().toString().substring(0, 8);

        log.info("Processing MIRS text message {}: to={}, group={}, length={}",
                messageId, request.getTo(), isGroupMessage, text.length());

        // In real implementation, would call MIRS API
        return ModernHttpSimulator.HttpSendResult.success(messageId);
    }

    /**
     * Handles PTT (Push-To-Talk) notification.
     */
    private ModernHttpSimulator.HttpSendResult handlePttNotification(ModernHttpSimulator.HttpSendRequest request) {
        // PTT notifications don't have text content
        String pttGroup = request.getMetadata() != null ?
            request.getMetadata().get("ptt_group") : null;

        if (pttGroup == null) {
            return ModernHttpSimulator.HttpSendResult.error("PTT group is required for PTT notifications");
        }

        String messageId = MESSAGE_ID_PREFIX + "ptt-" + UUID.randomUUID().toString();

        log.info("Processing MIRS PTT notification {}: to={}, group={}",
                messageId, request.getTo(), pttGroup);

        return ModernHttpSimulator.HttpSendResult.success(messageId);
    }

    /**
     * Handles location-based message.
     */
    private ModernHttpSimulator.HttpSendResult handleLocationMessage(ModernHttpSimulator.HttpSendRequest request) {
        if (request.getMetadata() == null) {
            return ModernHttpSimulator.HttpSendResult.error("Location data required for location message");
        }

        String latitude = request.getMetadata().get("latitude");
        String longitude = request.getMetadata().get("longitude");

        if (latitude == null || longitude == null) {
            return ModernHttpSimulator.HttpSendResult.error("Latitude and longitude are required");
        }

        String messageId = MESSAGE_ID_PREFIX + "loc-" + UUID.randomUUID().toString();

        log.info("Processing MIRS location message {}: to={}, lat={}, lon={}",
                messageId, request.getTo(), latitude, longitude);

        return ModernHttpSimulator.HttpSendResult.success(messageId);
    }

    /**
     * Handles emergency message with high priority.
     */
    private ModernHttpSimulator.HttpSendResult handleEmergencyMessage(ModernHttpSimulator.HttpSendRequest request) {
        // Emergency messages get special handling
        String emergencyCode = request.getMetadata() != null ?
            request.getMetadata().get("emergency_code") : "911";

        String messageId = MESSAGE_ID_PREFIX + "emrg-" + UUID.randomUUID().toString();

        log.warn("Processing MIRS EMERGENCY message {}: to={}, code={}",
                messageId, request.getTo(), emergencyCode);

        // Emergency messages always succeed immediately
        return ModernHttpSimulator.HttpSendResult.success(messageId);
    }

    @Override
    public String getProviderName() {
        return "mirs";
    }

    @Override
    public boolean supportsProvider(String provider) {
        return "mirs".equalsIgnoreCase(provider) ||
               "mirs-il".equalsIgnoreCase(provider) ||
               "iden".equalsIgnoreCase(provider); // iDEN network
    }

    /**
     * Validates MIRS subscriber ID format.
     * MIRS uses special numbering scheme.
     */
    private boolean isValidMirsSubscriber(String subscriberId) {
        if (subscriberId == null) {
            return false;
        }

        // MIRS subscriber IDs are typically 7-8 digits
        // Starting with specific prefixes
        String digits = subscriberId.replaceAll("[^0-9]", "");

        if (digits.length() < 7 || digits.length() > 8) {
            return false;
        }

        // MIRS prefixes (example - adjust based on actual requirements)
        return digits.startsWith("57") || // MIRS prefix
               digits.startsWith("58") || // MIRS prefix
               digits.startsWith("7");    // Special services
    }
}