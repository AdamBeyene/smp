package com.telemessage.simulators.common.conf;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.nio.charset.spi.CharsetProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CombinedCharsetProvider extends CharsetProvider {

    // Array of CharsetProvider classes to register
    private static final CharsetProvider[] PROVIDERS = {
            new net.freeutils.charset.CharsetProvider(),   // JCharset provider
            new org.smpp.charset.Gsm7BitCharsetProvider()  // SMPP provider for GSM7
    };

    // Cache for previously resolved charsets
    private final Map<String, Charset> charsetCache = new ConcurrentHashMap<>();

    public CombinedCharsetProvider() {
        // Public no-argument constructor required for SPI
        log.info("CombinedCharsetProvider initialized for SPI registration");
    }

    @Override
    public Charset charsetForName(String charsetName) {
        if (charsetName == null) {
            return null;
        }

        log.debug("Looking up charset: {}", charsetName);

        // Check cache first
        return charsetCache.computeIfAbsent(charsetName, name -> {
            for (CharsetProvider provider : PROVIDERS) {
                try {
                    Charset charset = provider.charsetForName(name);
                    if (charset != null) {
                        log.debug("Found charset '{}' using provider: {}", name, provider.getClass().getSimpleName());
                        return charset;
                    }
                } catch (Exception e) {
                    log.warn("Error looking up charset '{}' with provider {}: {}", name, provider.getClass().getSimpleName(), e.getMessage());
                }
            }
            log.warn("Charset '{}' not found in any provider", name);
            return null;
        });
    }

    @Override
    public Iterator<Charset> charsets() {
        List<Iterator<Charset>> iterators = new ArrayList<>();
        for (CharsetProvider provider : PROVIDERS) {
            iterators.add(provider.charsets());
        }

        return new Iterator<>() {
            private int currentProviderIndex = 0;

            @Override
            public boolean hasNext() {
                while (currentProviderIndex < iterators.size()) {
                    if (iterators.get(currentProviderIndex).hasNext()) {
                        return true;
                    }
                    currentProviderIndex++;
                }
                return false;
            }

            @Override
            public Charset next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return iterators.get(currentProviderIndex).next();
            }
        };
    }
}
