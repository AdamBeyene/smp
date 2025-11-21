package com.telemessage.simulators.http.modern;

import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

/**
 * Default HTTP provider handler implementation.
 * Used when no specific provider handler is available.
 */
@Slf4j
public class DefaultHttpProviderHandler implements HttpProviderHandler {

    @Override
    public ModernHttpSimulator.HttpSendResult processMessage(ModernHttpSimulator.HttpSendRequest request) {
        try {
            // Basic validation
            if (request.getTo() == null || request.getTo().isEmpty()) {
                return ModernHttpSimulator.HttpSendResult.error("Missing destination number");
            }
            if (request.getText() == null || request.getText().isEmpty()) {
                return ModernHttpSimulator.HttpSendResult.error("Missing message text");
            }

            // Generate message ID
            String messageId = UUID.randomUUID().toString();

            // Log the processing
            log.debug("Processing message {} via default handler: from={}, to={}, text_length={}",
                    messageId, request.getFrom(), request.getTo(),
                    request.getText() != null ? request.getText().length() : 0);

            // Simulate success (90% success rate)
            if (Math.random() > 0.1) {
                return ModernHttpSimulator.HttpSendResult.success(messageId);
            } else {
                return ModernHttpSimulator.HttpSendResult.error("Simulated delivery failure");
            }

        } catch (Exception e) {
            log.error("Error processing message in default handler", e);
            return ModernHttpSimulator.HttpSendResult.error("Processing failed: " + e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return "default";
    }

    @Override
    public boolean supportsProvider(String provider) {
        return "default".equalsIgnoreCase(provider) || provider == null;
    }
}