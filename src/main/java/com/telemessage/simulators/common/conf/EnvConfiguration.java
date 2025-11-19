package com.telemessage.simulators.common.conf;

import lombok.Data;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import jakarta.annotation.Priority;


@Configuration
@EnableAutoConfiguration
@ConfigurationProperties(prefix = "sim.env.configurations")
@Primary
@Priority(1)
@Data
public class EnvConfiguration {
     String baseUrl;
     int basePort;
     int serverManagementPort;

     String envCurrent;
     String localSharedLocation;
     String envName;
     String sharedFolderName;
     String envUrl;
     String envHost;
     boolean activateSmppSim = false;
     int smppWebPort = 8020;
     boolean redisEnabled = false;
     String redisHost;
     String redisAuth;

     int httpWebPort = 8032;

}
