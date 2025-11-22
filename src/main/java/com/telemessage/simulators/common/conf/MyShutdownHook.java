package com.telemessage.simulators.common.conf;


import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


@Slf4j
@Component
@Profile({"local", "awscrnd","awstest", "cloud", "crnd", "qacharlie"})
public class MyShutdownHook implements ApplicationRunner {

//    KafkaBeans kafka;
//    @Autowired
//    public MyShutdownHook(KafkaBeans kafka) {
//        this.kafka = kafka;
//    }

    @Autowired
    private ConfigurableApplicationContext context;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        context.registerShutdownHook();
//        context.registerShutdownHook(() -> {
//            // Perform your teardown actions here
//            // e.g. close open connections, flush data to disk, etc.
//        });
    }

    @PreDestroy
    public void cleanUp() {
        log.info("Sim tearDown: na");

    }
}
