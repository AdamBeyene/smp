package com.telemessage.simulators.smpp_cloudhopper.manager;

import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.impl.DefaultSmppServer;
import com.cloudhopper.smpp.pdu.BaseBind;
import com.cloudhopper.smpp.pdu.BaseBindResp;
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

        // Create and start server
        smppServer = new DefaultSmppServer(serverConfig, this, executorService);
        smppServer.start();

        isRunning = true;

        log.info("SMSC server {} started successfully on port {}",
            connectionId, serverConfig.getPort());
    }

    /**
     * Builds SMPP server configuration.
     */
    private SmppServerConfiguration buildServerConfiguration() {
        SmppServerConfiguration serverConfig = new SmppServerConfiguration();

        // Get port from receiver configuration
        int port = config.getReceiver() != null
            ? config.getReceiver().getPort()
            : 0;

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

        // System ID
        String systemId = config.getReceiver() != null
            ? config.getReceiver().getSystemId()
            : null;
        serverConfig.setSystemId(systemId);

        log.debug("SMSC server configuration: port={}, systemId={}, maxConnections={}",
            port, systemId, properties.getMaxConnectionSize());

        return serverConfig;
    }

    @Override
    public void sessionBindRequested(Long sessionId, SmppSessionConfiguration sessionConfiguration, BaseBind bindRequest) throws SmppProcessingException {
        log.info("Bind request received: sessionId={}, systemId={}, type={}",
            sessionId, bindRequest.getSystemId(), bindRequest.getCommandId());

        // Validate system ID and password
        String expectedSystemId = config.getReceiver() != null
            ? config.getReceiver().getSystemId()
            : null;
        String expectedPassword = config.getReceiver() != null
            ? config.getReceiver().getPassword()
            : null;

        if (expectedSystemId != null &&
            !expectedSystemId.equals(bindRequest.getSystemId())) {
            log.warn("Invalid system ID: expected={}, received={}",
                expectedSystemId, bindRequest.getSystemId());
            throw new SmppProcessingException(SmppConstants.STATUS_INVSYSID);
        }

        if (expectedPassword != null &&
            !expectedPassword.equals(bindRequest.getPassword())) {
            log.warn("Invalid password for system ID: {}", bindRequest.getSystemId());
            throw new SmppProcessingException(SmppConstants.STATUS_INVPASWD);
        }

        log.info("Bind validation successful for sessionId={}", sessionId);
    }

    @Override
    public void sessionCreated(Long sessionId, SmppServerSession session, BaseBindResp preparedBindResponse) throws SmppProcessingException {
        log.info("Session created: sessionId={}, systemId={}",
            sessionId, session.getConfiguration().getSystemId());

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

        log.info("Session registered and ready: sessionId={}", sessionId);
    }

    @Override
    public void sessionDestroyed(Long sessionId, SmppServerSession session) {
        log.info("Session destroyed: sessionId={}, systemId={}",
            sessionId, session.getConfiguration().getSystemId());

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
