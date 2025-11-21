package com.telemessage.simulators.http.modern;

import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

/**
 * Google Cloud Messaging (GCM) HTTP provider handler.
 * Implements GCM-specific message processing logic.
 */
@Slf4j
public class GcmHttpProviderHandler implements HttpProviderHandler {

    private static final int MAX_MESSAGE_SIZE = 4096; // GCM limit

    @Override
    public ModernHttpSimulator.HttpSendResult processMessage(ModernHttpSimulator.HttpSendRequest request) {
        try {
            // GCM-specific validation
            if (request.getText() != null && request.getText().length() > MAX_MESSAGE_SIZE) {
                return ModernHttpSimulator.HttpSendResult.error("Message exceeds GCM size limit of " + MAX_MESSAGE_SIZE + " bytes");
            }

            // Check for GCM registration ID (in metadata)
            if (request.getMetadata() != null) {
                String registrationId = request.getMetadata().get("gcm_registration_id");
                if (registrationId == null || registrationId.isEmpty()) {
                    log.warn("GCM registration ID not provided, using phone number as fallback");
                }
            }

            // Generate GCM message ID format
            String messageId = "gcm-" + UUID.randomUUID().toString();

            log.debug("Processing GCM message {}: to={}, size={}",
                    messageId, request.getTo(),
                    request.getText() != null ? request.getText().length() : 0);

            // Simulate GCM processing
            // In real implementation, this would call actual GCM API
            return ModernHttpSimulator.HttpSendResult.success(messageId);

        } catch (Exception e) {
            log.error("Error processing GCM message", e);
            return ModernHttpSimulator.HttpSendResult.error("GCM processing failed: " + e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return "gcm";
    }

    @Override
    public boolean supportsProvider(String provider) {
        return "gcm".equalsIgnoreCase(provider) ||
               "google".equalsIgnoreCase(provider) ||
               "fcm".equalsIgnoreCase(provider); // Also support FCM
    }
}