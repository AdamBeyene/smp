package com.telemessage.simulators.web;

import jakarta.ws.rs.Path;

//@Path ("/sim")
public class SimulatorEntry {
//
//    @Path("/smpp")
//    public SMPPResource getSMPPResource() {
//        return new SMPPResource();
//    }
//
//    @Path("/http")
//    public HttpResource getHttpResource() {
//        return new HttpResource();
//    }
//
//    @Path("/restart")
//    @Produces({"application/json"})
//    @GET
//    public String restart() {
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                System.exit(1);
//            }
//        }, 1000);
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                try {
//                    SMPPSimulator.get().shutdown();
//                } catch (Exception ignore){}
//                try {
//                    HttpSimulator.get().stop();
//                } catch (Exception ignore){}
//            }
//        }, 1);
//        return "Service is restarting";
//    }
//
//    @Path("/shutdown")
//    @Produces({"application/json"})
//    @GET
//    public String shutdown() {
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                System.exit(-1);
//            }
//        }, 5000);
//        new Timer().schedule(new TimerTask() {
//            @Override
//            public void run() {
//                try {
//                    SMPPSimulator.get().shutdown();
//                } catch (Exception ignore){}
//                try {
//                    HttpSimulator.get().stop();
//                } catch (Exception ignore){}
//            }
//        }, 1);
//        return "Service is going down";
//    }
//
//
//    @Path("/test")
//    @GET
//    public String test() {
//        return "OK";
//    }
}
