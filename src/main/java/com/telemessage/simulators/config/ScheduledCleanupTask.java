package com.telemessage.simulators.config;

import com.telemessage.simulators.smpp.SMPPTransceiver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Scheduled task for cleaning up stale multipart messages
 *
 * This task runs periodically to remove incomplete multipart messages that have
 * exceeded the timeout period. It prevents memory leaks from:
 * - Multipart locks that are never released
 * - Partial message parts stored in cache
 * - Timestamp tracking entries
 *
 * @author Claude Code Assistant
 * @since 2025-10-24
 */
@Slf4j
@Configuration
@EnableScheduling
public class ScheduledCleanupTask {

    /**
     * Cleanup incomplete multipart messages every minute
     *
     * This method calls the static cleanup methods in SMPPTransceiver and SMPPReceiver
     * to remove stale multipart message tracking data.
     *
     * Frequency: Every 60 seconds (60000ms)
     * Timeout: 5 minutes (configured in SMPPTransceiver/SMPPReceiver)
     *
     * The cleanup process:
     * 1. Checks all tracked multipart messages
     * 2. Identifies messages older than timeout period
     * 3. Removes multipart locks
     * 4. Removes partial parts from cache
     * 5. Removes timestamp tracking entries
     * 6. Logs cleanup activities
     */
    @Scheduled(fixedDelay = 60000) // Run every minute
    public void cleanupIncompleteMessages() {
        try {
            log.trace("Running scheduled cleanup of incomplete multipart messages");

            // Cleanup for SMPPTransceiver
            SMPPTransceiver.cleanupStaleMultiparts();

            // TODO: Cleanup for SMPPReceiver when implemented
            // SMPPReceiver.cleanupStaleMultiparts();

            log.trace("Scheduled cleanup completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled cleanup of incomplete messages", e);
            // Don't rethrow - allow scheduler to continue running
        }
    }
}
