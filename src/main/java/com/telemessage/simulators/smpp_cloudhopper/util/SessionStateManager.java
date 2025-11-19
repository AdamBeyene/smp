package com.telemessage.simulators.smpp_cloudhopper.util;

import com.cloudhopper.smpp.SmppSession;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages session states and statistics for Cloudhopper SMPP connections.
 *
 * <p>This class provides:</p>
 * <ul>
 *   <li>Session state tracking (BOUND, UNBOUND, FAILED, etc.)</li>
 *   <li>Session statistics (messages sent/received, errors)</li>
 *   <li>Last activity timestamps for health monitoring</li>
 *   <li>Thread-safe concurrent access</li>
 * </ul>
 *
 * <p>Thread Safety: All operations are thread-safe using ConcurrentHashMap
 * and AtomicLong counters.</p>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 */
@Slf4j
public class SessionStateManager {

    private final Map<Integer, SessionInfo> sessions = new ConcurrentHashMap<>();
    private final Map<Integer, SmppSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Registers a new session.
     *
     * @param connectionId Connection ID
     * @param session SmppSession instance
     */
    public void registerSession(int connectionId, SmppSession session) {
        SessionInfo info = new SessionInfo(connectionId);
        info.setState(CloudhopperUtils.SessionState.BOUND);
        info.setBindTime(Instant.now());
        sessions.put(connectionId, info);
        activeSessions.put(connectionId, session);
        log.info("Session registered: connectionId={}, sessionId={}", connectionId, session.getConfiguration().getName());
    }

    /**
     * Unregisters a session.
     *
     * @param connectionId Connection ID
     */
    public void unregisterSession(int connectionId) {
        SessionInfo info = sessions.get(connectionId);
        if (info != null) {
            info.setState(CloudhopperUtils.SessionState.UNBOUND);
            info.setUnbindTime(Instant.now());
            log.info("Session unregistered: connectionId={}, totalSent={}, totalReceived={}",
                connectionId, info.getMessagesSent(), info.getMessagesReceived());
        }
        activeSessions.remove(connectionId);
    }

    /**
     * Gets the session for a connection ID.
     *
     * @param connectionId Connection ID
     * @return SmppSession or null if not found
     */
    public SmppSession getSession(int connectionId) {
        return activeSessions.get(connectionId);
    }

    /**
     * Gets session info for a connection ID.
     *
     * @param connectionId Connection ID
     * @return SessionInfo or null if not found
     */
    public SessionInfo getSessionInfo(int connectionId) {
        return sessions.get(connectionId);
    }

    /**
     * Updates the session state.
     *
     * @param connectionId Connection ID
     * @param state New state
     */
    public void updateState(int connectionId, CloudhopperUtils.SessionState state) {
        SessionInfo info = sessions.get(connectionId);
        if (info != null) {
            info.setState(state);
            info.setLastActivity(Instant.now());
            log.debug("Session state updated: connectionId={}, state={}", connectionId, state);
        }
    }

    /**
     * Increments message sent counter.
     *
     * @param connectionId Connection ID
     */
    public void incrementMessagesSent(int connectionId) {
        SessionInfo info = sessions.get(connectionId);
        if (info != null) {
            info.incrementMessagesSent();
            info.setLastActivity(Instant.now());
        }
    }

    /**
     * Increments messages received counter.
     *
     * @param connectionId Connection ID
     */
    public void incrementMessagesReceived(int connectionId) {
        SessionInfo info = sessions.get(connectionId);
        if (info != null) {
            info.incrementMessagesReceived();
            info.setLastActivity(Instant.now());
        }
    }

    /**
     * Increments error counter.
     *
     * @param connectionId Connection ID
     */
    public void incrementErrors(int connectionId) {
        SessionInfo info = sessions.get(connectionId);
        if (info != null) {
            info.incrementErrors();
            info.setLastActivity(Instant.now());
        }
    }

    /**
     * Checks if a session is active and bound.
     *
     * @param connectionId Connection ID
     * @return true if session is active
     */
    public boolean isSessionActive(int connectionId) {
        SessionInfo info = sessions.get(connectionId);
        if (info == null) {
            return false;
        }

        SmppSession session = activeSessions.get(connectionId);
        if (session == null) {
            return false;
        }

        return session.isBound() &&
               CloudhopperUtils.canSubmitMessages(info.getState());
    }

    /**
     * Gets all active connection IDs.
     *
     * @return Array of active connection IDs
     */
    public Integer[] getActiveConnectionIds() {
        return activeSessions.keySet().toArray(new Integer[0]);
    }

    /**
     * Gets total number of active sessions.
     *
     * @return Active session count
     */
    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    /**
     * Gets total messages sent across all sessions.
     *
     * @return Total messages sent
     */
    public long getTotalMessagesSent() {
        return sessions.values().stream()
            .mapToLong(SessionInfo::getMessagesSent)
            .sum();
    }

    /**
     * Gets total messages received across all sessions.
     *
     * @return Total messages received
     */
    public long getTotalMessagesReceived() {
        return sessions.values().stream()
            .mapToLong(SessionInfo::getMessagesReceived)
            .sum();
    }

    /**
     * Gets total errors across all sessions.
     *
     * @return Total errors
     */
    public long getTotalErrors() {
        return sessions.values().stream()
            .mapToLong(SessionInfo::getErrors)
            .sum();
    }

    /**
     * Clears all session data.
     */
    public void clear() {
        sessions.clear();
        activeSessions.clear();
        log.info("All session data cleared");
    }

    /**
     * Session information holder.
     */
    public static class SessionInfo {
        private final int connectionId;
        private volatile CloudhopperUtils.SessionState state;
        private volatile Instant bindTime;
        private volatile Instant unbindTime;
        private volatile Instant lastActivity;

        private final AtomicLong messagesSent = new AtomicLong(0);
        private final AtomicLong messagesReceived = new AtomicLong(0);
        private final AtomicLong errors = new AtomicLong(0);

        public SessionInfo(int connectionId) {
            this.connectionId = connectionId;
            this.state = CloudhopperUtils.SessionState.UNBOUND;
            this.lastActivity = Instant.now();
        }

        public int getConnectionId() {
            return connectionId;
        }

        public CloudhopperUtils.SessionState getState() {
            return state;
        }

        public void setState(CloudhopperUtils.SessionState state) {
            this.state = state;
        }

        public Instant getBindTime() {
            return bindTime;
        }

        public void setBindTime(Instant bindTime) {
            this.bindTime = bindTime;
        }

        public Instant getUnbindTime() {
            return unbindTime;
        }

        public void setUnbindTime(Instant unbindTime) {
            this.unbindTime = unbindTime;
        }

        public Instant getLastActivity() {
            return lastActivity;
        }

        public void setLastActivity(Instant lastActivity) {
            this.lastActivity = lastActivity;
        }

        public long getMessagesSent() {
            return messagesSent.get();
        }

        public void incrementMessagesSent() {
            messagesSent.incrementAndGet();
        }

        public long getMessagesReceived() {
            return messagesReceived.get();
        }

        public void incrementMessagesReceived() {
            messagesReceived.incrementAndGet();
        }

        public long getErrors() {
            return errors.get();
        }

        public void incrementErrors() {
            errors.incrementAndGet();
        }

        @Override
        public String toString() {
            return String.format(
                "SessionInfo{connectionId=%d, state=%s, sent=%d, received=%d, errors=%d, lastActivity=%s}",
                connectionId, state, messagesSent.get(), messagesReceived.get(), errors.get(), lastActivity
            );
        }
    }
}
