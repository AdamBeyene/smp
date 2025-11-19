package com.telemessage.simulators.web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpResource {
//
//    static ObjectMapper m = new ObjectMapper();
//
//    @Path("/connection/{id: [0-9]+}/post")
//    @POST
//    public String postConnection(String postData, @PathParam("id") int id,
//                                 @QueryParam("source") String source, @QueryParam("target") String target) throws UnsupportedEncodingException {
//
//
//        return String.valueOf(process(postData, id, source, target));
//    }
//
//    @Path("/connection/{id: [0-9]+}/get")
//    @GET
//    public String getConnection(@PathParam("id") int id) throws UnsupportedEncodingException {
//        return String.valueOf(process("", id));
//    }
//
//    private static Object process(String postData, int id) throws UnsupportedEncodingException {
//        return process(postData, id, "", "");
//    }
//
//    private static Object process(String postData, int id, String source, String target) throws UnsupportedEncodingException {
//        HttpConnection conn = HttpSimulator.get().get(id);
//
//        if (conn == null || !conn.isStarted()) {
//            throw new CustomNotFoundException();
//        }
//
//        String msgId = String.valueOf(Long.parseLong(Utils.generateRandomKey(8, false)));
//        RedisUtils.addDataToRedis(msgId, "receive", postData);
//        RedisUtils.addDataToRedis(msgId, "id", String.valueOf(id));
//        RedisUtils.addDataToRedis(msgId, "date", String.valueOf(new Date(System.currentTimeMillis())));
//        HttpParam[] p = new HttpParam[2];
//
//        //972511000023
//        String destPrefix = "";
//        String destPrefixForStatus = "";
//        Map<String, String> postDataMap = HttpUtils.getMapFromPostData(postData);
//
//        if (postDataMap.size() > 0) {
//            target = postDataMap.get("target");
//        }
//
//        if (!StringUtils.isEmpty(target)) {
//            if (target.startsWith("972")) {
//                target = target.replace("972", "0");
//            }
//
//            destPrefix = target.substring(7,10);
//            destPrefixForStatus = target.substring(0, 7);
//        }
//
////        13: Bind failed --> 4576 (FAILED_SMS_PROVIDER_RESPONSE_LOGIN_INCORRECT)
////            14: Invalid password-->4578 (FAILED_SMS_PROVIDER_RESPONSE_INVALID_PASSWORD)
////            15: Invalid System ID-->4579 (FAILED_SMS_PROVIDER_RESPONSE_INVALID_SYSTEM_ID)
////            20: Message queue full -->4542(New status - FAILED_SMS_PROVIDER_RESPONSE_MESSAGE_QUEUE_FULL)
////        21: Invalid service type-->4592  (FAILED_SMS_PROVIDER_RESPONSE_INVALID_SERVICE_TYPE)
////            130: Bad username or password or IP address XXX.XXX.XXX.XXX not allowed--> 4572 (FAILED_SMS_PROVIDER_RESPONSE_INVALID_IP)
//        if (!StringUtils.isEmpty(conn.getAutomaticDR())) {
//            switch (destPrefixForStatus) {
//                case "0511000": {
//                    p[0] = new HttpParam("deliveryFailureReason", "1");
//                }
//                break;
//                case "0516000": {
//                    p[0] = new HttpParam("deliveryFailureReason", "6");
//                }
//                break;
//                case "0519000": {
//                    p[0] = new HttpParam("deliveryFailureReason", "9");
//                }
//                break;
//                case "0511100": {
//                    p[0] = new HttpParam("deliveryFailureReason", "11");
//                }
//                break;
//                case "0511313": {
//                    p[0] = new HttpParam("deliveryFailureReason", "13");
//                }
//                break;
//                case "0511400": {
//                    p[0] = new HttpParam("deliveryFailureReason", "14");
//                }
//                break;
//                case "0511500": {
//                    p[0] = new HttpParam("deliveryFailureReason", "15");
//                }
//                break;
//                case "0512000": {
//                    p[0] = new HttpParam("deliveryFailureReason", "20");
//                }
//                break;
//                case "0512100": {
//                    p[0] = new HttpParam("deliveryFailureReason", "21");
//                }
//                break;
//                case "0512700": {
//                    p[0] = new HttpParam("deliveryFailureReason", "27");
//                }
//                break;
//                case "0513400": {
//                    p[0] = new HttpParam("deliveryFailureReason", "34");
//                }
//                break;
//                case "0511010": {
//                    p[0] = new HttpParam("deliveryFailureReason", "101");
//                }
//                break;
//                case "0511200": {
//                    p[0] = new HttpParam("deliveryFailureReason", "120");
//                }
//                break;
//                case "0511210": {
//                    p[0] = new HttpParam("deliveryFailureReason", "121");
//                }
//                break;
//                case "0511300": {
//                    p[0] = new HttpParam("deliveryFailureReason", "130");
//                }
//                break;
//                case "0511310": {
//                    p[0] = new HttpParam("deliveryFailureReason", "131");
//                }
//                break;
//                case "0511600": {
//                    p[0] = new HttpParam("deliveryFailureReason", "160");
//                }
//                break;
//                case "0511610": {
//                    p[0] = new HttpParam("deliveryFailureReason", "161");
//                }
//                break;
//                case "0511630": {
//                    p[0] = new HttpParam("deliveryFailureReason", "163");
//                }
//                break;
//                case "0511640": {
//                    p[0] = new HttpParam("deliveryFailureReason", "164");
//                }
//                break;
//                case "0515170": {
//                    p[0] = new HttpParam("deliveryFailureReason", "517");
//                }
//                break;
//                case "0511024": {
//                    p[0] = new HttpParam("deliveryFailureReason", "1024");
//                }
//                break;
//                case "0511908": {
//                    p[0] = new HttpParam("deliveryFailureReason", "1908");
//                }
//                break;
//                case "0518899": {
//                    p[0] = new HttpParam("deliveryFailureReason", "8899");
//                }
//                break;
//
//                default: {
//                    p[0] = new HttpParam("deliveryFailureReason", "0");
//                }
//            }
//
//            p[1] = new HttpParam("operatorPrefix", destPrefix);
//            conn.getConnectionHandler().sendDeliveryReceipt(msgId, conn.getAutomaticDR(), p);
//        }
//        return conn.getConnectionHandler().generateDirectResponse(postData, msgId);
//    }
//
//
//    @Path("/test")
//    @Produces({"application/json"})
//    @Consumes({"application/json"})
//    @GET
//    public AbstractMessage[] test() {
//        HttpMessage sm = new HttpMessage();
//        sm.setText("hhh");
//        sm.setSrc("1111");
//        sm.setDst("2222");
//        sm.setParams(new HttpParam[]{new HttpParam("sss", "kkkk")});
//        DeliveryReceiptHttpMessage dr = new DeliveryReceiptHttpMessage();
//        dr.setMsgId("454545");
//        dr.setStatus(0);
//        dr.setParams(new HttpParam[]{new HttpParam("nnnn", "ttttt")});
//        return new AbstractMessage[]{
//                sm, dr
//        };
//    }
//
//    @Path("/connection/{id: [0-9]+}/send/message")
//    @Produces({"application/json"})
//    @Consumes({"application/json"})
//    @POST
//    public String httpSendMessage(@PathParam("id") int id, HttpMessage msg) {
//        log.info("httpSendMessage " + msg.getText());
//        try {
//            boolean success = HttpSimulator.get().send(id, new HttpRequest(msg.getSrc(), msg.getDst(), msg.getText(), msg.getParams()));
//            if (success) {
//                return "Message is sending.";
//            } else {
//                return "Failed to send message";
//            }
//        } catch (Exception e) {
//            log.error("", e);
//            return "Failed to send message " + e.getMessage();
//        }
//    }
//
//    @Path("/connection/{id: [0-9]+}/send/dr")
//    @Produces({"application/json"})
//    @Consumes({"application/json"})
//    @POST
//    public String httpSendDR(@PathParam("id") int id, DeliveryReceiptHttpMessage dr) {
//        try {
//            boolean success = HttpSimulator.get().sendDr(id, dr);
//            if (success) {
//                return "DR is sending.";
//            } else {
//                return "Failed to send DR";
//            }
//        } catch (Exception e) {
//            log.error("", e);
//            return "Failed to send DR " + e.getMessage();
//        }
//    }
//
//
//    @Path("/info/connection/{id: [0-9]*}")
//    @Produces({"application/json"})
//    @GET
//    public HttpWebConnection[] httpInfoConnection(@PathParam("id") Integer id) {
//        List<HttpWebConnection> conns = new ArrayList<>();
//        if (id != null && id > 0) {
//            HttpConnection c = HttpSimulator.get().get(id);
//            if (c != null)
//                conns.add(new HttpWebConnection(c));
//        } else {
//            List<HttpConnection> cs = new ArrayList<>(HttpSimulator.get().getConnections().values());
//            Collections.sort(cs, new Comparator<HttpConnection>() {
//                @Override
//                public int compare(HttpConnection o1, HttpConnection o2) {
//                    if (o1 == o2) return 0;
//                    if (o1 == null) return -1;
//                    if (o2 == null) return 1;
//                    return o1.getId() - o2.getId();
//                }
//            });
//
//            for (HttpConnection c : cs) {
//                conns.add(new HttpWebConnection(c));
//            }
//        }
//        return conns.toArray(new HttpWebConnection[conns.size()]);
//    }
//
//
//    @Path("/connection/{id: [0-9]+}/start/")
//    @Produces({"application/json"})
//    @GET
//    public String httpInfoStartConnection(@PathParam("id") int id) {
//        HttpConnection c = HttpSimulator.get().get(id);
//        if (c == null)
//            return "No connection with such id " + id;
//
//        if (c.isStarted()) {
//            return "Connection " + id + " is already started";
//        }
//        c.start();
//        return "Connection " + id + " is starting. It could take few moments";
//    }
//
//    @Path("/connection/{id: [0-9]+}/stop/")
//    @Produces({"application/json"})
//    @GET
//    public String httpInfoStopConnection(@PathParam("id") int id) {
//        HttpConnection c = HttpSimulator.get().get(id);
//        if (c == null)
//            return "No connection with such id " + id;
//
//        if (!c.isStarted()) {
//            return "Connection " + id + " is already stopped";
//        }
//        c.stop();
//        return "Connection " + id + " is stopping. It could take few moments";
//    }
}
