package com.telemessage.simulators.smpp_cloudhopper;

import com.telemessage.simulators.Simulator;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.common.services.filemanager.SimFileManager;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.smpp.SMPPRequest;
import com.telemessage.simulators.smpp.conf.SMPPConnectionConf;
import com.telemessage.simulators.smpp.conf.SMPPConnections;
import com.telemessage.simulators.smpp_cloudhopper.config.CloudhopperProperties;
import com.telemessage.simulators.smpp_cloudhopper.manager.CloudhopperConnectionManager;
import com.telemessage.simulators.smpp_cloudhopper.manager.CloudhopperESMEManager;
import com.telemessage.simulators.smpp_cloudhopper.manager.CloudhopperSMSCManager;
import com.telemessage.simulators.smpp_cloudhopper.util.SessionStateManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.simpleframework.xml.core.Persister;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Main orchestrator for Cloudhopper SMPP connections.
 *
 * <p>This service manages all SMPP connections using the modern Cloudhopper library.
 * It provides the same interface as the legacy SMPPSimulator for seamless switching.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li>Non-blocking I/O using Netty</li>
 *   <li>Support for ESME (client) and SMSC (server) modes</li>
 *   <li>Multiple connection types (Transmitter, Receiver, Transceiver)</li>
 *   <li>Automatic reconnection on failures</li>
 *   <li>Session state management and statistics</li>
 *   <li>Thread-safe concurrent operations</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <pre>
 * cloudhopper:
 *   enabled: true
 *   config:
 *     connection-timeout-ms: 10000
 *     bind-timeout-ms: 5000
 *     window-size: 100
 * </pre>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 * @see CloudhopperProperties
 * @see CloudhopperESMEManager
 * @see CloudhopperSMSCManager
 */
@Slf4j
@Service("cloudHopperSimulator")
public class CloudhopperSimulator implements Simulator {

    public static final String CONN_FILE = "smpps.xml";

    @Getter
    private final CloudhopperProperties properties;

    @Getter
    private final EnvConfiguration envConfig;

    @Getter
    private final MessagesCache messagesCache;

    @Getter
    private final SessionStateManager sessionStateManager;

    @Getter
    private SMPPConnections connections;

    private final Map<Integer, CloudhopperConnectionManager> connectionManagers = new ConcurrentHashMap<>();
    private final ExecutorService executorService;

    private volatile State state = State.STARTING;

    public enum State {
        STARTED, SHUTDOWN, STARTING, INVALID
    }

    /**
     * Constructor with dependency injection.
     *
     * @param properties Cloudhopper configuration properties
     * @param envConfig Environment configuration
     * @param messagesCache Shared message cache service
     */
    @Autowired
    public CloudhopperSimulator(
            CloudhopperProperties properties,
            EnvConfiguration envConfig,
            MessagesCache messagesCache) {
        this.properties = properties;
        this.envConfig = envConfig;
        this.messagesCache = messagesCache;
        this.sessionStateManager = new SessionStateManager();

        // Create thread pool for async operations
        int threadPoolSize = properties.getExecutor().getCorePoolSize();
        this.executorService = Executors.newFixedThreadPool(
            threadPoolSize,
            runnable -> {
                Thread thread = new Thread(runnable);
                thread.setName("cloudhopper-sim-" + thread.getId());
                thread.setDaemon(false);
                return thread;
            }
        );

        log.info("CloudhopperSimulator initialized with {} executor threads", threadPoolSize);
    }

    /**
     * Post-construct initialization.
     * Loads configuration from smpps.xml and prepares connection managers.
     */
    @PostConstruct
    public void init() {
        try {
            log.info("Initializing Cloudhopper SMPP Simulator...");
            readFromConfiguration();
            log.info("Cloudhopper SMPP Simulator configuration loaded successfully");
            log.info("Total connections configured: {}", connectionManagers.size());
        } catch (Exception e) {
            log.error("Failed to initialize Cloudhopper SMPP Simulator", e);
            state = State.INVALID;
        }
    }

    /**
     * Reads SMPP connection configuration from XML file.
     *
     * <p>Configuration file location: /com/telemessage/simulators/{ENV}/smpps.xml</p>
     *
     * @throws Exception if configuration loading fails
     */
    private void readFromConfiguration() throws Exception {
        String currentEnv = envConfig.getEnvCurrent();
        String configPath = String.format("com/telemessage/simulators/%s/%s", currentEnv, CONN_FILE);

        log.info("Loading Cloudhopper configuration from: {}", configPath);

        try (InputStream inputStream = SimFileManager.getResourceAsStream(configPath)) {
            if (inputStream == null) {
                throw new IllegalStateException("Configuration file not found: " + configPath);
            }

            Persister persister = new Persister();
            connections = persister.read(SMPPConnections.class, inputStream);

            if (connections == null || connections.getConnections() == null) {
                throw new IllegalStateException("No connections found in configuration");
            }

            log.info("Loaded {} connection configurations", connections.getConnections().size());

            // Create connection managers for each configured connection
            for (SMPPConnectionConf connConf : connections.getConnections()) {
                createConnectionManagers(connConf);
            }

        } catch (Exception e) {
            log.error("Failed to read configuration from {}", configPath, e);
            throw e;
        }
    }

