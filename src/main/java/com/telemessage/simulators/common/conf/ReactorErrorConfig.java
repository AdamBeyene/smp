package com.telemessage.simulators.common.conf;

import javax.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.buf.ByteChunk;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;
import reactor.core.Exceptions.SourceException;


@Slf4j
@Configuration
public class ReactorErrorConfig {

    @PostConstruct
    public void init() {
        // Drop known overflow exceptions, log at DEBUG
        Hooks.onErrorDropped(error -> {
            if (error instanceof SourceException ||
                    error instanceof ByteChunk.BufferOverflowException) {
                log.debug("Dropped overflow: {}", error.toString());
                return;
            }
            log.error("Unhandled dropped error", error);
        });

        // Warn if onNext signals are dropped due to backpressure
        Hooks.onNextDropped(value -> {
            log.warn("Dropped value due to backpressure: {}", value);
        });

        // Capture operator errors, log and rethrow
        Hooks.onOperatorError((throwable, data) -> {
            log.warn("Operator error on [{}]", data, throwable);
            return throwable;
        });
    }
}