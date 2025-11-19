package com.telemessage.simulators;

import lombok.extern.slf4j.Slf4j;



@Slf4j
public class Runner {
//    static Logger log = LoggerFactory.getLogger(Runner.class);
//    public static final int QUEUE_SIZE = 10000;
//    public static void main(String... args) throws Exception {
/*
        String action = args == null || args.length <= 0 || args[0].contains("-D") ? "run" : args[0];
        String env="";
        if(args != null && args.length > 0){
            for (String arg : args) {
                if(arg.contains("-D") && arg.contains("env")) {
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
            SMPPSimulator.get().start();
            HttpSimulator.get().start();
//            WebStarter.startJetty();
            log.info("Started");
        }
    }

    private static void stop() throws Exception {
        try {
            String url = "http://localhost:" + System.getProperty("com.telemessage.simulators.web.port", "0") + "/" + "sim/shutdown";
            log.info("trying to GET: " + url);
            Request.Get(url).execute();
        } catch (Exception ignore){}
        */
//    }
}
