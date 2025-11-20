package com.telemessage.simulators.smpp;

import com.telemessage.simulators.Simulator;
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
}
