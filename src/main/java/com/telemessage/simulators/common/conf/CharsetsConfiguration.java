package com.telemessage.simulators.common.conf;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.spi.CharsetProvider;

@Slf4j
@Configuration
public class CharsetsConfiguration {

    @Bean
    public CharsetProvider combinedCharsetProvider() {
        return new CombinedCharsetProvider();
    }
}

