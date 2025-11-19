package com.telemessage.simulators.web.wrappers;

import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public class SMPPWebConnection extends WebConnection {

    WebSMPPConnection transmitter;
    WebSMPPConnection receiver;

    public SMPPWebConnection() {
    }

    public SMPPWebConnection(SMPPConnectionConf conn) {
        super(conn.getId(), conn.getName());
        this.transmitter = new WebSMPPConnection(conn.getTransmitter());
        this.receiver = new WebSMPPConnection(conn.getReceiver());
    }

}
