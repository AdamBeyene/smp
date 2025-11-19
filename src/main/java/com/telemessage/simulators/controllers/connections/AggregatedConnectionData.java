package com.telemessage.simulators.controllers.connections;

import com.telemessage.simulators.web.wrappers.SMPPWebConnection;
import com.telemessage.simulators.web.wrappers.HttpWebConnection;
import java.util.List;
import java.util.Map;

public class AggregatedConnectionData {
    public Integer id;
    public String name;
    public String transmitterState;
    public String receiverState;
    public String transmitterType;
    public String receiverType;
    public Integer transmitterPort;
    public Integer receiverPort;
    public String transmitterHost;
    public String receiverHost;
    public String concatenationType;
    public List<Map<String, String>> extraInfo;

    // Constructor for SMPP connections
    public AggregatedConnectionData(
            SMPPWebConnection conn,
            List<Map<String, String>> extraInfo
    ) {
        this.id = conn.getId();
        this.name = conn.getName();
        this.transmitterState = conn.getTransmitter() != null ? conn.getTransmitter().getState() : null;
        this.receiverState = conn.getReceiver() != null ? conn.getReceiver().getState() : null;
        this.transmitterType = conn.getTransmitter() != null ? conn.getTransmitter().getType() : null;
        this.receiverType = conn.getReceiver() != null ? conn.getReceiver().getType() : null;
        this.transmitterPort = conn.getTransmitter() != null ? conn.getTransmitter().getPort() : null;
        this.receiverPort = conn.getReceiver() != null ? conn.getReceiver().getPort() : null;
        this.transmitterHost = conn.getTransmitter() != null ? conn.getTransmitter().getHost() : null;
        this.receiverHost = conn.getReceiver() != null ? conn.getReceiver().getHost() : null;
        this.concatenationType = null;
        this.extraInfo = extraInfo;
    }

    // Constructor for HTTP connections
    public AggregatedConnectionData(
            HttpWebConnection conn,
            List<Map<String, String>> extraInfo
    ) {
        this.id = conn.getId();
        this.name = conn.getName();
        this.transmitterState = conn.isStarted() ? "Bound" : "Unbound";
        this.receiverState = conn.isStarted() ? "Bound" : "Unbound";
        this.transmitterType = "HTTP";
        this.receiverType = "HTTP";
        this.transmitterPort = 8080;
        this.receiverPort = 8020;
        this.transmitterHost = conn.getInUrl();
        this.receiverHost = conn.getUrl();
        this.concatenationType = null;
        this.extraInfo = extraInfo;
    }
}
