package com.telemessage.simulators;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telemessage.qatools.annotation.EnableMcpServer;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.http.HttpSimulator;
import com.telemessage.simulators.smpp.SMPPSimulator;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication(scanBasePackages = {"com.telemessage.simulators"})
@EnableScheduling
@EnableRetry
@EnableMcpServer
public class TM_QA_SMPP_SIMULATOR_Application {

    public static  ObjectMapper masterMapper = new ObjectMapper();
    public static final int QUEUE_SIZE = 10000;

    MessagesCache cacheService;
    EnvConfiguration conf;
    static Simulator smppSim;  // Can be either SMPPSimulator (Logica) or CloudhopperSimulator
    static HttpSimulator httpSim;

    public TM_QA_SMPP_SIMULATOR_Application(MessagesCache cacheService,
                                            EnvConfiguration conf,
                                            @Qualifier("smppSimulator") Simulator smppSim,
                                            HttpSimulator httpSim
    ) {
        this.conf = conf;
        this.smppSim = smppSim;
        this.httpSim = httpSim;
        this.cacheService = cacheService;
    }

    @PostConstruct
    public void init() {
        if (conf == null || cacheService == null) {
            throw new RuntimeException("EnvConfiguration is not set");
        }
        System.out.println("Env Configuration is available");
    }

    public static void main(String[] args) throws Exception {

        SpringApplication.run(TM_QA_SMPP_SIMULATOR_Application.class, args);

        String action = args == null || args.length <= 0 || args[0].contains("-D") ? "run" : args[0];
        String env = "";
        if (args != null && args.length > 0) {
            for (String arg : args) {
                if (arg.contains("-D") && arg.contains("env")) {
                    env = arg.contains("-D") && arg.contains("env") ?
                            arg.split("=")[1] : "LOCAL";
                    System.setProperty("env", env);
                }
            }
        }
        EnvUtils.fillSystemProperties();
        log.info("Receiving action: " + action);
        if ("stop".equalsIgnoreCase(StringUtils.defaultString(action).trim())) {
            stop();
            log.info("Finished");
            System.exit(0);
        } else {
            smppSim.start();
            httpSim.start();
            log.info("Started");
        }
    }

    private static void stop() throws Exception {
        try {
            String url = "http://localhost:" + System.getProperty("com.telemessage.simulators.web.port", "0") + "/" + "sim/shutdown";
            log.info("trying to GET: " + url);

            RequestSpecification request = RestAssured.given();
            Response r = request.get(url);
        } catch (Exception ignore){}
    }
}
