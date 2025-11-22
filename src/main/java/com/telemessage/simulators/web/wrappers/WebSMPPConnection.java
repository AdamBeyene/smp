package com.telemessage.simulators.web.wrappers;

import com.telemessage.simulators.smpp.SMPPConnection;
import com.telemessage.simulators.web.CloudhopperStateService;
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

    /**
     * Constructor for Logica mode (legacy behavior).
     *
     * @param conn SMPPConnection with populated connManager
     */
    public WebSMPPConnection(SMPPConnection conn) {
        this(conn, null, 0);
    }

    /**
     * Constructor that supports both Logica and Cloudhopper modes.
     *
     * @param conn SMPPConnection configuration object
     * @param cloudhopperStateService Service to query Cloudhopper state (null for Logica mode)
     * @param connectionId Parent connection ID (used for Cloudhopper state lookup)
     */
    public WebSMPPConnection(SMPPConnection conn, CloudhopperStateService cloudhopperStateService, int connectionId) {
        if (conn != null) {
            // Check if this is a reference-only connection (one-way TX or RX)
            if (conn.isReference()) {
                this.state = "NA";
                log.info("Reference-only connection {} - setting state to NA (isReference=true)", connectionId);
            } else if (cloudhopperStateService != null) {
                // Cloudhopper mode: query actual session state
                this.state = cloudhopperStateService.getConnectionState(conn, connectionId);
                log.info("Cloudhopper mode: Connection {} state = '{}' (length={}, bytes={})",
                    connectionId, this.state, this.state.length(),
                    this.state.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                // Logica mode: use connManager (original logic)
                this.state = conn.isBound() ? "Bound" : (conn.isDead() ? "Dead" : "Unbound");
                log.info("Logica mode: Connection state = '{}' (length={}, bytes={})",
                    this.state, this.state.length(),
                    this.state.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            this.type = conn.getBindType().name();
            this.port = conn.getPort();
            this.host = conn.getHost();
            this.reference = conn.isReference() ? conn.getId() : null;
        } else {
            this.state = "NA";
            log.info("Null connection - setting state to NA");
        }
    }

}
