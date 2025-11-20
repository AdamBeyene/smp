package com.telemessage.simulators.controllers;


import com.telemessage.simulators.Simulator;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.controllers.utils.Utils;
import com.telemessage.simulators.smpp.SMPPConnection;
import com.telemessage.simulators.smpp.SMPPRequest;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.web.wrappers.AbstractMessage;
import com.telemessage.simulators.web.wrappers.DeliveryReceiptShortMessage;
import com.telemessage.simulators.web.wrappers.SMPPWebConnection;
import com.telemessage.simulators.web.wrappers.ShortMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/sim/smpp")
public class SimSMPP {

    EnvConfiguration conf;
    static Simulator smppSim;
    MessagesCache cacheService;
    @Autowired
    public SimSMPP(EnvConfiguration conf, @Qualifier("smppSimulator") Simulator smppSim, MessagesCache cacheService) {
        this.conf = conf;
        this.smppSim = smppSim;
        this.cacheService = cacheService;
    }

    @RequestMapping(method = RequestMethod.GET, path = "/test",
            produces = MediaType.APPLICATION_JSON_VALUE,
//            consumes = MediaType.APPLICATION_JSON_VALUE,
            name = "test")
    public AbstractMessage[] test() {
        ShortMessage sm = new ShortMessage();
        sm.setText("hhh");
        sm.setSrc("1111");
        sm.setDst("2222");

        DeliveryReceiptShortMessage dr = new DeliveryReceiptShortMessage();
        dr.setProviderResult("454545");
        dr.setStatus(DeliveryReceiptShortMessage.DRs.DELIVRD);
        dr.setSrc("1111");
        dr.setDst("2222");
        return new AbstractMessage[] {
                sm, dr
        };
    }

    @RequestMapping(method = RequestMethod.POST, path = "/connection/{id}/send/message",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE,
             name = "sendMessage")
    public String smppSendMessage(@PathVariable("id") int id, @RequestBody ShortMessage msg) {
        return sendMessage(id, msg.getSrc(), msg.getDst(), msg.getServiceType(), msg.getText(), msg.getClb(), msg.getUserMessageRef(), msg.getSrcSubAddress(), msg.getDstSubAddress(),
                msg.getScheduleDeliveryTime(), msg.getMessageState(), true , msg.getParams(), msg.getPartsDelay());
    }

    @RequestMapping(method = RequestMethod.POST, path = "/connection/{id}/send/message/random-parts-concatenation",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            name = "sendMessage-random-parts")
    public String smppSendPartFromConcatenationMessage(@PathVariable("id") int id,@RequestBody ShortMessage msg) {
        return sendMessage(id, msg.getSrc(), msg.getDst(), msg.getServiceType(), msg.getText(), msg.getClb(), msg.getUserMessageRef(), msg.getSrcSubAddress(), msg.getDstSubAddress(),
                msg.getScheduleDeliveryTime(), msg.getMessageState(), false ,  msg.getParams(), msg.getPartsDelay());
    }

    @RequestMapping(method = RequestMethod.POST, path = "/connection/{id}/send/dr",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE,
            name = "sendDr")
    public String smppSendDR(@PathVariable("id") int id, @RequestBody DeliveryReceiptShortMessage dr) {
        return sendMessage(id, dr.getSrc(), dr.getDst(), dr.getServiceType(), dr.getText(), null, null, null, null, null, ShortMessage.Message_state_enum.NONE, true , null, null);
    }

    protected String sendMessage(int id, String src, String dst, String serviceType, String text, String clb,
                                 Short userMessageRef, String srcSubAddress, String dstSubAddress, String scheduleDeliveryTime, ShortMessage.Message_state_enum messageState, boolean sendAllPartsOfConcatenateMessage , List<Map<String, String>> optional_params,List<Long> partsDelay ) {
        try {
            String data = String.valueOf(new Date().getTime());
            /*MessagesObject chacheMessage = MessagesObject.builder()
                    .dir("Out")
                    .id(data)
                    .text(text)
                    .sendMessageSM(data + " SM " + data)
                    .directResponse(data + " dirResp " + data)
                    .providerId(data + " prov1" + data).build();

            cacheService.addCacheRecord(data, chacheMessage);*/
            boolean success = smppSim.send(id, new SMPPRequest(src, dst, text, serviceType, clb,
                    userMessageRef, srcSubAddress, dstSubAddress, scheduleDeliveryTime, messageState == null ? ShortMessage.Message_state_enum.NONE : messageState , optional_params, partsDelay), sendAllPartsOfConcatenateMessage);
            if (success) {
                return "Message is sending.";
            } else {
                return "Failed to send message";
            }
        } catch (Exception e) {
            log.error("", e);
            return "Failed to send message " + e.getMessage();
        }
    }



