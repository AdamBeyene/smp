package com.telemessage.simulators.smpp_cloudhopper.manager;

import com.cloudhopper.smpp.*;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.impl.DefaultSmppSessionHandler;
import com.cloudhopper.smpp.pdu.*;
import com.cloudhopper.smpp.type.*;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.controllers.message.MessagesObject;
import com.telemessage.simulators.controllers.message.MessageUtils;
import com.telemessage.simulators.smpp.SMPPRequest;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.smpp_cloudhopper.concatenation.CloudhopperConcatenationType;
import com.telemessage.simulators.smpp_cloudhopper.config.CloudhopperProperties;
import com.telemessage.simulators.smpp_cloudhopper.sender.CloudhopperMessageSender;
import com.telemessage.simulators.smpp_cloudhopper.session.CloudhopperClientSessionHandler;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils;
import com.telemessage.simulators.smpp_cloudhopper.util.SessionStateManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * ESME (External Short Message Entity) connection manager using Cloudhopper.
 *
 * <p>Manages client-side SMPP connections where this application acts as ESME,
 * connecting to an external SMSC (Short Message Service Center).</p>
 *
 * <p><b>Supported Bind Types:</b></p>
 * <ul>
 *   <li>TRANSMITTER - Send messages only</li>
 *   <li>RECEIVER - Receive messages only</li>
 *   <li>TRANSCEIVER - Bi-directional (send and receive)</li>
 * </ul>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic reconnection on connection loss</li>
 *   <li>Enquire link keep-alive</li>
 *   <li>Window-based flow control</li>
 *   <li>Session statistics tracking</li>
 * </ul>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 */
@Slf4j
public class CloudhopperESMEManager implements CloudhopperConnectionManager {

    private final int connectionId;
    private final SMPPConnectionConf config;
    private final CloudhopperProperties properties;
    private final SessionStateManager sessionStateManager;
    private final MessagesCache messagesCache;
    private final ExecutorService executorService;

    private SmppClient smppClient;
    private SmppSession session;
    private CloudhopperClientSessionHandler sessionHandler;
    private ScheduledExecutorService reconnectExecutor;
    private final CloudhopperMessageSender messageSender;

    private volatile boolean isRunning = false;
    private volatile int reconnectAttempts = 0;

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
    public CloudhopperESMEManager(
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
        this.messageSender = new CloudhopperMessageSender();
    }

