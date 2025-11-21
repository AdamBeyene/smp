package com.telemessage.simulators.smpp_cloudhopper.connection;

import com.cloudhopper.smpp.SmppSession;
import com.cloudhopper.smpp.SmppSessionConfiguration;
import com.cloudhopper.smpp.pdu.EnquireLink;
import com.cloudhopper.smpp.pdu.EnquireLinkResp;
import com.cloudhopper.smpp.type.RecoverablePduException;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppTimeoutException;
import com.cloudhopper.smpp.type.UnrecoverablePduException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Connection monitoring and health management for Cloudhopper SMPP sessions.
 *
 * Features:
 * - Periodic enquire link (keep-alive) sending
 * - Connection health monitoring
 * - Automatic reconnection with exponential backoff
 * - Session state tracking
 * - Performance metrics collection
 * - Dead connection detection
 * - Graceful shutdown handling
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-21
 */
@Slf4j
@Component
public class CloudhopperConnectionMonitor {

    // Monitoring configuration
    private static final long DEFAULT_ENQUIRE_LINK_INTERVAL = 30000; // 30 seconds
    private static final long DEFAULT_ENQUIRE_LINK_TIMEOUT = 10000; // 10 seconds
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    private static final long INITIAL_RECONNECT_DELAY = 1000; // 1 second
    private static final long MAX_RECONNECT_DELAY = 60000; // 60 seconds
    private static final double BACKOFF_MULTIPLIER = 2.0;

    // Session tracking
    private final Map<String, MonitoredSession> monitoredSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ExecutorService reconnectExecutor = Executors.newCachedThreadPool();

    // Global monitoring state
    private final AtomicBoolean monitoringEnabled = new AtomicBoolean(true);
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    /**
     * Registers a session for monitoring.
     *
     * @param sessionId Unique session identifier
     * @param session SMPP session to monitor
     * @param config Session configuration
     * @param reconnectHandler Handler for reconnection logic
     */
    public void registerSession(String sessionId, SmppSession session,
                               SmppSessionConfiguration config,
                               ReconnectHandler reconnectHandler) {

        if (session == null || sessionId == null) {
            log.warn("Cannot register null session or sessionId");
            return;
        }

        MonitoredSession monitored = new MonitoredSession(
            sessionId, session, config, reconnectHandler
        );

        monitoredSessions.put(sessionId, monitored);

        // Start monitoring for this session
        startMonitoring(monitored);

        log.info("Registered session {} for monitoring", sessionId);
    }

    /**
     * Unregisters a session from monitoring.
     *
     * @param sessionId Session identifier
     */
    public void unregisterSession(String sessionId) {
        MonitoredSession monitored = monitoredSessions.remove(sessionId);
        if (monitored != null) {
            monitored.stopMonitoring();
            log.info("Unregistered session {} from monitoring", sessionId);
        }
    }

