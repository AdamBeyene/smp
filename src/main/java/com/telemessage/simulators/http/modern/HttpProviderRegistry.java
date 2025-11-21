package com.telemessage.simulators.http.modern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for HTTP provider handlers.
 * Manages provider-specific implementations.
 */
@Slf4j
@Component
public class HttpProviderRegistry {

    private final Map<String, HttpProviderHandler> handlers = new HashMap<>();

    @PostConstruct
    public void init() {
        // Register default handlers
        registerHandler(new DefaultHttpProviderHandler());
        registerHandler(new GcmHttpProviderHandler());
        registerHandler(new CellcomHttpProviderHandler());
        registerHandler(new MirsHttpProviderHandler());

        log.info("Registered {} HTTP provider handlers", handlers.size());
    }

    /**
     * Register a provider handler.
     */
    public void registerHandler(HttpProviderHandler handler) {
        String providerName = handler.getProviderName().toLowerCase();
        handlers.put(providerName, handler);
        log.debug("Registered handler for provider: {}", providerName);
    }

    /**
     * Get handler for a specific provider.
     */
    public HttpProviderHandler getHandler(String provider) {
        if (provider == null) {
            return handlers.get("default");
        }

        String key = provider.toLowerCase();
        HttpProviderHandler handler = handlers.get(key);

        if (handler == null) {
            log.debug("No specific handler for provider {}, using default", provider);
            return handlers.get("default");
        }

        return handler;
    }

    /**
     * Check if a provider is supported.
     */
    public boolean isProviderSupported(String provider) {
        return provider != null && handlers.containsKey(provider.toLowerCase());
    }
}