    @Override
    public void start() throws Exception {
        log.info("Starting ESME connection: {} (ID: {})", config.getName(), connectionId);

        if (isRunning) {
            log.warn("ESME connection {} already running", connectionId);
            return;
        }

        // Create SMPP client
        smppClient = new DefaultSmppClient();

        // Create session handler
        sessionHandler = new CloudhopperClientSessionHandler(
            connectionId,
            config,
            sessionStateManager,
            messagesCache
        );

        // Create reconnect executor
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("esme-reconnect-" + connectionId);
            t.setDaemon(true);
            return t;
        });

        // Set isRunning BEFORE attempting connection so reconnect logic works
        isRunning = true;

        // Attempt initial connection (may fail, but reconnect will handle it)
        try {
            connect();
            log.info("ESME connection {} started successfully", connectionId);
        } catch (Exception e) {
            log.warn("ESME connection {} initial bind failed, will retry via reconnect mechanism", connectionId);
            // Don't rethrow - let reconnect mechanism handle it
        }
    }

    /**
     * Establishes SMPP connection and performs bind operation.
     */
    private void connect() throws Exception {
        try {
            // Build session configuration
            SmppSessionConfiguration sessionConfig = buildSessionConfiguration();

            // Bind to SMSC
            log.info("Binding to SMSC: {}:{} (System ID: {})",
                sessionConfig.getHost(),
                sessionConfig.getPort(),
                sessionConfig.getSystemId());

            session = smppClient.bind(sessionConfig, sessionHandler);

            // Register session
            sessionStateManager.registerSession(connectionId, session);

            // Update state
            sessionStateManager.updateState(connectionId, CloudhopperUtils.SessionState.BOUND);

            // Reset reconnect attempts on successful connection
            reconnectAttempts = 0;

            log.info("Successfully bound to SMSC: {} (Session: {})",
                config.getName(), session.getConfiguration().getName());

        } catch (Exception e) {
            log.error("Failed to bind to SMSC: {}", config.getName(), e);
            scheduleReconnect();
            throw e;
        }
    }

    /**
     * Builds SMPP session configuration from connection config.
     */
    private SmppSessionConfiguration buildSessionConfiguration() {
        SmppSessionConfiguration sessionConfig = new SmppSessionConfiguration();

        // Determine bind type
        SmppBindType bindType = determineBindType();
        sessionConfig.setType(bindType);

        // Connection details - handle all three types: transmitter, receiver, transceiver
        String host;
        int port;
        String systemId;
        String password;

        if (config.getTransmitter() != null) {
            host = config.getTransmitter().getHost();
            port = config.getTransmitter().getPort();
            systemId = config.getTransmitter().getSystemId();
            password = config.getTransmitter().getPassword();
        } else if (config.getReceiver() != null) {
            host = config.getReceiver().getHost();
            port = config.getReceiver().getPort();
            systemId = config.getReceiver().getSystemId();
            password = config.getReceiver().getPassword();
        } else if (config.getTransceiver() != null) {
            host = config.getTransceiver().getHost();
            port = config.getTransceiver().getPort();
            systemId = config.getTransceiver().getSystemId();
            password = config.getTransceiver().getPassword();
        } else {
            throw new IllegalStateException("No connection type configured for connection " + connectionId);
        }

        sessionConfig.setHost(host != null ? host : "localhost");
        sessionConfig.setPort(port);

        sessionConfig.setSystemId(systemId);
        sessionConfig.setPassword(password);

        // Timeouts
        sessionConfig.setConnectTimeout(properties.getConnectionTimeoutMs());
        sessionConfig.setBindTimeout(properties.getBindTimeoutMs());
        sessionConfig.setRequestExpiryTimeout(properties.getRequestExpiryTimeoutMs());
        sessionConfig.setWindowMonitorInterval(properties.getWindowMonitorIntervalMs());

        // Window settings
        sessionConfig.setWindowSize(properties.getWindowSize());

        // Monitoring
        sessionConfig.setCountersEnabled(properties.getCountersEnabled());

        // Session name for identification
        sessionConfig.setName("esme-" + connectionId + "-" + config.getName());

        // System type
        sessionConfig.setSystemType(properties.getSession().getDefaultSystemType());

        // Logging (use LoggingOptions)
        sessionConfig.getLoggingOptions().setLogPdu(properties.getSession().getLogPduEnabled());
        sessionConfig.getLoggingOptions().setLogBytes(properties.getSession().getLogBytesEnabled());

        log.debug("Session configuration: bindType={}, host={}:{}, systemId={}, windowSize={}",
            bindType, host, port, systemId, properties.getWindowSize());

        return sessionConfig;
    }

    /**
     * Determines the bind type based on configuration.
     */
    private SmppBindType determineBindType() {
        if (config.getTransceiver() != null) {
            return SmppBindType.TRANSCEIVER;
        } else if (config.getTransmitter() != null) {
            com.telemessage.simulators.smpp.SMPPConnection.BindOption bindOption = config.getTransmitter().getBindOption();
            if (bindOption == com.telemessage.simulators.smpp.SMPPConnection.BindOption.receiver) {
                return SmppBindType.RECEIVER;
            } else {
                return SmppBindType.TRANSMITTER;
            }
        }
        return SmppBindType.TRANSMITTER; // Default
    }

    @Override
    public boolean send(SMPPRequest request, boolean sendAllParts) {
        if (!isBound()) {
            log.error("Cannot send message: session not bound for connection {}", connectionId);
            return false;
        }

        try {
            // Determine encoding (default to GSM7)
            String encoding = "GSM7";

            // Determine concatenation type (default to UDHI)
            CloudhopperConcatenationType concatenationType = CloudhopperConcatenationType.UDHI;

            // Use CloudhopperMessageSender for automatic splitting and sending
            CloudhopperMessageSender.SendResult result = messageSender.sendLongMessage(
                session,
                request.getSrc(),
                request.getDst(),
                request.getText(),
                encoding,
                concatenationType,
                properties.getSession().getResponseTimeoutMs()
            );

            if (result.isSuccess()) {
                // Update stats for all parts sent
                for (int i = 0; i < result.getPartsSent(); i++) {
                    sessionStateManager.incrementMessagesSent(connectionId);
                }

                // Cache message(s)
                List<String> messageIds = result.getMessageIds();
                for (int i = 0; i < messageIds.size(); i++) {
                    String messageId = messageIds.get(i);
                    MessagesObject cacheMessage = MessagesObject.builder()
                        .dir("OUT_FULL")
                        .id(messageId)
                        .text(request.getText())
                        .from(request.getSrc())
                        .to(request.getDst())
                        .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                        .messageEncoding(encoding)
                        .concatenationType(concatenationType.name())
                        .implementationType("Cloudhopper")
                        .totalParts(result.getTotalParts())
                        .partNumber(i + 1)
                        .build();
                    messagesCache.addCacheRecord(messageId, cacheMessage);
                }

                log.info("Message sent successfully: parts={}/{}, dest={}",
                    result.getPartsSent(), result.getTotalParts(), request.getDst());
                return true;
            } else {
                sessionStateManager.incrementErrors(connectionId);
                log.error("Message sending failed: parts={}/{}, error={}",
                    result.getPartsSent(), result.getTotalParts(), result.getErrorMessage());
                return false;
            }

        } catch (Exception e) {
            sessionStateManager.incrementErrors(connectionId);
            log.error("Exception while sending message on connection {}", connectionId, e);
            return false;
        }
    }

    @Override
    public void stop() throws Exception {
        log.info("Stopping ESME connection: {}", connectionId);

        isRunning = false;

        // Stop reconnect scheduler
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdown();
            try {
                reconnectExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                reconnectExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Shutdown session handler (cleanup executor)
        if (sessionHandler != null) {
            sessionHandler.shutdown();
        }

        // Unbind and close session
        if (session != null && session.isBound()) {
            try {
                session.unbind(5000);
                session.close();
                session.destroy();
                sessionStateManager.unregisterSession(connectionId);
                log.info("Session unbound and closed for connection {}", connectionId);
            } catch (Exception e) {
                log.error("Error unbinding session for connection {}", connectionId, e);
            }
        }

        // Destroy client
        if (smppClient != null) {
            smppClient.destroy();
        }

        log.info("ESME connection {} stopped", connectionId);
    }

    @Override
    public boolean isBound() {
        return session != null && session.isBound();
    }

    @Override
    public int getConnectionId() {
        return connectionId;
    }

    @Override
    public String getConnectionName() {
        return config.getName();
    }

    /**
     * Schedules a reconnection attempt.
     */
    private void scheduleReconnect() {
        if (!properties.getMonitoring().getAutoReconnectEnabled()) {
            log.warn("Auto-reconnect disabled for connection {}, will not retry", connectionId);
            return;
        }

        if (!isRunning) {
            log.debug("Connection {} not running, skipping reconnect", connectionId);
            return;
        }

        int maxAttempts = properties.getMonitoring().getMaxReconnectAttempts();
        if (maxAttempts > 0 && reconnectAttempts >= maxAttempts) {
            log.error("╔═══════════════════════════════════════════════════════════════════╗");
            log.error("║  RECONNECTION STOPPED - Max attempts ({}) reached for conn {}   ║", maxAttempts, connectionId);
            log.error("║  Set max-reconnect-attempts: 0 for infinite retries              ║");
            log.error("╚═══════════════════════════════════════════════════════════════════╝");
            return;
        }

        reconnectAttempts++;
        long delayMs = properties.getMonitoring().getReconnectDelayMs();

        if (maxAttempts == 0) {
            log.info("Scheduling reconnect attempt {} (infinite retries) for connection {} in {}ms",
                reconnectAttempts, connectionId, delayMs);
        } else {
            log.info("Scheduling reconnect attempt {}/{} for connection {} in {}ms",
                reconnectAttempts, maxAttempts, connectionId, delayMs);
        }

        reconnectExecutor.schedule(() -> {
            try {
                log.info("Attempting reconnect #{} for connection {}", reconnectAttempts, connectionId);
                connect();
            } catch (Exception e) {
                log.error("Reconnect attempt #{} failed for connection {}: {}",
                    reconnectAttempts, connectionId, e.getMessage());
                // connect() already called scheduleReconnect() before throwing,
                // so next attempt is already scheduled
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
