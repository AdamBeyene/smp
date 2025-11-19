package com.telemessage.simulators.web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SMPPResource {
//
//    @Path("/test")
//    @Produces({"application/json"})
//    @Consumes({"application/json"})
//    @GET
//    public AbstractMessage[] test() {
//        ShortMessage sm = new ShortMessage();
//        sm.setText("hhh");
//        sm.setSrc("1111");
//        sm.setDst("2222");
//
//        DeliveryReceiptShortMessage dr = new DeliveryReceiptShortMessage();
//        dr.setProviderResult("454545");
//        dr.setStatus(DeliveryReceiptShortMessage.DRs.DELIVRD);
//        dr.setSrc("1111");
//        dr.setDst("2222");
//        return new AbstractMessage[] {
//                sm, dr
//        };
//    }
//
//    @Path("/connection/{id: [0-9]+}/send/message")
//    @Produces({"application/json"})
//    @Consumes({"application/json"})
//    @POST
//    public String smppSendMessage(@PathParam("id") int id, ShortMessage msg) {
//        return sendMessage(id, msg.getSrc(), msg.getDst(), msg.getServiceType(), msg.getText(), msg.getClb(), msg.getUserMessageRef(), msg.getSrcSubAddress(), msg.getDstSubAddress(),
//                msg.getScheduleDeliveryTime(), msg.getMessageState(), true , msg.getParams());
//    }
//
//    @Path("/connection/{id: [0-9]+}/send/message/random-parts-concatenation")
//    @Produces({"application/json"})
//    @Consumes({"application/json"})
//    @POST
//    public String smppSendPartFromConcatenationMessage(@PathParam("id") int id, ShortMessage msg) {
//        return sendMessage(id, msg.getSrc(), msg.getDst(), msg.getServiceType(), msg.getText(), msg.getClb(), msg.getUserMessageRef(), msg.getSrcSubAddress(), msg.getDstSubAddress(),
//                msg.getScheduleDeliveryTime(), msg.getMessageState(), false ,  msg.getParams());
//    }
//
//    @Path("/connection/{id: [0-9]+}/send/dr")
//    @Produces({"application/json"})
//    @Consumes({"application/json"})
//    @POST
//    public String smppSendDR(@PathParam("id") int id, DeliveryReceiptShortMessage dr) {
//        return sendMessage(id, dr.getSrc(), dr.getDst(), dr.getServiceType(), dr.getText(), null, null, null, null, null, null, true , null);
//    }
//
//    protected String sendMessage(int id, String src, String dst, String serviceType, String text, String clb,
//                                 Short userMessageRef, String srcSubAddress, String dstSubAddress, String scheduleDeliveryTime, ShortMessage.Message_state_enum messageState, boolean sendAllPartsOfConcatenateMessage , List<Map<String, String>>  optional_params ) {
//        try {
//            boolean success = SMPPSimulator.get().send(id, new SMPPRequest(src, dst, text, serviceType, clb,
//                    userMessageRef, srcSubAddress, dstSubAddress, scheduleDeliveryTime, messageState == null ? -1 : messageState.getValue() , optional_params), sendAllPartsOfConcatenateMessage);
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
//    @Path("/info/connection/{id: [0-9]*}")
//    @Produces({"application/json"})
//    @GET
//    public SMPPWebConnection[] smppInfoConnection(@PathParam("id") Integer id) {
//        List<SMPPWebConnection> conns = new ArrayList<>();
//        if (id != null && id > 0) {
//            SMPPConnectionConf c = SMPPSimulator.get().get(id);
//            if (c != null)
//                conns.add(new SMPPWebConnection(c));
//        } else {
//
//            List<SMPPConnectionConf> cs = new ArrayList<>(SMPPSimulator.get().getConnections().values());
//            Collections.sort(cs, new Comparator<SMPPConnectionConf>() {
//                @Override
//                public int compare(SMPPConnectionConf o1, SMPPConnectionConf o2) {
//                    if (o1 == o2) return 0;
//                    if (o1 == null) return -1;
//                    if (o2 == null) return 1;
//                    return o1.getId() - o2.getId();
//                }
//            });
//
//            for (SMPPConnectionConf c : cs) {
//                conns.add(new SMPPWebConnection(c));
//            }
//        }
//        return conns.toArray(new SMPPWebConnection[conns.size()]);
//    }
//
//
//    @Path("/connection/{id: [0-9]+}/start/{type: transmitter|receiver}")
//    @Produces({"application/json"})
//    @GET
//    public String smppStartConnection(@PathParam("id") int id, @PathParam("type") String type) {
//        SMPPConnectionConf c = SMPPSimulator.get().get(id);
//        if (c == null)
//            return "No connection with such id " + id;
//        if (type == null)
//            return "Please, choose receiver or transmitter for connection " + id;
//
//        boolean isTr = "transmitter".equals(type);
//        SMPPConnection transmitter = isTr ? c.getTransmitter() : null;
//        boolean isRec = "receiver".equals(type);
//        SMPPConnection receiver = isRec ? c.getReceiver() : null;
//
//        if (isTr && transmitter == null) {
//            return "No transmitter for connection " + id;
//        } else if (isTr && transmitter.isReference()) {
//            return "Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver();
//        } else if (isTr && transmitter.isBound()) {
//            return "Transmitter for connection " + id + " is already started";
//        } else if (isTr && !transmitter.isBound()) {
//            try {
//                transmitter.connect();
//                return "Transmitter for connection " + id + " is starting. It could take few moments";
//            } catch (Exception e) {
//                return "Failed to start transmitter for connection: " + id + ", with error: " + e.getMessage();
//            }
//        }
//
//        if (isRec && receiver == null) {
//            return "No receiver for connection " + id;
//        } else if (isRec && receiver.isBound()) {
//            return "Receiver for connection " + id + " is already started";
//        } else if (isRec && !receiver.isBound()) {
//            try {
//                receiver.connect();
//                return "Receiver for connection " + id + " is starting. It could take few moments";
//            } catch (Exception e) {
//                return "Failed to start receiver for connection: " + id + ", with error: " + e.getMessage();
//            }
//        }
//
//        return "";
//    }
//
//    @Path("/connection/{id: [0-9]+}/reset/{type: transmitter|receiver}")
//    @Produces({"application/json"})
//    @GET
//    public String smppResetConnection(@PathParam("id") int id, @PathParam("type") String type) {
//        SMPPConnectionConf c = SMPPSimulator.get().get(id);
//        if (c == null)
//            return "No connection with such id " + id;
//        if (type == null)
//            return "Please, choose receiver or transmitter for connection " + id;
//
//        boolean isTr = "transmitter".equals(type);
//        SMPPConnection transmitter = isTr ? c.getTransmitter() : null;
//        boolean isRec = "receiver".equals(type);
//        SMPPConnection receiver = isRec ? c.getReceiver() : null;
//
//
//        if (isTr && transmitter == null) {
//            return "No transmitter for connection " + id;
//        }else if (isTr && transmitter.isReference()) {
//            return "Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver();
//        } else if (isTr && !transmitter.isBound()) {
//            return "Transmitter for connection " + id + " is already stopped";
//        } else if (isTr && transmitter.isBound()) {
//            try {
//                transmitter.disconnect();
//                return "Transmitter for connection " + id + " is stopping. It could take few moments";
//            } catch (Exception e) {
//                return "Failed to stop transmitter for connection: " + id + ", with error: " + e.getMessage();
//            }
//        }
//
//        if (isRec && receiver == null) {
//            return "No receiver for connection " + id;
//        } else if (isRec && !receiver.isBound()) {
//            return "Receiver for connection " + id + " is already stopped";
//        } else if (isRec && receiver.isBound()) {
//            try {
//                receiver.disconnect();
//                return "Receiver for connection " + id + " is stopping. It could take few moments";
//            } catch (Exception e) {
//                return "Failed to stop receiver for connection: " + id + ", with error: " + e.getMessage();
//            }
//        }
//
//        return "";
//    }
//
//    @Path("/connection/{id: [0-9]+}/stop/{type: transmitter|receiver}")
//    @Produces({"application/json"})
//    @GET
//    public String smppStopConnection(@PathParam("id") int id, @PathParam("type") String type) {
//        SMPPConnectionConf c = SMPPSimulator.get().get(id);
//        if (c == null)
//            return "No connection with such id " + id;
//        if (type == null)
//            return "Please, choose receiver or transmitter for connection " + id;
//
//        boolean isTr = "transmitter".equals(type);
//        SMPPConnection transmitter = isTr ? c.getTransmitter() : null;
//        boolean isRec = "receiver".equals(type);
//        SMPPConnection receiver = isRec ? c.getReceiver() : null;
//
//
//        if (isTr && transmitter == null) {
//            return "No transmitter for connection " + id;
//        }else if (isTr && transmitter.isReference()) {
//            return "Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver();
//        } else if (isTr && !transmitter.isBound()) {
//            return "Transmitter for connection " + id + " is already stopped";
//        } else if (isTr && transmitter.isBound()) {
//            try {
//                transmitter.shutdown();
//                return "Transmitter for connection " + id + " is stopping. It could take few moments";
//            } catch (Exception e) {
//                return "Failed to stop transmitter for connection: " + id + ", with error: " + e.getMessage();
//            }
//        }
//
//        if (isRec && receiver == null) {
//            return "No receiver for connection " + id;
//        } else if (isRec && !receiver.isBound()) {
//            return "Receiver for connection " + id + " is already stopped";
//        } else if (isRec && receiver.isBound()) {
//            try {
//                receiver.shutdown();
//                return "Receiver for connection " + id + " is stopping. It could take few moments";
//            } catch (Exception e) {
//                return "Failed to stop receiver for connection: " + id + ", with error: " + e.getMessage();
//            }
//        }
//
//        return "";
//    }
//
//
//    @Path("/connections/reset/all")
//    @Produces({"application/json"})
//    @GET
//    public String smppResetAll() {
//        for (SMPPConnectionConf s : SMPPSimulator.get().getConnections().values()) {
//            for (SMPPConnection c : s.getAllConnections()) {
//                try {
//                    if (c != null) {
//                        c.disconnect();
//                        c.connect();
//                    }
//                } catch (Exception ignore) {
//                }
//            }
//        }
//        return "Reset executed. It could take some time to accomplish the action";
//    }
}
