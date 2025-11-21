package com.telemessage.simulators.smpp_cloudhopper.manager;

import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.type.SmppProcessingException;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.smpp.SMPPRequest;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.smpp_cloudhopper.config.CloudhopperProperties;
import com.telemessage.simulators.smpp_cloudhopper.session.CloudhopperClientSessionHandler;
import com.telemessage.simulators.smpp_cloudhopper.util.SessionStateManager;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;

/**
 * SMSC (Short Message Service Center) connection manager using Cloudhopper.
 *
 * <p>Manages server-side SMPP connections where this application acts as SMSC,
 * accepting inbound connections from external ESMEs.</p>
 *
 * <p><b>Supported Operations:</b></p>
 * <ul>
 *   <li>Accept BIND requests from clients</li>
 *   <li>Receive SUBMIT_SM messages from clients</li>
 *   <li>Send DELIVER_SM messages to clients</li>
 *   <li>Handle ENQUIRE_LINK keep-alive</li>
 * </ul>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Multi-client support</li>
 *   <li>Authentication validation</li>
 *   <li>Session management per client</li>
 *   <li>Automatic DR generation</li>
 * </ul>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 */
@Slf4j
public class CloudhopperSMSCManager implements CloudhopperConnectionManager, SmppServerHandler {

    private final int connectionId;
    private final SMPPConnectionConf config;
    private final CloudhopperProperties properties;
    private final SessionStateManager sessionStateManager;
    private final MessagesCache messagesCache;
    private final ExecutorService executorService;

    private SmppServer smppServer;
    private volatile boolean isRunning = false;

    /**
     * Constructor.
     *
     * @param connectionId Connection ID
     * @param config Connection configuration
     * @param properties Cloudhopper properties
     * @param sessionStateManager Session state manager
     * @param messagesCache Message cache service
     * @param executorService Executor service for async operations
     */
    public CloudhopperSMSCManager(
            int connectionId,
            SMPPConnectionConf config,
            CloudhopperProperties properties,
            SessionStateManager sessionStateManager,
            MessagesCache messagesCache,
            ExecutorService executorService) {
        this.connectionId = connectionId;
        this.config = config;
        this.properties = properties;
        this.sessionStateManager = sessionStateManager;
        this.messagesCache = messagesCache;
        this.executorService = executorService;
    }

    @Override
    public void start() throws Exception {
        log.info("Starting SMSC server: {} (ID: {})", config.getName(), connectionId);

        if (isRunning) {
            log.warn("SMSC server {} already running", connectionId);
            return;
        }

        // Build server configuration
        SmppServerConfiguration serverConfig = buildServerConfiguration();

        log.info("Creating SMSC server with config: port={}, host={}, bindTimeout={}ms, maxConnections={}",
                 serverConfig.getPort(),
                 serverConfig.getHost(),
                 serverConfig.getBindTimeout(),
                 serverConfig.getMaxConnectionSize());

        // Create and start server
        smppServer = new DefaultSmppServer(serverConfig, this, executorService);

        log.info("Starting SMSC server listen on port {}...", serverConfig.getPort());
        smppServer.start();

        isRunning = true;

        log.info("✓ SMSC server {} started successfully on {}:{}",
            connectionId, serverConfig.getHost(), serverConfig.getPort());
    }

    /**
     * Builds SMPP server configuration.
     */
    private SmppServerConfiguration buildServerConfiguration() {
        SmppServerConfiguration serverConfig = new SmppServerConfiguration();

        // Get port from any configured element (receiver, transmitter, or transceiver)
        int port;
        String systemId;

        if (config.getReceiver() != null) {
            port = config.getReceiver().getPort();
            systemId = config.getReceiver().getSystemId();
        } else if (config.getTransmitter() != null) {
            port = config.getTransmitter().getPort();
            systemId = config.getTransmitter().getSystemId();
        } else if (config.getTransceiver() != null) {
            port = config.getTransceiver().getPort();
            systemId = config.getTransceiver().getSystemId();
        } else {
            throw new IllegalStateException("No connection type configured for SMSC connection " + connectionId);
        }

        // Bind to all interfaces (0.0.0.0) to accept connections from host machine
        serverConfig.setHost("0.0.0.0");
        serverConfig.setPort(port);
        serverConfig.setName("smsc-" + connectionId + "-" + config.getName());

        // Timeouts
        serverConfig.setBindTimeout(properties.getBindTimeoutMs());
        serverConfig.setDefaultRequestExpiryTimeout(properties.getRequestExpiryTimeoutMs());
        serverConfig.setDefaultWindowMonitorInterval(properties.getWindowMonitorIntervalMs());

        // Window settings
        serverConfig.setDefaultWindowSize(properties.getWindowSize());

        // Session limits
        serverConfig.setMaxConnectionSize(properties.getMaxConnectionSize());

        // Enable non-blocking sockets
        serverConfig.setNonBlockingSocketsEnabled(properties.getNonBlockingSocketsEnabled());

        log.debug("SMSC server configuration: host=0.0.0.0, port={}, expectedSystemId={}, maxConnections={}",
            port, systemId, properties.getMaxConnectionSize());

        return serverConfig;
    }