    @RequestMapping(method = RequestMethod.GET, path = "/info/connection/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            name = "connInfo")
    public SMPPWebConnection[] getSmppInfoConnection(@PathVariable(required = false, name = "id") Integer id) {
        List<SMPPWebConnection> conns = new ArrayList<>();
        if (id != null && id > 0) {
            SMPPConnectionConf c = smppSim.get(id);
            if (c != null)
                conns.add(new SMPPWebConnection(c));
        } else {

            List<SMPPConnectionConf> cs = new ArrayList<>(smppSim.getConnections().values());
            Collections.sort(cs, new Comparator<SMPPConnectionConf>() {
                @Override
                public int compare(SMPPConnectionConf o1, SMPPConnectionConf o2) {
                    if (o1 == o2) return 0;
                    if (o1 == null) return -1;
                    if (o2 == null) return 1;
                    return o1.getId() - o2.getId();
                }
            });

            for (SMPPConnectionConf c : cs) {
                conns.add(new SMPPWebConnection(c));
            }
        }
        return conns.toArray(new SMPPWebConnection[conns.size()]);
    }

    @RequestMapping(method = RequestMethod.GET, path = {"/info/connection","/info/connection/"},
            produces = MediaType.APPLICATION_JSON_VALUE,
            name = "connInfo")
    public SMPPWebConnection[] getSmppInfoConnection() {
        return Utils.smppInfoConnections();
    }

    @Async
    @RequestMapping(method = RequestMethod.GET, path = "/connection/{id}/stop/{type}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            name = "stopByType")
    public CompletableFuture<String> smppStopConnection(@PathVariable("id") int id, @PathVariable("type") TYPE type) {
        SMPPConnectionConf c = smppSim.get(id);
        if (c == null)
            return CompletableFuture.completedFuture("No connection with such id " + id);
//            return "No connection with such id " + id;
        if (type == null)
            return CompletableFuture.completedFuture("Please, choose receiver or transmitter for connection " + id);
//            return "Please, choose receiver or transmitter for connection " + id;

        boolean isTr = "transmitter".equals(type.name());
            SMPPConnection transmitter = isTr ? c.getTransmitter() : null;
        boolean isRec = "receiver".equals(type.name());
            SMPPConnection receiver = isRec ? c.getReceiver() : null;


        if (isTr && transmitter == null) {
            return CompletableFuture.completedFuture("No transmitter for connection " + id);
//            return "No transmitter for connection " + id;
        }else if (isTr && transmitter.isReference()) {
            return CompletableFuture.completedFuture("Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver());
//            return "Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver();
        } else if (isTr && !transmitter.isBound()) {
            return CompletableFuture.completedFuture("Transmitter for connection " + id + " is already stopped");
//            return "Transmitter for connection " + id + " is already stopped";
        } else if (isTr && transmitter.isBound()) {
            try {
                transmitter.shutdownAsync();
                return CompletableFuture.completedFuture("Transmitter for connection \" + id + \" is stopping. It could take few moments");
//                return "";
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Failed to stop transmitter for connection: " + id + ", with error: " + e.getMessage());
//                return "Failed to stop transmitter for connection: " + id + ", with error: " + e.getMessage();
            }
        }

        if (isRec && receiver == null) {
            return CompletableFuture.completedFuture("No receiver for connection " + id);
//            return "No receiver for connection " + id;
        } else if (isRec && !receiver.isBound()) {
            return CompletableFuture.completedFuture("Receiver for connection " + id + " is already stopped");
//            return "Receiver for connection " + id + " is already stopped";
        } else if (isRec && receiver.isBound()) {
            try {
                receiver.shutdownAsync();
                return CompletableFuture.completedFuture("Receiver for connection " + id + " is stopping. It could take few moments");
//                return "Receiver for connection " + id + " is stopping. It could take few moments";
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Failed to stop receiver for connection: " + id + ", with error: " + e.getMessage());
//                return "Failed to stop receiver for connection: " + id + ", with error: " + e.getMessage();
            }
        }
        return CompletableFuture.completedFuture("");
//        return "";
    }


    @Async
    @RequestMapping(method = RequestMethod.GET, path = "/connection/{id}/start/{type}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            name = "connInfoByType")
    public CompletableFuture<String> smppStartConnection(@PathVariable("id") int id, @PathVariable("type") TYPE type) {
        SMPPConnectionConf c = smppSim.get(id);
        if (c == null)
            return CompletableFuture.completedFuture("No connection with such id " + id);
//            return "No connection with such id " + id;
        if (type == null)
            return CompletableFuture.completedFuture("Please, choose receiver or transmitter for connection " + id);
//            return "Please, choose receiver or transmitter for connection " + id;

        boolean isTr = "transmitter".equals(type.name());
        SMPPConnection transmitter = isTr ? c.getTransmitter() : null;
        boolean isRec = "receiver".equals(type.name());
        SMPPConnection receiver = isRec ? c.getReceiver() : null;

        if (isTr && transmitter == null) {
            return CompletableFuture.completedFuture("No transmitter for connection " + id);
//            return "No transmitter for connection " + id;
        } else if (isTr && transmitter.isReference()) {
            return CompletableFuture.completedFuture("Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver());
//            return "Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver();
        } else if (isTr && transmitter.isBound()) {
            return CompletableFuture.completedFuture("Transmitter for connection " + id + " is already started");
//            return "Transmitter for connection " + id + " is already started";
        } else if (isTr && !transmitter.isBound()) {
            try {
                transmitter.connectAsync();
                return CompletableFuture.completedFuture("Transmitter for connection " + id + " is starting. It could take few moments");
//                return "Transmitter for connection " + id + " is starting. It could take few moments";
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Failed to start transmitter for connection: " + id + ", with error: " + e.getMessage());
//                return "Failed to start transmitter for connection: " + id + ", with error: " + e.getMessage();
            }
        }

        if (isRec && receiver == null) {
            return CompletableFuture.completedFuture("No receiver for connection " + id);
//            return "No receiver for connection " + id;
        } else if (isRec && receiver.isBound()) {
            return CompletableFuture.completedFuture("Receiver for connection " + id + " is already started");
//            return "Receiver for connection " + id + " is already started";
        } else if (isRec && !receiver.isBound()) {
            try {
                receiver.connectAsync();
                return CompletableFuture.completedFuture("Receiver for connection " + id + " is starting. It could take few moments");
//                return "Receiver for connection " + id + " is starting. It could take few moments";
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Failed to start receiver for connection: " + id + ", with error: " + e.getMessage());
//                return "Failed to start receiver for connection: " + id + ", with error: " + e.getMessage();
            }
        }
        return CompletableFuture.completedFuture("");
//        return "";
    }


    @Async
    @RequestMapping(method = RequestMethod.GET, path = "/connection/{id}/reset/{type}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            name = "resetByType")
    public CompletableFuture<String> smppResetConnection(@PathVariable("id") int id, @PathVariable("type") TYPE type) {
        SMPPConnectionConf c = smppSim.get(id);
        if (c == null)
            return CompletableFuture.completedFuture("No connection with such id " + id);
//            return "No connection with such id " + id;
        if (type == null)
            return CompletableFuture.completedFuture("Please, choose receiver or transmitter for connection " + id);
//            return "Please, choose receiver or transmitter for connection " + id;

        boolean isTr = "transmitter".equals(type.name());
        SMPPConnection transmitter = isTr ? c.getTransmitter() : null;
        boolean isRec = "receiver".equals(type.name());
        SMPPConnection receiver = isRec ? c.getReceiver() : null;


        if (isTr && transmitter == null) {
            return CompletableFuture.completedFuture("No transmitter for connection " + id);
//            return "No transmitter for connection " + id;
        }else if (isTr && transmitter.isReference()) {
            return CompletableFuture.completedFuture("Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver());
//            return "Transmitter for connection " + id + " is reference to transmitter of connection " + c.getReceiver();
        } else if (isTr && !transmitter.isBound()) {
            return CompletableFuture.completedFuture("Transmitter for connection " + id + " is already stopped");
//            return "Transmitter for connection " + id + " is already stopped";
        } else if (isTr && transmitter.isBound()) {
            try {
                transmitter.disconnect();
                return CompletableFuture.completedFuture("Transmitter for connection " + id + " is stopping. It could take few moments");
//                return "Transmitter for connection " + id + " is stopping. It could take few moments";
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Failed to stop transmitter for connection: " + id + ", with error: " + e.getMessage());
//                return "Failed to stop transmitter for connection: " + id + ", with error: " + e.getMessage();
            }
        }

        if (isRec && receiver == null) {
            return CompletableFuture.completedFuture("No receiver for connection " + id);
//            return "No receiver for connection " + id;
        } else if (isRec && !receiver.isBound()) {
            return CompletableFuture.completedFuture("Receiver for connection " + id + " is already stopped");
//            return "Receiver for connection " + id + " is already stopped";
        } else if (isRec && receiver.isBound()) {
            try {
                receiver.disconnect();
                return CompletableFuture.completedFuture("Receiver for connection " + id + " is stopping. It could take few moments");
//                return "Receiver for connection " + id + " is stopping. It could take few moments";
            } catch (Exception e) {
                return CompletableFuture.completedFuture("Failed to stop receiver for connection: " + id + ", with error: " + e.getMessage());
//                return "Failed to stop receiver for connection: " + id + ", with error: " + e.getMessage();
            }
        }
        return CompletableFuture.completedFuture("");
//        return "";
    }

    @Async
    @RequestMapping(method = RequestMethod.GET, path = "/connections/reset/all",
            produces = MediaType.APPLICATION_JSON_VALUE,
            name = "resetAll")
    public CompletableFuture<String> smppResetAll() {
        for (SMPPConnectionConf s : smppSim.getConnections().values()) {
            for (SMPPConnection c : s.getAllConnections()) {
                try {
                    if (c != null) {
                        c.disconnectAsync();
                        c.connectAsync();
                    }
                } catch (Exception ignore) {
                }
            }
        }
        return CompletableFuture.completedFuture("Reset executed. It could take some time to accomplish the action");
//        return "Reset executed. It could take some time to accomplish the action";
    }



    private enum TYPE {
        transmitter,receiver
    }
}
