package com.telemessage.simulators.smpp_cloudhopper.manager;

import com.telemessage.simulators.smpp.SMPPRequest;

/**
 * Interface for Cloudhopper SMPP connection managers.
 *
 * <p>Defines the contract for managing SMPP connections in both ESME (client)
 * and SMSC (server) modes.</p>
 *
 * <p>Implementations:</p>
 * <ul>
 *   <li>{@link CloudhopperESMEManager} - Client-side connection management</li>
 *   <li>{@link CloudhopperSMSCManager} - Server-side connection management</li>
 * </ul>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 */
public interface CloudhopperConnectionManager {

    /**
     * Starts the connection manager and establishes SMPP connection(s).
     *
     * <p>For ESME mode: Initiates outbound connection to SMSC.</p>
     * <p>For SMSC mode: Starts listening for inbound connections.</p>
     *
     * @throws Exception if connection setup fails
     */
    void start() throws Exception;

    /**
     * Stops the connection manager and closes all active connections.
     *
     * <p>Performs graceful shutdown:</p>
     * <ul>
     *   <li>Sends UNBIND request</li>
     *   <li>Waits for pending operations to complete</li>
     *   <li>Closes network connections</li>
     *   <li>Releases resources</li>
     * </ul>
     *
     * @throws Exception if shutdown fails
     */
    void stop() throws Exception;

    /**
     * Sends a message through this connection.
     *
     * @param request SMPP request with message details
     * @param sendAllParts Whether to send all parts for concatenated messages
     * @return true if message was sent successfully
     */
    boolean send(SMPPRequest request, boolean sendAllParts);

    /**
     * Checks if the connection is currently bound and active.
     *
     * @return true if connection is bound
     */
    boolean isBound();

    /**
     * Gets the connection ID.
     *
     * @return Connection ID
     */
    int getConnectionId();

    /**
     * Gets the connection name.
     *
     * @return Connection name
     */
    String getConnectionName();
}
