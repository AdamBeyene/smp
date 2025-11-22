package com.telemessage.simulators.web;

import com.telemessage.simulators.smpp.SMPPConnection;
import com.telemessage.simulators.smpp_cloudhopper.CloudhopperSimulator;
import com.telemessage.simulators.smpp_cloudhopper.util.CloudhopperUtils;
import com.telemessage.simulators.smpp_cloudhopper.util.SessionStateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Service for querying Cloudhopper connection states.
 *
 * <p>In Cloudhopper mode, SMPPConnection objects are only configuration holders
 * and never have their connManager field populated. The actual connection state
 * exists in CloudhopperSimulator's SessionStateManager.</p>
 *
 * <p>This service bridges the gap between the config objects and the actual
 * runtime state for UI display purposes.</p>
 *
 * <p>This service is only available when Cloudhopper mode is enabled.</p>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-22
 */
@Slf4j
@Service
@ConditionalOnProperty(
    prefix = "cloudhopper",
    name = "enabled",
    havingValue = "true"
)
public class CloudhopperStateService {

    private final CloudhopperSimulator cloudhopperSimulator;

    public CloudhopperStateService(CloudhopperSimulator cloudhopperSimulator) {
        this.cloudhopperSimulator = cloudhopperSimulator;
    }

    /**
     * Gets the connection state for a specific connection in Cloudhopper mode.
     *
     * @param conn SMPPConnection config object
     * @param connectionId Parent connection ID
     * @return State string: "Bound", "Unbound", "Dead", or "NA"
     */
    public String getConnectionState(SMPPConnection conn, int connectionId) {
        if (conn == null) {
            return "NA";
        }

        try {
            SessionStateManager sessionStateManager = cloudhopperSimulator.getSessionStateManager();

            // Check if session is registered
            SessionStateManager.SessionInfo sessionInfo = sessionStateManager.getSessionInfo(connectionId);

            if (sessionInfo == null) {
                // Session not registered yet - might be starting up or failed to start
                log.debug("No session info found for connection ID {}", connectionId);
                return "Unbound";
            }

            // Check session state
            CloudhopperUtils.SessionState state = sessionInfo.getState();

            if (state == null) {
                return "Unknown";
            }

            // Check if session is actually active (session object exists and is bound)
            boolean isActive = sessionStateManager.isSessionActive(connectionId);

            if (isActive && state == CloudhopperUtils.SessionState.BOUND) {
                return "Bound";
            } else if (state == CloudhopperUtils.SessionState.UNBOUND) {
                return "Unbound";
            } else if (state == CloudhopperUtils.SessionState.BINDING) {
                return "Binding";
            } else if (state == CloudhopperUtils.SessionState.UNBINDING) {
                return "Unbinding";
            } else if (state == CloudhopperUtils.SessionState.CLOSED) {
                return "Dead";
            } else {
                return "Unbound";
            }

        } catch (Exception e) {
            log.error("Error getting Cloudhopper state for connection {}", connectionId, e);
            return "Unknown";
        }
    }

    /**
     * Checks if a connection is bound in Cloudhopper mode.
     *
     * @param connectionId Connection ID
     * @return true if bound
     */
    public boolean isBound(int connectionId) {
        try {
            return cloudhopperSimulator.getSessionStateManager().isSessionActive(connectionId);
        } catch (Exception e) {
            log.error("Error checking bound state for connection {}", connectionId, e);
            return false;
        }
    }

    /**
     * Checks if a connection is dead in Cloudhopper mode.
     *
     * @param connectionId Connection ID
     * @return true if dead
     */
    public boolean isDead(int connectionId) {
        try {
            SessionStateManager.SessionInfo sessionInfo =
                cloudhopperSimulator.getSessionStateManager().getSessionInfo(connectionId);

            if (sessionInfo == null) {
                return false; // Not dead, just not started yet
            }

            CloudhopperUtils.SessionState state = sessionInfo.getState();
            return state == CloudhopperUtils.SessionState.CLOSED;

        } catch (Exception e) {
            log.error("Error checking dead state for connection {}", connectionId, e);
            return false;
        }
    }
}
