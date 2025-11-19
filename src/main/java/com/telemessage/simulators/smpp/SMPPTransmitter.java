package com.telemessage.simulators.smpp;

import com.logica.smpp.Data;
import com.logica.smpp.pdu.*;
import com.logica.smpp.pdu.tlv.TLVInt;
import com.logica.smpp.pdu.tlv.TLVString;
import com.logica.smpp.pdu.tlv.WrongLengthException;
import com.logica.smpp.util.ByteBuffer;
import com.telemessage.simulators.common.Utils;
import com.telemessage.simulators.common.conf.CombinedCharsetProvider;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessageUtils;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.qatools.error.ErrorTracker;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.freeutils.charset.CharsetProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.*;

import static com.telemessage.simulators.smpp.SimUtils.*;

/**
 * This is transmitter smpp connection
 * It could receive only DRs
 */
@Slf4j
@NoArgsConstructor
@Component
public class SMPPTransmitter extends SMPPConnection {

    EnvConfiguration conf;
    static SMPPSimulator smppSim;
    ErrorTracker errorTracker;

    @Autowired
    public SMPPTransmitter(EnvConfiguration conf,
                           SMPPSimulator smppSim,
                           ErrorTracker errorTracker) {
        super.setConf(conf);
        this.conf = conf;
        this.smppSim = smppSim;
        this.errorTracker = errorTracker;
    }

    @Override
    public void receive(Request request) throws IOException {
        int commandID = request.getCommandId();
        Response response = null;
        switch (commandID) {
            case Data.DELIVER_SM:
            case Data.SUBMIT_SM:
                // DRs are cached via checkDR
                response = checkDR((SendMessageSM) request, messagesCache);
                break;

            case Data.UNBIND:
                response = request.getResponse();
                break;

            case Data.BIND_TRANSMITTER:
            case Data.BIND_TRANSCEIVER:
                response = request.getResponse();
                response.setCommandId(Data.ESME_RALYBND);
                if (!isBound()) {
                    throw new IOException("Receive bind command, not in startConnection code, while connection is Unbound " + this.getId() + " " + this.getClass().getName());
                }
                break;

            case Data.ENQUIRE_LINK:
                response = request.getResponse();
                if (isBound()) {
                    if (monitor != null)
                        monitor.setLastAckedEnquireLinkTime(System.currentTimeMillis());
                    break;
                }

            default:
                response = new GenericNack(Data.ESME_RINVCMDID, request.getSequenceNumber());
        }
        connManager.respond(response);
        if (commandID == Data.UNBIND) {
            connManager.closeConnection(false);
            initConnection();
        }
    }

    @Override
    public void handleResponse(Response response, SMPPRequestManager requestManager) {
        try {
            final Integer id = requestManager.get(Integer.class, response.getSequenceNumber());
            if (id == null) {
                if (response instanceof EnquireLinkResp) {
                } else if (response instanceof BindResponse || response instanceof SubmitSMResp || response instanceof DeliverSMResp) {
                    log.debug("Wait reached timeout " + response.debugString());
                } else {
                    log.debug("Unexpected event (Unknown response type)" + response.debugString());
                }
            } else {
                requestManager.putAndNotify(id, response);
            }
        } catch (Exception e) {
            log.error("", e);
            errorTracker.captureError(
                "SMPPTransmitter.receive",
                e,
                "receive-failed",
                Map.of(
                    "operation", "receive"
                )
            );
        }
    }

    @Override
    public String getName() {
        return smppSim.getName() + "-" + bindType.name() + "-Transmitter";
    }

    public void send(SendMessageSM msg) {
        final SMPPTransmitter me = this;
        this.service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignore) {}

