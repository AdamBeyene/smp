package com.telemessage.simulators.smpp;

import com.logica.smpp.pdu.*;
import com.telemessage.qatools.error.ErrorTracker;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.http.HttpSimulator;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "cloudhopper",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true
)
@Data
public class SMPPTransmitterReadonly extends SMPPTransmitter {

    private Object errorTracker;
    EnvConfiguration conf;
    static SMPPSimulator smppSim;
    SMPPTransmitter transmitter;
    int ref = 0;

    @Autowired
    public SMPPTransmitterReadonly(EnvConfiguration conf,
                                   SMPPSimulator smppSim, ErrorTracker errorTracker) {
        super(conf, smppSim, errorTracker);
        this.conf = conf;
        this.smppSim = smppSim; // ensure messagesCache is set
        this.errorTracker = errorTracker;
    }


//    @Override
//    public void send(SendMessageSM msg, MessagesCache mc) {
//        log.warn("Readonly transmitter " + ref + " cannot send messages. It is reference to " + transmitter.getId());
//    }

    @Override
    public void send(SendMessageSM msg) {
        log.warn("Readonly transmitter " + ref + " cannot send messages. It is reference to " + transmitter.getId());
    }

    @Override
    public boolean isReference() {
        return true;
    }

    @Override
    public int getId() {
        return ref;
    }

    @Override
    public String getName() {
        return transmitter.getName() + "-ref";
    }

    @Override
    public void connect() throws AlreadyBoundException {
        log.warn("Readonly transmitter " + ref + " cannot connect. It is reference to " + transmitter.getId());
    }

    @Override
    public void disconnect() {
        log.warn("Readonly transmitter " + ref + " cannot disconnect. It is reference to " + transmitter.getId());
    }

    @Override
    public void shutdown() {
        log.warn("Readonly transmitter " + ref + " cannot shutdown. It is reference to " + transmitter.getId());
    }

    @Override
    public List<SendMessageSM> prepareMessage(SMPPRequest req, boolean sendAllPartsOfConcatenateMessage) throws UnsupportedEncodingException
            , WrongLengthOfStringException, IntegerOutOfRangeException, WrongDateFormatException {
        return transmitter.prepareMessage(req, true);
    }

    @Override
    public String toString() {
        return transmitter.toString();
    }

    @Override
    public void receive(Request request) throws IOException {
        throw new UnsupportedOperationException("defineShortMessage");
    }
/*
    @Override
    public void receive(Request request, MessagesCache messagesCache) throws IOException {
        throw new UnsupportedOperationException("defineShortMessage");
    }*/

    @Override
    public void handleResponse(Response response, SMPPRequestManager requestManager) {
        throw new UnsupportedOperationException("defineShortMessage");
    }

    @Override
    public Response checkDR(SendMessageSM smppMessage) throws IOException {
        throw new UnsupportedOperationException("checkDR");
    }
    @Override
    public Response checkDR(SendMessageSM smppMessage, MessagesCache c) throws IOException {
        throw new UnsupportedOperationException("checkDR");
    }

    @Override
    public SendMessageSM createMessage() {
        throw new UnsupportedOperationException("createMessage");
    }

    @Override
    public BindOption getBindOption() {
        return transmitter.getBindOption();
    }

    @Override
    public BindType getBindType() {
        return transmitter.getBindType();
    }

    @Override
    public byte getClbNPI() {
        return transmitter.getClbNPI();
    }

    @Override
    public byte getClbTON() {
        return transmitter.getClbTON();
    }

    @Override
    public SMPPRequest.ConcatenationType getConcatenation() {
        return transmitter.getConcatenation();
    }

    @Override
    public byte getDataCoding() {
        return transmitter.getDataCoding();
    }

    @Override
    public byte getDstNPI() {
        return transmitter.getDstNPI();
    }

    @Override
    public byte getDstTON() {
        return transmitter.getDstTON();
    }

    @Override
    public String getEncoding() {
        return transmitter.getEncoding();
    }

    @Override
    public String getHost() {
        return transmitter.getHost();
    }

    @Override
    public int getMaxMessageSize(boolean convertToUnicodeNeeded, boolean canBeDisplayedInIsoLatin) {
        throw new UnsupportedOperationException("getMaxMessageSize");
    }

    @Override
    public String getPassword() {
        return transmitter.getPassword();
    }

    @Override
    public int getPort() {
        return transmitter.getPort();
    }

    @Override
    public byte getSrcNPI() {
        return transmitter.getSrcNPI();
    }

    @Override
    public byte getSrcTON() {
        return transmitter.getSrcTON();
    }

    @Override
    public String getSystemId() {
        return transmitter.getSystemId();
    }

    @Override
    public String getSystemType() {
        return transmitter.getSystemType();
    }

    @Override
    public int getThreads() {
        return transmitter.getThreads();
    }

    @Override
    public long getTimeout() {
        return transmitter.getTimeout();
    }

    @Override
    public void handleRequest(Request request, SMPPRequestManager requestManager) throws IOException {
        throw new UnsupportedOperationException("handleRequest");
    }

    @Override
    public int hashCode() {
        return transmitter.hashCode();
    }

    @Override
    public boolean isBound() {
        return transmitter.isBound();
    }

    @Override
    public void onClose(boolean success) {
        throw new UnsupportedOperationException("onClose");
    }

    @Override
    public void onMonitorFailure() {
        // do nothing
    }

    @Override
    public void onStart(boolean success) {
        // do nothing
    }

    @Override
    public void setBindOption(BindOption bindOption) {
        // do nothing
    }

    @Override
    public void setBindType(BindType bindType) {
        // do nothing
    }

    @Override
    public void setClbNPI(byte clbNPI) {
        // do nothing
    }

    @Override
    public void setClbTON(byte clbTON) {
        // do nothing
    }

    @Override
    public void setConcatenation(SMPPRequest.ConcatenationType concatenation) {
        // do nothing
    }

    @Override
    public void setDataCoding(byte dataCoding) {
        // do nothing
    }

    @Override
    public void setDstNPI(byte dstNPI) {
        // do nothing
    }

    @Override
    public void setDstTON(byte dstTON) {
        // do nothing
    }

    @Override
    public void setEncoding(String encoding) {
        // do nothing
    }

    @Override
    public void setHost(String host) {
        // do nothing
    }

    @Override
    public void setId(int id) {
        // do nothing
    }

    @Override
    public void setPassword(String password) {
        // do nothing
    }

    @Override
    public void setPort(int port) {
        // do nothing
    }

    @Override
    public void setSrcNPI(byte srcNPI) {
        // do nothing
    }

    @Override
    public void setSrcTON(byte srcTON) {
        // do nothing
    }

    @Override
    public void setSystemId(String systemId) {
        // do nothing
    }

    @Override
    public void setSystemType(String systemType) {
        // do nothing
    }

    @Override
    public void setThreads(int threads) {
        // do nothing
    }

    @Override
    public void setTimeout(long timeout) {
        // do nothing
    }

    public SMPPTransmitterReadonly() {

    }

    public SMPPTransmitterReadonly(SMPPTransmitter transmitter, int ref) {
        this.transmitter = transmitter;
        this.ref = ref;
    }

    @Override
    protected List<String> splitMessages(SMPPRequest req) throws UnsupportedEncodingException {
        throw new UnsupportedOperationException("splitMessages");
    }

    @Override
    public void start() {
        // do nothing
    }

    @Override
    public boolean isDead() {
        return transmitter.isDead();
    }
}