    /**
     * Starts monitoring for a specific session.
     */
    private void startMonitoring(MonitoredSession monitored) {
        if (!monitoringEnabled.get()) {
            return;
        }

        // Schedule periodic enquire link
        // Use default interval as SmppSessionConfiguration doesn't expose enquireLink timer directly
        long interval = DEFAULT_ENQUIRE_LINK_INTERVAL;

        monitored.enquireLinkFuture = scheduler.scheduleWithFixedDelay(
            () -> sendEnquireLink(monitored),
            interval,
            interval,
            TimeUnit.MILLISECONDS
        );

        // Schedule health check
        monitored.healthCheckFuture = scheduler.scheduleWithFixedDelay(
            () -> checkSessionHealth(monitored),
            10000, // Initial delay 10 seconds
            10000, // Check every 10 seconds
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Sends enquire link to keep session alive.
     */
    private void sendEnquireLink(MonitoredSession monitored) {
        if (!monitored.isActive() || shutdownRequested.get()) {
            return;
        }

        try {
            SmppSession session = monitored.session;
            if (session != null && session.isBound()) {
                EnquireLink enquireLink = new EnquireLink();
                enquireLink.setSequenceNumber(monitored.nextSequenceNumber());

                long startTime = System.currentTimeMillis();

                // Send enquire link and wait for response
                EnquireLinkResp response = session.enquireLink(
                    enquireLink,
                    DEFAULT_ENQUIRE_LINK_TIMEOUT
                );

                long responseTime = System.currentTimeMillis() - startTime;
                monitored.updateMetrics(responseTime);

                log.trace("Enquire link successful for session {}, response time: {}ms",
                        monitored.sessionId, responseTime);

                // Reset failure counter on success
                monitored.consecutiveFailures.set(0);

            } else {
                log.debug("Session {} not bound, skipping enquire link", monitored.sessionId);
                monitored.incrementFailures();
            }

        } catch (SmppTimeoutException e) {
            log.warn("Enquire link timeout for session {}", monitored.sessionId);
            monitored.incrementFailures();
            handleEnquireLinkFailure(monitored);

        } catch (RecoverablePduException | UnrecoverablePduException e) {
            log.error("PDU error during enquire link for session {}", monitored.sessionId, e);
            monitored.incrementFailures();
            handleEnquireLinkFailure(monitored);

        } catch (SmppChannelException e) {
            log.error("Channel error during enquire link for session {}", monitored.sessionId, e);
            monitored.markDead();
            triggerReconnection(monitored);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Enquire link interrupted for session {}", monitored.sessionId);

        } catch (Exception e) {
            log.error("Unexpected error during enquire link for session {}", monitored.sessionId, e);
            monitored.incrementFailures();
        }
    }

    /**
     * Checks overall session health.
     */
    private void checkSessionHealth(MonitoredSession monitored) {
        if (shutdownRequested.get()) {
            return;
        }

        try {
            SmppSession session = monitored.session;

            // Check if session is still valid
            if (session == null || !session.isBound()) {
                if (!monitored.reconnecting.get()) {
                    log.warn("Session {} is not bound, triggering reconnection", monitored.sessionId);
                    triggerReconnection(monitored);
                }
                return;
            }

            // Check for excessive failures
            if (monitored.consecutiveFailures.get() >= MAX_CONSECUTIVE_FAILURES) {
                log.error("Session {} exceeded max failures ({}), marking as dead",
                        monitored.sessionId, MAX_CONSECUTIVE_FAILURES);
                monitored.markDead();
                triggerReconnection(monitored);
                return;
            }

            // Check for stale session (no activity for too long)
            long timeSinceLastActivity = System.currentTimeMillis() - monitored.lastActivityTime.get();
            if (timeSinceLastActivity > 300000) { // 5 minutes
                log.warn("Session {} appears stale (no activity for {}ms)",
                        monitored.sessionId, timeSinceLastActivity);
                sendEnquireLink(monitored); // Force an enquire link
            }

            // Log health status periodically
            if (monitored.enquireLinkCount.get() % 10 == 0) {
                log.debug("Session {} health: enquireLinks={}, failures={}, avgResponseTime={}ms",
                        monitored.sessionId,
                        monitored.enquireLinkCount.get(),
                        monitored.consecutiveFailures.get(),
                        monitored.getAverageResponseTime());
            }

        } catch (Exception e) {
            log.error("Error during health check for session {}", monitored.sessionId, e);
        }
    }

    /**
     * Handles enquire link failure.
     */
    private void handleEnquireLinkFailure(MonitoredSession monitored) {
        int failures = monitored.consecutiveFailures.get();

        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            log.error("Session {} has failed {} consecutive enquire links, marking as dead",
                    monitored.sessionId, failures);
            monitored.markDead();
            triggerReconnection(monitored);
        } else {
            log.warn("Session {} enquire link failure {} of {}",
                    monitored.sessionId, failures, MAX_CONSECUTIVE_FAILURES);
        }
    }

    /**
     * Triggers reconnection for a dead session.
     */
    private void triggerReconnection(MonitoredSession monitored) {
        if (monitored.reconnecting.compareAndSet(false, true)) {
            reconnectExecutor.submit(() -> performReconnection(monitored));
        }
    }

    /**
     * Performs reconnection with exponential backoff.
     */
    private void performReconnection(MonitoredSession monitored) {
        if (monitored.reconnectHandler == null) {
            log.warn("No reconnect handler for session {}", monitored.sessionId);
            monitored.reconnecting.set(false);
            return;
        }

        long delay = INITIAL_RECONNECT_DELAY;
        int attempt = 0;
        final int maxAttempts = 10;

        while (attempt < maxAttempts && !shutdownRequested.get()) {
            attempt++;

            try {
                log.info("Reconnection attempt {} for session {}, delay={}ms",
                        attempt, monitored.sessionId, delay);

                // Wait with exponential backoff
                Thread.sleep(delay);

                // Attempt reconnection
                SmppSession newSession = monitored.reconnectHandler.reconnect(
                    monitored.sessionId,
                    monitored.config
                );

                if (newSession != null && newSession.isBound()) {
                    // Success!
                    monitored.session = newSession;
                    monitored.consecutiveFailures.set(0);
                    monitored.reconnectAttempts.set(0);
                    monitored.lastActivityTime.set(System.currentTimeMillis());

                    log.info("Successfully reconnected session {} after {} attempts",
                            monitored.sessionId, attempt);

                    // Restart monitoring
                    startMonitoring(monitored);
                    break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Reconnection interrupted for session {}", monitored.sessionId);
                break;

            } catch (Exception e) {
                log.error("Reconnection attempt {} failed for session {}",
                        attempt, monitored.sessionId, e);
            }

            // Exponential backoff
            delay = Math.min((long)(delay * BACKOFF_MULTIPLIER), MAX_RECONNECT_DELAY);
        }

        if (attempt >= maxAttempts) {
            log.error("Failed to reconnect session {} after {} attempts",
                    monitored.sessionId, maxAttempts);
        }

        monitored.reconnecting.set(false);
    }

    /**
     * Gets monitoring statistics for a session.
     */
    public SessionStatistics getStatistics(String sessionId) {
        MonitoredSession monitored = monitoredSessions.get(sessionId);
        if (monitored == null) {
            return null;
        }

        return new SessionStatistics(
            monitored.sessionId,
            monitored.isActive(),
            monitored.enquireLinkCount.get(),
            monitored.consecutiveFailures.get(),
            monitored.getAverageResponseTime(),
            monitored.lastActivityTime.get(),
            monitored.reconnectAttempts.get()
        );
    }

    /**
     * Gets statistics for all monitored sessions.
     */
    public Map<String, SessionStatistics> getAllStatistics() {
        Map<String, SessionStatistics> stats = new HashMap<>();
        for (Map.Entry<String, MonitoredSession> entry : monitoredSessions.entrySet()) {
            stats.put(entry.getKey(), getStatistics(entry.getKey()));
        }
        return stats;
    }

    /**
     * Enables or disables monitoring globally.
     */
    public void setMonitoringEnabled(boolean enabled) {
        monitoringEnabled.set(enabled);
        log.info("Connection monitoring {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Shuts down the monitoring system.
     */
    public void shutdown() {
        log.info("Shutting down connection monitor");
        shutdownRequested.set(true);
        monitoringEnabled.set(false);

        // Stop monitoring all sessions
        for (MonitoredSession monitored : monitoredSessions.values()) {
            monitored.stopMonitoring();
        }

        // Shutdown executors
        scheduler.shutdown();
        reconnectExecutor.shutdown();

        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!reconnectExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                reconnectExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            reconnectExecutor.shutdownNow();
        }

        monitoredSessions.clear();
    }

    /**
     * Monitored session wrapper.
     */
    private static class MonitoredSession {
        final String sessionId;
        volatile SmppSession session;
        final SmppSessionConfiguration config;
        final ReconnectHandler reconnectHandler;

        final AtomicInteger sequenceNumber = new AtomicInteger(1);
        final AtomicInteger enquireLinkCount = new AtomicInteger(0);
        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final AtomicInteger reconnectAttempts = new AtomicInteger(0);
        final AtomicLong lastActivityTime = new AtomicLong(System.currentTimeMillis());
        final AtomicBoolean reconnecting = new AtomicBoolean(false);

        // Performance metrics
        final AtomicLong totalResponseTime = new AtomicLong(0);
        final AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong maxResponseTime = new AtomicLong(0);

        ScheduledFuture<?> enquireLinkFuture;
        ScheduledFuture<?> healthCheckFuture;

        MonitoredSession(String sessionId, SmppSession session,
                        SmppSessionConfiguration config,
                        ReconnectHandler reconnectHandler) {
            this.sessionId = sessionId;
            this.session = session;
            this.config = config;
            this.reconnectHandler = reconnectHandler;
        }

        boolean isActive() {
            return session != null && session.isBound() && !reconnecting.get();
        }

        int nextSequenceNumber() {
            int seq = sequenceNumber.getAndIncrement();
            if (seq > 0x7FFFFFFF) {
                sequenceNumber.set(1);
                return 1;
            }
            return seq;
        }

        void incrementFailures() {
            consecutiveFailures.incrementAndGet();
        }

        void markDead() {
            if (session != null) {
                try {
                    session.close();
                } catch (Exception e) {
                    log.debug("Error closing dead session", e);
                }
            }
        }

        void updateMetrics(long responseTime) {
            enquireLinkCount.incrementAndGet();
            totalResponseTime.addAndGet(responseTime);
            lastActivityTime.set(System.currentTimeMillis());

            // Update min/max
            long currentMin = minResponseTime.get();
            if (responseTime < currentMin) {
                minResponseTime.compareAndSet(currentMin, responseTime);
            }

            long currentMax = maxResponseTime.get();
            if (responseTime > currentMax) {
                maxResponseTime.compareAndSet(currentMax, responseTime);
            }
        }

        long getAverageResponseTime() {
            int count = enquireLinkCount.get();
            return count > 0 ? totalResponseTime.get() / count : 0;
        }

        void stopMonitoring() {
            if (enquireLinkFuture != null) {
                enquireLinkFuture.cancel(true);
            }
            if (healthCheckFuture != null) {
                healthCheckFuture.cancel(true);
            }
        }
    }

    /**
     * Handler interface for reconnection logic.
     */
    public interface ReconnectHandler {
        SmppSession reconnect(String sessionId, SmppSessionConfiguration config) throws Exception;
    }

    /**
     * Session statistics container.
     */
    public static class SessionStatistics {
        public final String sessionId;
        public final boolean active;
        public final int enquireLinkCount;
        public final int consecutiveFailures;
        public final long averageResponseTime;
        public final long lastActivityTime;
        public final int reconnectAttempts;

        SessionStatistics(String sessionId, boolean active, int enquireLinkCount,
                         int consecutiveFailures, long averageResponseTime,
                         long lastActivityTime, int reconnectAttempts) {
            this.sessionId = sessionId;
            this.active = active;
            this.enquireLinkCount = enquireLinkCount;
            this.consecutiveFailures = consecutiveFailures;
            this.averageResponseTime = averageResponseTime;
            this.lastActivityTime = lastActivityTime;
            this.reconnectAttempts = reconnectAttempts;
        }

        @Override
        public String toString() {
            return String.format("Session[%s]: active=%s, enquireLinks=%d, failures=%d, avgResponse=%dms",
                sessionId, active, enquireLinkCount, consecutiveFailures, averageResponseTime);
        }
    }
}