    @Override
    public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, BaseBind bindRequest) throws SmppProcessingException {
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║  SMSC BIND REQUEST RECEIVED on connection {}               ║", connectionId);
        log.info("║  SessionId: {}, SystemId: {}, Type: {}          ║", sessionId, bindRequest.getSystemId(), bindRequest.getCommandId());
        log.info("╚════════════════════════════════════════════════════════════════╝");

        try {
            // Get expected credentials from any configured element
            String expectedSystemId;
            String expectedPassword;

            if (config.getReceiver() != null) {
                expectedSystemId = config.getReceiver().getSystemId();
                expectedPassword = config.getReceiver().getPassword();
            } else if (config.getTransmitter() != null) {
                expectedSystemId = config.getTransmitter().getSystemId();
                expectedPassword = config.getTransmitter().getPassword();
            } else if (config.getTransceiver() != null) {
                expectedSystemId = config.getTransceiver().getSystemId();
                expectedPassword = config.getTransceiver().getPassword();
            } else {
                log.error("No connection type configured - rejecting bind");
                throw new SmppProcessingException(SmppConstants.STATUS_SYSERR);
            }

            log.debug("Validating credentials: expected systemId={}", expectedSystemId);

            if (expectedSystemId != null &&
                !expectedSystemId.equals(bindRequest.getSystemId())) {
                log.warn("❌ Invalid system ID: expected={}, received={}",
                    expectedSystemId, bindRequest.getSystemId());
                throw new SmppProcessingException(SmppConstants.STATUS_INVSYSID);
            }

            if (expectedPassword != null &&
                !expectedPassword.equals(bindRequest.getPassword())) {
                log.warn("❌ Invalid password for system ID: {}", bindRequest.getSystemId());
                throw new SmppProcessingException(SmppConstants.STATUS_INVPASWD);
            }

            log.info("✓ Bind validation successful for sessionId={}, systemId={}",
                     sessionId, bindRequest.getSystemId());

        } catch (SmppProcessingException e) {
            log.error("Bind request validation failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during bind validation", e);
            throw new SmppProcessingException(SmppConstants.STATUS_SYSERR);
        }
    }

    @Override
    public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException {
        log.info("✓ Session created: sessionId={}, systemId={}, connection={}",
            sessionId, session.getConfiguration().getSystemId(), connectionId);

        try {
            // Create session handler
            CloudhopperClientSessionHandler sessionHandler = new CloudhopperClientSessionHandler(
                connectionId,
                config,
                sessionStateManager,
                messagesCache
            );

            // Set session handler
            session.serverReady(sessionHandler);

            // Register session
            sessionStateManager.registerSession(connectionId, session);

            log.info("✓ Session registered and ready: sessionId={}, connection={}", sessionId, connectionId);
        } catch (Exception e) {
            log.error("Failed to create session handler for sessionId={}", sessionId, e);
            throw new SmppProcessingException(SmppConstants.STATUS_SYSERR);
        }
    }

    @Override
    public void sessionDestroyed(Long sessionId, SmppServerSession session) {
        log.info("Session destroyed: sessionId={}, systemId={}, connection={}",
            sessionId, session.getConfiguration().getSystemId(), connectionId);

        sessionStateManager.unregisterSession(connectionId);
    }

    @Override
    public boolean send(SMPPRequest request, boolean sendAllParts) {
        // SMSC mode typically receives messages rather than sending
        // This method could be used to send DELIVER_SM (delivery receipts)
        log.warn("Send operation not typically used in SMSC mode for connection {}", connectionId);
        return false;
    }

    @Override
    public void stop() throws Exception {
        log.info("Stopping SMSC server: {}", connectionId);

        isRunning = false;

        if (smppServer != null) {
            smppServer.stop();
            smppServer.destroy();
        }

        log.info("SMSC server {} stopped", connectionId);
    }

    @Override
    public boolean isBound() {
        // SMSC server is "bound" if it's running and accepting connections
        return isRunning && smppServer != null;
    }

    @Override
    public int getConnectionId() {
        return connectionId;
    }

    @Override
    public String getConnectionName() {
        return config.getName();
    }
}
