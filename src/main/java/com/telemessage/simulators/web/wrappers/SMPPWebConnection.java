package com.telemessage.simulators.web.wrappers;

import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.web.CloudhopperStateService;
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

    /**
     * Constructor for Logica mode (legacy behavior).
     *
     * @param conn SMPPConnectionConf with transmitter/receiver SMPPConnection objects
     */
    public SMPPWebConnection(SMPPConnectionConf conn) {
        this(conn, null);
    }

    /**
     * Constructor that supports both Logica and Cloudhopper modes.
     *
     * @param conn SMPPConnectionConf configuration object
     * @param cloudhopperStateService Service to query Cloudhopper state (null for Logica mode)
     */
    public SMPPWebConnection(SMPPConnectionConf conn, CloudhopperStateService cloudhopperStateService) {
        super(conn.getId(), conn.getName());
        int connectionId = conn.getId();

        // Pass CloudhopperStateService and connection ID to WebSMPPConnection constructors
        this.transmitter = new WebSMPPConnection(conn.getTransmitter(), cloudhopperStateService, connectionId);
        this.receiver = new WebSMPPConnection(conn.getReceiver(), cloudhopperStateService, connectionId);
    }

}
