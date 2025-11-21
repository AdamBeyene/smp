package com.telemessage.simulators.http.modern;

/**
 * Interface for HTTP provider-specific message handling.
 * Each provider (GCM, Cellcom, MIRS, etc.) can implement this
 * to provide custom processing logic.
 */
public interface HttpProviderHandler {

    /**
     * Process a message according to provider-specific rules.
     *
     * @param request The HTTP send request
     * @return Result of the processing
     */
    ModernHttpSimulator.HttpSendResult processMessage(ModernHttpSimulator.HttpSendRequest request);

    /**
     * Get the provider name this handler supports.
     */
    String getProviderName();

    /**
     * Check if this handler supports the given provider.
     */
    boolean supportsProvider(String provider);
}