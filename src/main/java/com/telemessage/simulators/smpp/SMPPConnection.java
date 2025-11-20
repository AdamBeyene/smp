package com.telemessage.simulators.smpp;

import com.logica.smpp.Data;
import com.logica.smpp.pdu.*;
import com.logica.smpp.util.ByteBuffer;
import com.telemessage.simulators.TM_QA_SMPP_SIMULATOR_Application;
import com.telemessage.simulators.common.Utils;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessageUtils;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

import static com.telemessage.simulators.smpp.SMPPConnectionMonitor.DEF_ENQUIRE_LINK_SESSION_LOCK_TIME;

/**
 * Class represents SMPPConnection. It contains basic configuration of SMPP connection and default implementation
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "cloudhopper",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true
)
@org.springframework.context.annotation.Scope("prototype") // ensure each subclass gets its own dependencies injected
public abstract class SMPPConnection implements DispatcherMonitorListener, SMPPConnManagerListener {

    public static final String DR  = "id:%s sub:000 dlvrd:000 submit date:%d done date:0911220855 stat:%s err:000 text:";
    public static final String DR2 = "id:%s sub:000 dlvrd:000 submit date:%d done date:0911220855 stat:%s err:000 text:";

    private static final long DEF_TIMEOUT = 100000;

    public static final int ASCII_CONCAT_LENGTH = 153;
    public static final int MAX_ASCII_CONCAT_LENGTH = 160;
    public static final int UNICODE_CONCAT_LENGTH = 67;
    public static final int MAX_UNICODE_CONCAT_LENGTH = 70;

    public enum BindType {
        ESME, SMSC;
    }

    public enum BindOption {
        receiver, transceiver, transmitter
    }

    @Getter
    @Setter
    protected int id;
    @Getter
    @Setter
    @Attribute protected BindType bindType;
    @Getter
    @Setter
    @Element(required = false) protected String host;
    @Getter
    @Setter
    @Element protected int port;
    @Getter
    @Setter
    @Element(required = false) protected long timeout = DEF_TIMEOUT;
    @Getter
    @Setter
    @Element(required = false) protected byte srcTON = Data.DFLT_GSM_TON;
    @Getter
    @Setter
    @Element(required = false) protected byte srcNPI = Data.DFLT_GSM_NPI;
    @Getter
    @Setter
    @Element(required = false) protected byte dstTON = Data.DFLT_GSM_TON;
    @Getter
    @Setter
    @Element(required = false) protected byte dstNPI = Data.DFLT_GSM_NPI;
    @Getter
    @Setter
    @Element(required = false) protected byte clbTON = Data.DFLT_GSM_TON;
    @Getter
    @Setter
    @Element(required = false) protected byte clbNPI = Data.DFLT_GSM_NPI;
    @Getter
    @Setter
    @Element(required = false) protected byte dataCoding = Data.DFLT_DATA_CODING;
    @Getter
    @Setter
    @Element protected String systemId;
    @Getter
    @Setter
    @Element protected String password;
    @Getter
    @Setter
    @Element(required = false) protected String systemType; // The system ID for authentication to SMSC: EXT_SME
    @Getter
    @Setter
    @Element(required = false) protected String encoding = "ISO-8859-1";
    @Getter
    @Setter
    @Element protected BindOption bindOption;
    @Getter
    @Setter
    @Element (required = false) protected SMPPRequest.ConcatenationType concatenation = SMPPRequest.ConcatenationType.NA;
    @Setter
    @Getter
    @Element (required = false) protected int threads = 10;
    protected SMPPConnManager connManager;
    protected SMPPConnectionMonitor monitor;
    protected ExecutorService service = null;

    protected MessagesCache messagesCache;

    // Ensure messagesCache is set after construction, even for subclasses not managed by Spring
    @Autowired(required = true)
    public void setMessagesCache(MessagesCache messagesCache) {
        this.messagesCache = messagesCache;
    }

    protected EnvConfiguration conf;

    public SMPPConnection() {}

    protected void setConf(EnvConfiguration conf) {
        this.conf = conf;
    }

    public boolean isReference() { return false; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SMPPConnection)) return false;
        SMPPConnection that = (SMPPConnection) o;
        return id == that.id &&
                port == that.port &&
                timeout == that.timeout &&
                srcTON == that.srcTON &&
                srcNPI == that.srcNPI &&
                dstTON == that.dstTON &&
                dstNPI == that.dstNPI &&
                clbTON == that.clbTON &&
                clbNPI == that.clbNPI &&
                dataCoding == that.dataCoding &&
                threads == that.threads &&
                bindType == that.bindType &&
                Objects.equals(host, that.host) &&
                Objects.equals(systemId, that.systemId) &&
                Objects.equals(password, that.password) &&
                Objects.equals(systemType, that.systemType) &&
                Objects.equals(encoding, that.encoding) &&
                bindOption == that.bindOption &&
                concatenation == that.concatenation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, bindType, host, port, timeout, srcTON, srcNPI, dstTON, dstNPI, clbTON, clbNPI, dataCoding, systemId, password, systemType, encoding, bindOption, concatenation, threads);
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("SMPPConnection{");
        sb.append("bindOption=").append(bindOption);
        sb.append(", id=").append(id);
        sb.append(", bindType=").append(bindType);
        sb.append(", host='").append(host).append('\'');
        sb.append(", port=").append(port);
        sb.append(", timeout=").append(timeout);
        sb.append(", srcTON=").append(srcTON);
        sb.append(", srcNPI=").append(srcNPI);
        sb.append(", dstTON=").append(dstTON);
        sb.append(", dstNPI=").append(dstNPI);
        sb.append(", clbTON=").append(clbTON);
        sb.append(", clbNPI=").append(clbNPI);
        sb.append(", dataCoding=").append(dataCoding);
        sb.append(", systemId='").append(systemId).append('\'');
        sb.append(", password='").append(password).append('\'');
        sb.append(", systemType='").append(systemType).append('\'');
        sb.append(", encoding='").append(encoding).append('\'');
        sb.append(", concatenation=").append(concatenation);
        sb.append(", threads=").append(threads);
        sb.append('}');
        return sb.toString();
    }

    public boolean isBound() { return connManager != null && connManager.isBound(); }

    public boolean isDead() { return connManager == null; }

    public void disconnect() {
        log.info("Disconnect: " + this.toString());
        boolean sendUnbind = false;
        if (isBound())
            sendUnbind = true;
        if (monitor != null)
            monitor.pause();
        connManager.closeConnection(sendUnbind);
    }

    @Async
    public void disconnectAsync() {
        log.info("Disconnect: " + this.toString());
        boolean sendUnbind = false;
        if (isBound())
            sendUnbind = true;
        if (monitor != null)
            monitor.pause();
        connManager.closeConnection(sendUnbind);
    }

    public void shutdown() {
        log.info("Close: " + this.toString());
        if (monitor != null)
            monitor.shutDownMonitor();
        connManager.shutDown();
    }

    @Async
    public CompletableFuture<String> shutdownAsync() {
        log.info("@Async lose: " + this.toString());
        if (monitor != null)
            monitor.shutDownMonitor();
        connManager.shutDown();
        return CompletableFuture.completedFuture("shutdown connection");
    }

    public void connect() throws AlreadyBoundException {
        log.info("Attempting to connect: {}", this.toString());
        if (isBound())
            throw new AlreadyBoundException("Already Bound " + this.toString());
        synchronized (this) {
            if (connManager == null) {
                connManager = ConnectionManagerFactory.get(this.bindType);
                connManager.addListener(this);
                log.info("Created new connManager for {}: {}", this.getName(), connManager.getLogName());
            } else {
                log.info("Reusing existing connManager for {}: {}", this.getName(), connManager.getLogName());
            }
        }
        try {
            connManager.startConnection(this);
            log.info("Connection started: {}", this.toString());
        } catch (AlreadyBoundException e) {
            log.warn("Already bound: {}", this.toString());
            throw e;
        } catch (Exception e) {
            log.error("Failed to start connection: {}", this.toString(), e);
            throw new RuntimeException("Failed to start connection: " + this.toString(), e);
        }
    }

    @Async
    public CompletableFuture<String> connectAsync() {
        log.info("Attempting to connectAsync: {}", this.toString());
        try {
            if (isBound())
                throw new AlreadyBoundException("Already Bound " + this.toString());
            synchronized (this) {
                if (connManager == null) {
                    connManager = ConnectionManagerFactory.get(this.bindType);
                    connManager.addListener(this);
                    log.info("Created new connManager for {}: {}", this.getName(), connManager.getLogName());
                } else {
                    log.info("Reusing existing connManager for {}: {}", this.getName(), connManager.getLogName());
                }
            }
            connManager.startConnection(this);
            log.info("Connection started (async): {}", this.toString());
            return CompletableFuture.completedFuture("starting connection");
        } catch (AlreadyBoundException e) {
            log.warn("Already bound: {}", this.toString());
            return CompletableFuture.completedFuture("Already bound");
        } catch (Exception e) {
            log.error("Failed to connectAsync: {}", this.toString(), e);
            return CompletableFuture.completedFuture("Failed to connect: " + e.getMessage());
        }
    }

    public void start() {
        log.info("Starting: {}", this.toString());
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(TM_QA_SMPP_SIMULATOR_Application.QUEUE_SIZE);
        service = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, queue);
        initConnection();
    }

    public void initConnection() {
        final SMPPConnection me = this;
        new Thread(() -> {
            try {
                connect();
            } catch (AlreadyBoundException e) {
                log.debug("Already bound: {}", me.toString());
            } catch (Exception e) {
                log.error("initConnection: Failed to connect: {}", me.toString(), e);
            }
        }, "SMPPConnection-init-" + this.getId()).start();
    }

    @Override
    public void onMonitorFailure() {
        onClose(false);
        this.initConnection();
    }

    @Override
    public void onStart(boolean success) {
        if (success) {
            if (monitor == null) {
                monitor = new SMPPConnectionMonitor("Monitor - " + this.getName(), connManager, DEF_ENQUIRE_LINK_SESSION_LOCK_TIME, 0, this);
                new Thread(monitor).start();
            }
            monitor.wakeup();
        }
    }

    @Override
    public void onClose(boolean success) {
        if (monitor != null)
            monitor.pause();
    }

    // public abstract void receive(Request request, MessagesCache messagesCacheService) throws IOException;

    public abstract void receive(Request request) throws IOException;


    public abstract String getName();


    public Response checkDR(SendMessageSM smppMessage) throws IOException {
        monitor.setLastMessage(System.currentTimeMillis());
        ByteBuffer messagePayloadAsBuffer = null;
        byte[] tempBuffer = null;
        String message = smppMessage.getShortMessage();
        if (StringUtils.isEmpty(message) && smppMessage.hasMessagePayload()) {
            try {
                messagePayloadAsBuffer = smppMessage.getMessagePayload();
            } catch (ValueNotSetException ex) {
                ex.printStackTrace();
            }
            tempBuffer = messagePayloadAsBuffer.getBuffer();
            message = new String(tempBuffer);
        }
        Response response = smppMessage.getResponse();
        SendMessageResponse responseSM = smppMessage.getResponse();
        if (!StringUtils.defaultString(message).toLowerCase().startsWith("id:")) {
            response.setCommandStatus(Data.ESME_RSYSERR);
        }
        log.info("CheckDR for mid: {}, sm: {}", responseSM.getMessageId(), responseSM.debugString());
        return response;
    }


    public Response checkDR(SendMessageSM smppMessage, MessagesCache messagesCacheService) throws IOException {
        monitor.setLastMessage(System.currentTimeMillis());

        // Extract message content preserving original format
        ByteBuffer messagePayloadAsBuffer = null;
        byte[] tempBuffer = null;
        String message = null;

        try {
            message = smppMessage.getShortMessage();
        } catch (Exception e) {
            log.debug("Error getting short message", e);
        }

        if (StringUtils.isEmpty(message) && smppMessage.hasMessagePayload()) {
            try {
                messagePayloadAsBuffer = smppMessage.getMessagePayload();
                tempBuffer = messagePayloadAsBuffer.getBuffer();
                message = new String(tempBuffer, encoding);
            } catch (Exception ex) {
                log.error("Error processing message payload", ex);
            }
        }

        Response response = smppMessage.getResponse();
        SendMessageResponse responseSM = smppMessage.getResponse();
        if (!StringUtils.defaultString(message).toLowerCase().startsWith("id:")) {
            response.setCommandStatus(Data.ESME_RSYSERR);
        }

        String msgId = smppMessage.getResponse().getMessageId();
        try {
            MessagesObject cachedMessage = messagesCacheService.getMessageByID(msgId);

            // Capture raw message bytes
            byte[] rawBytes = null;
            if (tempBuffer != null) {
                rawBytes = tempBuffer;
            } else if (message != null) {
                rawBytes = message.getBytes(encoding);
            }

            if (StringUtils.isNotEmpty(msgId) && cachedMessage != null) {
                log.info("found cachedMessage record, Updating for mid: {}, and dr: {}", msgId, responseSM.debugString());
                cachedMessage.setDirectResponse(cachedMessage.getDirectResponse() + "\n\n" + response.debugString());
                cachedMessage.setDeliveryReceiptShortMessage(smppMessage.debugString());
                cachedMessage.setDeliveryReceiptTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()));

                // Only set raw bytes if we have them and they don't exist already
                if (rawBytes != null && cachedMessage.getRawMessageBytes() == null) {
                    cachedMessage.setRawMessageBytes(rawBytes);
                }

                // Ensure messageTime is set
                String time = MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis());
                if (StringUtils.isEmpty(cachedMessage.getMessageTime())){
                    cachedMessage.setMessageTime(time);
                }
                // Ensure messageTime is set
                if (StringUtils.isEmpty(cachedMessage.getDeliveryReceiptTime())){
                    cachedMessage.setDeliveryReceiptTime(time);
                }

            } else {
                log.info("Not found cachedMessage record, Adding new for mid: {} and dr: {}", msgId, responseSM.debugString());
                cachedMessage = MessagesObject.builder()
                        .id(msgId)
                        .directResponse(response.debugString())
                        .deliveryReceiptShortMessage(smppMessage.debugString())
                        .deliveryReceiptTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                        .rawMessageBytes(rawBytes)
                        .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                        .providerId(monitor.connManager.getProviderId()).build();
            }

            messagesCacheService.addCacheRecord(msgId, cachedMessage);
        } catch (Exception e) {
            log.error("Error caching smpp message record", e);
        }
        log.info("CheckDR for mid: {}, sm: {}", responseSM.getMessageId(), responseSM.debugString());
        return response;
    }


    public SendMessageSM createMessage() {
        return bindType == BindType.SMSC  ? new DeliverSM() : new SubmitSM();
    }

    /**
     * There are two situations where we need to use lengthForNonIsoLatinEncoding:
     * 1. we need to convert to unicode
     * 2. we got non-iso-latin text that can't be displayed in iso-latin
     */
    public int getMaxMessageSize(boolean convertToUnicodeNeeded, boolean canBeDisplayedInIsoLatin) {
        return convertToUnicodeNeeded ||
                ("UTF-8".equalsIgnoreCase(this.encoding) || "ISO-8859-8".equalsIgnoreCase(this.encoding))
                        && !canBeDisplayedInIsoLatin ? UNICODE_CONCAT_LENGTH : ASCII_CONCAT_LENGTH;
    }

    protected List<String> splitMessages(SMPPRequest req) throws UnsupportedEncodingException {
        if (this.concatenation == null)
            this.concatenation = SMPPRequest.ConcatenationType.NA;
        log.debug("start split message");
        String message = req.getText();
        boolean isConvertToUnicode = isConvertToUnicode(message, this.encoding);
        log.debug("isConvertToUnicode with encoding {} {}", this.encoding,isConvertToUnicode);
        if (this.concatenation == SMPPRequest.ConcatenationType.PAYLOAD || this.concatenation == SMPPRequest.ConcatenationType.PAYLOAD_MESSAGE
                ||message.length() <= (isConvertToUnicode ? MAX_UNICODE_CONCAT_LENGTH : MAX_ASCII_CONCAT_LENGTH)) {
            log.debug("PAYLOAD/PAYLOAD_MESSAGE {}" ,message);
            return Collections.singletonList(message);
        }
        List<String> messages = null;
        int length = isConvertToUnicode ? UNICODE_CONCAT_LENGTH : ASCII_CONCAT_LENGTH;
        switch (this.concatenation) {
            case UDHI_PAYLOAD:
            case UDHI:
            case SAR:
                messages = Utils.splitByLength(message, length);
                log.debug("split UDHI_PAYLOAD/UDHI/SAR {}" ,messages);
                break;
            default:
                try {
                    messages = Utils.split(message, length);
                } catch (Exception ignored) {}
                log.debug("split {}" ,messages);
                break;
        }
        return messages;
    }


    public static boolean isConvertToUnicode(String message, String encoding) throws UnsupportedEncodingException {
        // first check if we can display it in iso-latin
        if (Utils.canBeDisplayedInEnc(message, "ISO-8859-1"))
            return false;
        // then check if we can display it in the dispatcher's encoding
        return !Utils.canBeDisplayedInEnc(message, encoding);
    }

}
