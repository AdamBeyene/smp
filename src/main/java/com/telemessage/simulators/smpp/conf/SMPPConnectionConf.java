package com.telemessage.simulators.smpp.conf;

import com.telemessage.simulators.conf.AbstractConnection;
import com.telemessage.simulators.smpp.SMPPConnection;
import com.telemessage.simulators.smpp.SMPPReceiver;
import com.telemessage.simulators.smpp.SMPPTransceiver;
import com.telemessage.simulators.smpp.SMPPTransmitter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Validate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Setter
@Getter
@Slf4j
@Root(name = "connection")
public class SMPPConnectionConf extends AbstractConnection {

    @Element(required = false) SMPPTransmitter transmitter;
    @Element(required = false) SMPPReceiver receiver;
    @Element(required = false) SMPPTransceiver transceiver;
    @Attribute(required = false, name = "transmitter") int transmitterRef;


    public SMPPConnectionConf() {
        this.automaticDR = "DELIVRD";
        this.directStatus = SMPPCodes.valueOf("ESME_ROK").code;
    }

    public Integer getDirectStatusAsNumber() {
        return !StringUtils.isEmpty(super.getDirectStatus()) && !"0".equals(super.getDirectStatus())?
                Integer.parseInt(SMPPCodes.valueOf(super.getDirectStatus()).code)
                : Integer.parseInt(SMPPCodes.valueOf("ESME_ROK").code);
    }

    public void setDirectStatus(int directStatus) {
        super.setAutomaticDR(String.valueOf(directStatus));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SMPPConnectionConf)) return false;
        SMPPConnectionConf that = (SMPPConnectionConf) o;
        return id == that.id &&
                Objects.equals(transmitter, that.transmitter) &&
                Objects.equals(receiver, that.receiver) &&
                Objects.equals(transceiver, that.transceiver);
    }

    @Validate
    public void validate() {
        if (transmitter != null) transmitter.setId(id);
        if (receiver != null) receiver.setId(id);
        if (transceiver != null) transceiver.setId(id);
    }

    //To Be Reviewed
    public int getTransmitterRef() {
        return transmitterRef > 0 && transmitter != null ? transmitter.getId() : transmitterRef;
    }

    public int getRef() {
        return transmitterRef;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, transmitter, receiver, transceiver);
    }

    public List<SMPPConnection> getAllConnections() {
        return Arrays.asList(transmitter, receiver, transceiver);
    }
}