                    Response resp = connManager.send(msg);
                    String msgId = ((SendMessageResponse) resp).getMessageId();
                    String mid = StringUtils.isEmpty(msgId) ? String.valueOf(new Date().getTime()) : msgId;
                    try {
                        byte[] rawBytes = null;
                        try {
                            if (msg.getShortMessage()!=null) {
                                rawBytes = msg.getShortMessage().getBytes(encoding);
                            } else if (msg.hasMessagePayload()) {
                                rawBytes = msg.getMessagePayload().getBuffer();
                            }
                        } catch (Exception e) {
                            log.debug("Could not capture raw message bytes", e);
                        }

                        MessagesObject chacheMessage = MessagesObject.builder()
                                .dir("OUT_PART")
                                .id(mid)
                                .text(SimUtils.getMessageTextForCaching(msg, me))
                                .from(msg.getSourceAddr().getAddress())
                                .to(msg.getDestAddr().getAddress())
                                .sendMessageSM(msg.debugString())
                                .directResponse(resp.debugString())
                                .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                                .messageEncoding(encoding)
                                .rawMessageBytes(rawBytes)
                                .providerId(StringUtils.isNotEmpty(connManager.getProviderId()) ?
                                        connManager.getProviderId() : String.valueOf(connManager.getPort())).build();

                        // [Critical] Log the cached text for verification
                        log.debug("Caching message text for mid {}: [{}]", mid, chacheMessage.getText());

                        MessagesCache cache = smppSim.getMessagesCacheService();
                        if (cache != null) {
                            boolean ok = cache.addCacheRecord(mid, chacheMessage);
                            if (!ok) {
                                log.error("Failed to add outgoing message to cache for id {}", msgId);
                                errorTracker.captureError(
                                    "SMPPTransmitter.send",
                                    new RuntimeException("Failed to add outgoing message to cache"),
                                    "cache-outgoing-message-failed",
                                    Map.of(
                                        "operation", "cache_outgoing_message",
                                        "messageId", String.valueOf(msgId)
                                    )
                                );
                            }
                        } else {
                            log.error("messagesCache is null! Cannot cache outgoing message id {}", mid);
                            errorTracker.captureError(
                                "SMPPTransmitter.send",
                                new NullPointerException("messagesCache is null"),
                                "messages-cache-null",
                                Map.of(
                                    "operation", "cache_outgoing_message",
                                    "messageId", String.valueOf(mid)
                                )
                            );
                        }
                    } catch (Exception e) {
                        log.error("Error caching smpp message record", e);
                        errorTracker.captureError(
                            "SMPPTransmitter.send",
                            e,
                            "cache-message-record-failed",
                            Map.of(
                                "operation", "cache_message_record"
                            )
                        );
                    }
                } catch (Exception e) {
                    log.error("", e);
                    errorTracker.captureError(
                        "SMPPTransmitter.send",
                        e,
                        "send-failed",
                        Map.of(
                            "operation", "send"
                        )
                    );
                }
            }
        });
    }

    public void sendOld(final SendMessageSM msg) {
        final SMPPTransmitter me = this;
        this.service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    try {
                        Thread.sleep(250);
                    } catch (InterruptedException ignore) {}
                    Response resp = connManager.send(msg);
                    String msgId = ((SendMessageResponse) resp).getMessageId();
                    String mid = StringUtils.isEmpty(msgId) ? String.valueOf(new Date().getTime()) : msgId;
                    try {
                        byte[] rawBytes = null;
                        try {
                            if (msg.getShortMessage()!=null) {
                                rawBytes = msg.getShortMessage().getBytes(encoding);
                            } else if (msg.hasMessagePayload()) {
                                rawBytes = msg.getMessagePayload().getBuffer();
                            }
                        } catch (Exception e) {
                            log.debug("Could not capture raw message bytes", e);
                        }

                        MessagesObject chacheMessage = MessagesObject.builder()
                                .dir("OUT_old")
                                .id(mid)
                                .text(SimUtils.getMessageTextForCaching(msg, me))
                                .from(msg.getSourceAddr().getAddress())
                                .to(msg.getDestAddr().getAddress())
                                .sendMessageSM(msg.debugString())
                                .directResponse(resp.debugString())
                                .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                                .messageEncoding(encoding)
                                .rawMessageBytes(rawBytes)
                                .providerId(StringUtils.isNotEmpty(connManager.getProviderId()) ?
                                        connManager.getProviderId() : String.valueOf(connManager.getPort())).build();
                        if (smppSim.getMessagesCacheService() != null) {
                            boolean ok = smppSim.getMessagesCacheService().addCacheRecord(mid, chacheMessage);
                            if (!ok) {
                                log.error("Failed to add outgoing message to cache for id {}", msgId);
                                errorTracker.captureError(
                                    "SMPPTransmitter.sendOld",
                                    new RuntimeException("Failed to add outgoing message to cache"),
                                    "cache-outgoing-old-message-failed",
                                    Map.of(
                                        "operation", "cache_outgoing_old_message",
                                        "messageId", String.valueOf(msgId)
                                    )
                                );
                            }
                        } else {
                            log.error("messagesCache is null! Cannot cache outgoing message id {}", mid);
                            errorTracker.captureError(
                                "SMPPTransmitter.sendOld",
                                new NullPointerException("messagesCache is null"),
                                "messages-cache-null-old",
                                Map.of(
                                    "operation", "cache_outgoing_old_message",
                                    "messageId", String.valueOf(mid)
                                )
                            );
                        }
                    } catch (Exception e) {
                        log.error("Error caching smpp message record", e);
                        errorTracker.captureError(
                            "SMPPTransmitter.sendOld",
                            e,
                            "cache-old-message-record-failed",
                            Map.of(
                                "operation", "cache_old_message_record"
                            )
                        );
                    }
                } catch (Exception e) {
                    log.error("", e);
                    errorTracker.captureError(
                        "SMPPTransmitter.sendOld",
                        e,
                        "send-old-failed",
                        Map.of(
                            "operation", "send_old"
                        )
                    );
                }
            }
        });
    }

    public List<SendMessageSM> prepareMessage(SMPPRequest req, boolean sendAllPartsOfConcatenateMessage) throws UnsupportedEncodingException, WrongLengthOfStringException, IntegerOutOfRangeException, WrongDateFormatException {
        List<String> texts = splitMessages(req);

        List<SendMessageSM> messages = new ArrayList<>();
        byte refNum = (byte) Utils.random.nextInt(256);
        boolean isConvertToUnicode = isConvertToUnicode(req.getText(), this.encoding);
        int splitLength = isConvertToUnicode ? MAX_UNICODE_CONCAT_LENGTH : MAX_ASCII_CONCAT_LENGTH;

        String enc = this.encoding;
        byte dataCoding = this.dataCoding;
        Pair<Byte, String> dataCodingAndEnc = SimUtils.prepareDataCodingAndEnc(isConvertToUnicode, enc, dataCoding, req.getText());
        dataCoding = dataCodingAndEnc.getLeft();
        enc = dataCodingAndEnc.getRight();

        byte srcTON;
        byte srcNPI = this.srcNPI;
        srcTON = determineTONByAddressSuffix(req.src)!=-1? determineTONByAddressSuffix(req.src):this.srcTON;

        byte dstTON;
        byte dstNPI = this.dstNPI;
        dstTON = determineTONByAddressSuffix(req.dst)!=-1? determineTONByAddressSuffix(req.dst):this.dstTON;

        String src = StringUtils.remove(req.getSrc(), "+- \t");
        if (!"".equals(StringUtils.defaultString(src).replaceAll("[0-9\\-\\+\\s]", "").trim())) {
            srcTON = com.logica.smpp.Data.GSM_TON_ALPHANUMERIC;
            srcNPI = com.logica.smpp.Data.GSM_NPI_UNKNOWN;
        }

        ByteBuffer callback = null;
        if (!StringUtils.isEmpty(req.getCallback())) {
            callback = new ByteBuffer();
            callback.appendByte((byte) 1);
            callback.appendByte(clbTON);
            callback.appendByte(clbNPI);
            callback.appendString(req.getCallback());
        }

        log.debug("prepare() called with params: " + "\n" +
                "srcTON: " + srcTON + "\n" +
                "srcNPI: " + srcNPI + "\n" +
                "src: " + src + "\n" +
                "dstTON: " + dstTON + "\n" +
                "dstNPI: " + dstNPI + "\n" +
                "dst: " + req.getDst() + "\n" +
                "dataCoding: " + dataCoding + "\n" +
                "encoding: " + enc + "\n" +
                "texts: " + texts);

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            SendMessageSM message = createMessage();

            if (callback != null)
                message.setCallbackNum(callback);

            message.setSourceAddr(srcTON, srcNPI, src);
            message.setDestAddr(dstTON, dstNPI, req.getDst());

            if (!StringUtils.isEmpty(req.getServiceType()))
                message.setServiceType(req.getServiceType());

            if (req.getUserMessageRef() != null) {
                message.setUserMessageReference(req.getUserMessageRef());
            }

            if (!StringUtils.isEmpty(req.getDstSubAddress())) {
                message.setDestSubaddress(new ByteBuffer(req.getDstSubAddress().getBytes()));
            }

            if (!StringUtils.isEmpty(req.getSrcSubAddress())) {
                message.setSourceSubaddress(new ByteBuffer(req.getSrcSubAddress().getBytes()));
            }

            Short owner_tag = 0x1926;
            Short extMessageId_tag = 0x1927;
            Short messageTime_tag = 0x1928;
            if (req.getParams() != null && req.getParams().size() > 0 ) {
                req.getParams().forEach(m -> {
                    if ("owner".equals(m.get("tag"))){
                        Integer owner_value = Integer.valueOf(m.get("value"));
                        TLVInt tlv = new TLVInt();
                        tlv.setTag(owner_tag);
                        tlv.setValue(owner_value);
                        message.setExtraOptional(tlv);
                        log.info("owner_tag: " + owner_tag);
                        log.info("val: " + owner_value);
                    }
                    if ("messageid".equals(m.get("tag"))){
                        String extMessageId_value = m.get("value");
                        TLVString tlv = new TLVString();
                        tlv.setTag(extMessageId_tag);
                        try {
                            tlv.setValue(extMessageId_value);
                        } catch (WrongLengthException e) {
                            e.printStackTrace();
                        }
                        message.setExtraOptional(tlv);
                        log.info("extMessageId_tag: " + extMessageId_tag);
                        log.info("val: " + extMessageId_value);
                    }
                    if ("messagetime".equals(m.get("tag"))){
                        String messageTime_value = m.get("value");
                        TLVString tlv = new TLVString();
                        tlv.setTag(messageTime_tag);
                        try {
                            tlv.setValue(messageTime_value);
                        } catch (WrongLengthException e) {
                            e.printStackTrace();
                        }
                        message.setExtraOptional(tlv);
                        log.info("messageTime_tag: " + messageTime_tag);
                        log.info("val: " + messageTime_value);
                    }
                });

            }

            if (!StringUtils.isEmpty(req.getScheduleDeliveryTime())) {
                String scheduleDeliveryTime = req.getScheduleDeliveryTime();
                if(!scheduleDeliveryTime.endsWith("+"))
                    scheduleDeliveryTime += "+";
                message.setScheduleDeliveryTime(scheduleDeliveryTime);
            }

            if (req.getMessageState() != null && req.getMessageState().getValue() > -1) {
                message.setMessageState(req.getMessageState().getValue());
            }

            message.setDataCoding(dataCoding);
            switch (this.concatenation) {
                case UDHI_PAYLOAD:
                    if (texts.size() > 1) {
                        ByteBuffer udh = createUDH(refNum,texts.size(), i+1,  text,  enc);
                        message.setEsmClass((byte) (Data.SM_UDH_GSM | Data.SM_STORE_FORWARD_MODE));
                        message.setMessagePayload(udh);
                        log.debug("UDHI_PAYLOAD: " +"\n"+
                                message.debugString() + "\n"+
                                "val:" + text +"\n"+
                                "enc:" + enc +"\n"+
                                "dataCoding:" + dataCoding+"\n"+
                                "all_texts size:" + (byte) texts.size() +"\n"+
                                "Sequence number:" +(byte) (i+1) +"\n"+
                                "refNum:" + refNum);
                    } else {
                        message.setShortMessage(text, enc);
                        log.debug("default: " +"\n"+
                                message.debugString() + "\n"+
                                "val:" + text +"\n"+
                                "enc:" + enc+"\n"+
                                "dataCoding:" + dataCoding);
                    }
                    break;
                case PAYLOAD_MESSAGE:
                    message.setMessagePayload(new ByteBuffer(StringUtils.defaultString(text).getBytes(enc)));

                    log.debug("PAYLOAD_MESSAGE: " +"\n"+
                            message.debugString() + "\n"+
                            "val:" + text +"\n"+
                            "enc:" + enc+"\n"+
                            "dataCoding:" + dataCoding);
                    break;
                case PAYLOAD:
                    if (StringUtils.defaultString(text).length() > splitLength) {
                        message.setMessagePayload(new ByteBuffer(StringUtils.defaultString(text).getBytes(enc)));

                        log.debug("PAYLOAD: " +"\n"+
                                message.debugString() + "\n"+
                                "val:" + text +"\n"+
                                "enc:" + enc+"\n"+
                                "dataCoding:" + dataCoding);
                    }else {
                        message.setShortMessage(new String(text.getBytes(Charset.forName(enc))), enc);
                        log.debug("default: " + "\n" +
                                message.debugString() + "\n" +
                                "val:" + text + "\n" +
                                "enc:" + enc + "\n" +
                                "dataCoding:" + dataCoding);
                    }
                    break;
                case UDHI:
                    if (texts.size() > 1) {
                        ByteBuffer udh = createUDH(refNum,texts.size(), i+1,  text,  enc);
                        String encodedText = SimUtils.createString(udh, enc);
                        message.setEsmClass((byte) (Data.SM_UDH_GSM | Data.SM_STORE_FORWARD_MODE));

                        message.setShortMessage(encodedText, enc);

                        log.debug("UDHI: " +"\n"+
                                message.debugString() + "\n"+
                                "val:" + text +"\n"+
                                "enc:" + enc +"\n"+
                                "dataCoding:" + dataCoding+"\n"+
                                "all_texts size:" + (byte) texts.size() +"\n"+
                                "Sequence number:" +(byte) (i+1) +"\n"+
                                "refNum:" + refNum);
                    } else {
                        message.setShortMessage(text, enc);
                        log.debug("default: " +"\n"+
                                message.debugString() + "\n"+
                                "val:" + text +"\n"+
                                "enc:" + enc+"\n"+
                                "dataCoding:" + dataCoding);
                    }
                    break;
                case SAR:
                    if (texts.size() > 1) {
                        message.setSarTotalSegments((short) texts.size());
                        message.setSarSegmentSeqnum((short) (i+1));
                        message.setSarMsgRefNum(refNum);
                        message.setShortMessage(text, enc);
                        log.debug("SAR: " +"\n"+
                                message.debugString() + "\n"+
                                "val:" + text +"\n"+
                                "enc:" + enc+"\n"+
                                "dataCoding:" + dataCoding+"\n"+
                                "setSarTotalSegments :" + (byte) texts.size() +"\n"+
                                "setSarSegmentSeqnum :" +(byte) (i+1) +"\n"+
                                "refNum:" + refNum);
                    } else {
                        message.setShortMessage(text, enc);
                        log.debug("default: " +"\n"+
                                message.debugString() + "\n"+
                                "val:" + text +"\n"+
                                "enc:" + enc+"\n"+
                                "dataCoding:" + dataCoding);
                    }
                    break;
                default:
                    message.setShortMessage(text, enc);
                    log.debug("default: " +"\n"+
                            message.debugString() + "\n"+
                            "val:" + text +"\n"+
                            "enc:" + enc+"\n"+
                            "dataCoding:" + dataCoding);
                    break;
            }
            message.assignSequenceNumber(true);
            if(shouldAddMessage(sendAllPartsOfConcatenateMessage, texts.size(), i + 1, messages.size()))
                messages.add(message);
        }
        log.debug("message prepare() done");
        return messages;
    }
    private static final Random RANDOM = new Random();

    private boolean shouldAddMessage(boolean sendAllPartsOfConcatenateMessage, int totalPartsNumber, int partNumber, int numberOfMessages) {
        return sendAllPartsOfConcatenateMessage ||
                isRandomConditionSatisfied() ||
                isSecondToLastPartSend(totalPartsNumber, numberOfMessages) ||
                isFirstPartToSend(totalPartsNumber, partNumber, numberOfMessages);
    }

    private boolean isRandomConditionSatisfied() {
        return RANDOM.nextInt(4) == 1;
    }

    private boolean isSecondToLastPartSend(int totalPartsNumber, int numberOfMessages) {
        return numberOfMessages == totalPartsNumber - 1;
    }

    private boolean isFirstPartToSend(int totalPartsNumber, int partNumber, int numberOfMessages) {
        return numberOfMessages == 0 && totalPartsNumber == partNumber;
    }

    private ByteBuffer createUDH(byte refNum, int totalParts, int sequenceNumber, String text, String encoding) {
        ByteBuffer udh = new ByteBuffer();
        udh.appendByte((byte) 5);
        udh.appendByte((byte) 0x00);
        udh.appendByte((byte) 3);
        udh.appendByte(refNum);
        udh.appendByte((byte) totalParts);
        udh.appendByte((byte) sequenceNumber);
        Charset actualCharset;
        if(encoding.equals("SCGSM") || encoding.equals("CCGSM") || encoding.equals("GSM7")){
            CombinedCharsetProvider provider = new CombinedCharsetProvider();
            actualCharset = provider.charsetForName("CCGSM");
        } else {
            actualCharset = Charset.forName(encoding);
        }
        byte[] textBytes = text.getBytes(actualCharset);
        udh.appendBytes(textBytes, textBytes.length);
        return udh;
    }


    private byte determineNPI(String address) {
        return srcNPI;
    }

    @Override
    public void onStart(boolean success) {
        super.onStart(success);
    }

    @Override
    public void handleRequest(final Request request, SMPPRequestManager requestManager) throws IOException {
        final SMPPConnection me = this;
        this.service.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    receive(request);
                } catch (IOException e) {
                    log.error(me.toString(), e);
                    errorTracker.captureError(
                        "SMPPTransmitter.handleRequest",
                        e,
                        "handle-request-failed",
                        Map.of(
                            "operation", "handle_request"
                        )
                    );
                }
            }
        });
    }


}
