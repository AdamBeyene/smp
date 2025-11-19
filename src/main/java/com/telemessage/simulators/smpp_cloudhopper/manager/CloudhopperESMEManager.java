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
import com.telemessage.simulators.smpp_cloudhopper.config.CloudhopperProperties;
import com.telemessage.simulators.smpp_cloudhopper.session.CloudhopperClientSessionHandler;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils;
import com.telemessage.simulators.smpp_cloudhopper.util.SessionStateManager;
import lombok.extern.slf4j.Slf4j;

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

        // Attempt initial connection
        connect();

        isRunning = true;
        log.info("ESME connection {} started successfully", connectionId);
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

        // Connection details
        String host = (config.getTransmitter() != null)
            ? config.getTransmitter().getHost()
            : config.getTransceiver().getHost();
        int port = (config.getTransmitter() != null)
            ? config.getTransmitter().getPort()
            : config.getTransceiver().getPort();

        sessionConfig.setHost(host != null ? host : "localhost");
        sessionConfig.setPort(port);

        // Authentication
        String systemId = (config.getTransmitter() != null)
            ? config.getTransmitter().getSystemId()
            : config.getTransceiver().getSystemId();
        String password = (config.getTransmitter() != null)
            ? config.getTransmitter().getPassword()
            : config.getTransceiver().getPassword();

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
            // Create submit_sm PDU
            SubmitSm submitSm = new SubmitSm();

            // Determine source TON/NPI (alphanumeric vs numeric)
            byte srcTon = SmppConstants.TON_ALPHANUMERIC;
            byte srcNpi = SmppConstants.NPI_UNKNOWN;
            String src = request.getSrc();
            if (src != null && src.matches("^[0-9+]+$")) {
                // Numeric address
                srcTon = SmppConstants.TON_INTERNATIONAL;
                srcNpi = SmppConstants.NPI_E164;
            }

            // Determine destination TON/NPI
            byte dstTon = SmppConstants.TON_INTERNATIONAL;
            byte dstNpi = SmppConstants.NPI_E164;

            // Set source address
            submitSm.setSourceAddress(CloudhopperUtils.createAddress(srcTon, srcNpi, src));

            // Set destination address
            submitSm.setDestAddress(CloudhopperUtils.createAddress(dstTon, dstNpi, request.getDst()));

            // Set message text and encoding (default to GSM7)
            String encoding = "GSM7";
            byte[] messageBytes = CloudhopperUtils.encodeMessage(request.getText(), encoding);
            submitSm.setShortMessage(messageBytes);
            submitSm.setDataCoding(CloudhopperUtils.getDataCoding(encoding));

            // Set service type
            if (request.getServiceType() != null) {
                submitSm.setServiceType(request.getServiceType());
            }

            // Set registered delivery (request DR)
            submitSm.setRegisteredDelivery(SmppConstants.REGISTERED_DELIVERY_SMSC_RECEIPT_REQUESTED);

            // Send synchronously
            SubmitSmResp response = session.submit(submitSm,
                properties.getSession().getResponseTimeoutMs());

            // Check response
            if (response.getCommandStatus() == SmppConstants.STATUS_OK) {
                sessionStateManager.incrementMessagesSent(connectionId);

                // Cache message
                String messageId = response.getMessageId();
                MessagesObject cacheMessage = MessagesObject.builder()
                    .dir("OUT_FULL")
                    .id(messageId)
                    .text(request.getText())
                    .from(request.getSrc())
                    .to(request.getDst())
                    .messageTime(MessageUtils.getMessageDateFromTimestamp(System.currentTimeMillis()))
                    .messageEncoding(encoding)
                    .build();
                messagesCache.addCacheRecord(messageId, cacheMessage);

                log.debug("Message sent successfully: msgId={}, dest={}",
                    messageId, request.getDst());
                return true;
            } else {
                sessionStateManager.incrementErrors(connectionId);
                log.error("Message submission failed: status={}",
                    response.getCommandStatus());
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
        if (!properties.getMonitoring().getAutoReconnectEnabled() || !isRunning) {
            return;
        }

        int maxAttempts = properties.getMonitoring().getMaxReconnectAttempts();
        if (maxAttempts > 0 && reconnectAttempts >= maxAttempts) {
            log.error("Max reconnect attempts ({}) reached for connection {}", maxAttempts, connectionId);
            return;
        }

        reconnectAttempts++;
        long delayMs = properties.getMonitoring().getReconnectDelayMs();

        log.info("Scheduling reconnect attempt {} for connection {} in {}ms",
            reconnectAttempts, connectionId, delayMs);

        reconnectExecutor.schedule(() -> {
            try {
                log.info("Attempting reconnect #{} for connection {}", reconnectAttempts, connectionId);
                connect();
            } catch (Exception e) {
                log.error("Reconnect attempt #{} failed for connection {}",
                    reconnectAttempts, connectionId, e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
