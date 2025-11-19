package com.telemessage.simulators.web.wrappers;

import com.telemessage.simulators.smpp.SMPPConnection;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Setter
@Getter
@Slf4j
public class WebSMPPConnection {

    String state;
    String type;
    int port;
    String host;
    Integer reference;

    public WebSMPPConnection() {
    }

    public WebSMPPConnection(SMPPConnection conn) {
        if (conn != null) {
            this.state = conn.isBound() ? "Bound" : (conn.isDead() ? "Dead" : "Unbound");
            this.type = conn.getBindType().name();
            this.port = conn.getPort();
            this.host = conn.getHost();
            this.reference = conn.isReference() ? conn.getId() : null;
        } else {
            this.state = "NA";
        }
    }

}
