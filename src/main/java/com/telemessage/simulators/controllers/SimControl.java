package com.telemessage.simulators.controllers;


import com.telemessage.simulators.Simulator;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.http.HttpSimulator;
import com.telemessage.simulators.web.HttpResource;
import com.telemessage.simulators.web.SMPPResource;
import lombok.extern.slf4j.Slf4j;
import org.simpleframework.xml.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Description;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Timer;
import java.util.TimerTask;

@Slf4j
@RestController
@RequestMapping("/sim")
public class SimControl {

    EnvConfiguration conf;

    static Simulator smppSim;
    static HttpSimulator httpSim;
    @Autowired
    public SimControl(EnvConfiguration conf,
                      @Qualifier("smppSimulator") Simulator smppSim,
                      HttpSimulator httpSim
    ) {
        this.conf = conf;
        this.smppSim = smppSim;
        this.httpSim = httpSim;
    }
    

//    @Path("/test")
//    @GET
//    public String test() {
//        return "OK";
//    }
    @RequestMapping(method = RequestMethod.GET,
            path = "/test",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    @Description("Test OK")
    public ResponseEntity<String> health() {

        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                .body("OK");
    }

    @RequestMapping(method = RequestMethod.GET, path = "/restart", produces = MediaType.APPLICATION_JSON_VALUE, name = "RESTART")
    public ResponseEntity<String> restart() {
        scheduleShutdownSimulators(1000);
        startSimulators();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("SMPP and HTTP Services are restarting...");
    }

//    @Path("/restart")
//    @Produces({"application/json"})
//    @GET
    @RequestMapping(method = RequestMethod.GET, path = "/shutdown", produces = MediaType.APPLICATION_JSON_VALUE,
            name = "RESTART")
    @Description("Test health get OK \uD83E\uDD2C")
    public ResponseEntity<String> shutdown() {
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                System.exit(-1);
//            }
//        }, 5000);
        scheduleShutdownSimulators(1);

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("Service is going down");
    }


    @RequestMapping(method = RequestMethod.GET, path = "/start", produces = MediaType.APPLICATION_JSON_VALUE, name = "START")
    public ResponseEntity<String> start() {
        startSimulators();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("SMPP and HTTP Services are starting...");
    }

    @Path("/smpp")
    public SMPPResource getSMPPResource() {
        return new SMPPResource();
    }

    @Path("/http")
    public HttpResource getHttpResource() {
        return new HttpResource();
    }



    private void scheduleShutdownSimulators(long delay) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    smppSim.shutdown();
                } catch (Exception ignore){}
                try {
                    httpSim.stop();
                } catch (Exception ignore){}
            }
        }, delay);
        log.info("Sim Stopped");
    }

    private void shutdownSimulators() {
        try {
            smppSim.shutdown();
        } catch (Exception ignore) {}
        try {
            httpSim.stop();
        } catch (Exception ignore) {}
        log.info("Sim Stopped");
    }

    private void startSimulators() {
        try {
            smppSim.start();
        } catch (Exception ignore) {}
        try {
            httpSim.start();
        } catch (Exception ignore) {}
        log.info("Sim Started");
    }

}
