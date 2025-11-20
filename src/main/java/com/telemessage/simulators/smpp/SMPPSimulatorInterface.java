package com.telemessage.simulators.smpp;

import com.telemessage.simulators.Simulator;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.smpp.conf.SMPPConnections;

/**
 * SMPP-specific simulator interface.
 *
 * <p>This interface extends the base Simulator interface and adds SMPP-specific methods.
 * Both the legacy Logica SMPPSimulator and the modern CloudhopperSimulator implement this interface.</p>
 *
 * <p>This allows controllers to use either implementation interchangeably without knowing
 * which SMPP library is being used.</p>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-20
 */
public interface SMPPSimulatorInterface extends Simulator {

    /**
     * Get the SMPP connections configuration.
     *
     * @return SMPPConnections configuration object containing all connection definitions
     */
    SMPPConnections getConns();

    /**
     * Send an SMPP message through the specified connection.
     *
     * @param connectionId The ID of the connection to send through
     * @param request The SMPP request containing message details
     * @param sendAllParts Whether to send all parts of a concatenated message
     * @return true if the message was sent successfully, false otherwise
     */
    boolean send(int connectionId, SMPPRequest request, boolean sendAllParts);

    /**
     * Get a specific SMPP connection configuration by ID.
     *
     * @param id The connection ID
     * @return The SMPPConnectionConf for the specified ID, or null if not found
     */
    SMPPConnectionConf get(int id);
}