    /**
     * Creates connection managers for a connection configuration.
     *
     * @param connConf Connection configuration
     */
    private void createConnectionManagers(SMPPConnectionConf connConf) {
        int connectionId = connConf.getId();

        log.info("Creating managers for connection ID {}: {}", connectionId, connConf.getName());

        // Create ESME manager if transmitter/transceiver configured
        if (connConf.getTransmitter() != null || connConf.getTransceiver() != null) {
            CloudhopperESMEManager esmeManager = new CloudhopperESMEManager(
                connectionId,
                connConf,
                properties,
                sessionStateManager,
                messagesCache,
                executorService
            );
            connectionManagers.put(connectionId, esmeManager);
            log.info("Created ESME manager for connection {}", connectionId);
        }

        // Create SMSC manager if receiver configured
        if (connConf.getReceiver() != null) {
            CloudhopperSMSCManager smscManager = new CloudhopperSMSCManager(
                connectionId,
                connConf,
                properties,
                sessionStateManager,
                messagesCache,
                executorService
            );
            connectionManagers.put(connectionId + 10000, smscManager); // Offset for receiver
            log.info("Created SMSC manager for connection {}", connectionId);
        }
    }

    /**
     * Starts all configured SMPP connections.
     */
    public void startConnections() {
        log.info("Starting all Cloudhopper SMPP connections...");
        state = State.STARTED;

        int successCount = 0;
        int failCount = 0;

        for (Map.Entry<Integer, CloudhopperConnectionManager> entry : connectionManagers.entrySet()) {
            try {
                CloudhopperConnectionManager manager = entry.getValue();
                manager.start();
                successCount++;
                log.info("Successfully started connection: {}", entry.getKey());
            } catch (Exception e) {
                failCount++;
                log.error("Failed to start connection: {}", entry.getKey(), e);
            }
        }

        log.info("Connection startup complete: {} successful, {} failed", successCount, failCount);

        if (failCount > 0 && successCount == 0) {
            state = State.INVALID;
            log.error("All connections failed to start!");
        }
    }

    /**
     * Sends a message through the specified connection.
     *
     * @param connectionId Connection ID
     * @param request SMPP request with message details
     * @param sendAllParts Whether to send all parts of concatenated messages
     * @return true if message sent successfully
     */
    public boolean send(int connectionId, SMPPRequest request, boolean sendAllParts) {
        CloudhopperConnectionManager manager = connectionManagers.get(connectionId);

        if (manager == null) {
            log.error("Connection manager not found for ID: {}", connectionId);
            return false;
        }

        if (!sessionStateManager.isSessionActive(connectionId)) {
            log.warn("Session not active for connection ID: {}", connectionId);
            return false;
        }

        try {
            return manager.send(request, sendAllParts);
        } catch (Exception e) {
            log.error("Failed to send message on connection {}", connectionId, e);
            return false;
        }
    }

    /**
     * Gets connection configuration by ID.
     *
     * @param id Connection ID
     * @return SMPPConnectionConf or null if not found
     */
    public SMPPConnectionConf get(int id) {
        if (connections == null || connections.getConnections() == null) {
            return null;
        }

        return connections.getConnections().stream()
            .filter(conn -> conn.getId() == id)
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets all connection configurations.
     *
     * @return List of all connections
     */
    public List<SMPPConnectionConf> getAllConnections() {
        return connections != null ? connections.getConnections() : List.of();
    }

    /**
     * Stops a specific connection.
     *
     * @param connectionId Connection ID
     */
    public void stopConnection(int connectionId) {
        CloudhopperConnectionManager manager = connectionManagers.get(connectionId);
        if (manager != null) {
            try {
                manager.stop();
                log.info("Connection {} stopped successfully", connectionId);
            } catch (Exception e) {
                log.error("Failed to stop connection {}", connectionId, e);
            }
        }
    }

    /**
     * Shuts down all SMPP connections and cleans up resources.
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Cloudhopper SMPP Simulator...");
        state = State.SHUTDOWN;

        // Stop all connection managers
        for (Map.Entry<Integer, CloudhopperConnectionManager> entry : connectionManagers.entrySet()) {
            try {
                entry.getValue().stop();
                log.info("Stopped connection manager: {}", entry.getKey());
            } catch (Exception e) {
                log.error("Error stopping connection manager: {}", entry.getKey(), e);
            }
        }

        connectionManagers.clear();

        // Shutdown executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor service did not terminate in time, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for executor shutdown", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clear session state
        sessionStateManager.clear();

        log.info("Cloudhopper SMPP Simulator shutdown complete");
    }

    /**
     * Gets the current simulator state.
     *
     * @return Current state
     */
    public State getManagerState() {
        return state;
    }

    /**
     * Checks if the simulator is ready to accept messages.
     *
     * @return true if ready
     */
    public boolean isReady() {
        return state == State.STARTED &&
               sessionStateManager.getActiveSessionCount() > 0;
    }

    /**
     * Gets statistics summary.
     *
     * @return Statistics as formatted string
     */
    public String getStatistics() {
        return String.format(
            "Cloudhopper SMPP Statistics: State=%s, ActiveSessions=%d, TotalSent=%d, TotalReceived=%d, TotalErrors=%d",
            state,
            sessionStateManager.getActiveSessionCount(),
            sessionStateManager.getTotalMessagesSent(),
            sessionStateManager.getTotalMessagesReceived(),
            sessionStateManager.getTotalErrors()
        );
    }
}
