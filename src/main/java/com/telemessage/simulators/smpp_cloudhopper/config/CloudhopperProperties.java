package com.telemessage.simulators.smpp_cloudhopper.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Configuration properties for Cloudhopper SMPP implementation.
 *
 * <p>This class defines all configurable parameters for the Cloudhopper SMPP client/server
 * using Spring Boot's @ConfigurationProperties mechanism for type-safe configuration.</p>
 *
 * <p>Properties are loaded from application.yaml under the 'cloudhopper.config' prefix.</p>
 *
 * <p>Example configuration:</p>
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
 */
@Data
@Validated
@ConfigurationProperties(prefix = "cloudhopper.config")
public class CloudhopperProperties {

    /**
     * Connection timeout in milliseconds for establishing socket connections.
     * Default: 10000ms (10 seconds)
     */
    @NotNull
    @Positive
    private Integer connectionTimeoutMs = 10000;

    /**
     * Bind timeout in milliseconds for completing SMPP bind operations.
     * Default: 5000ms (5 seconds)
     */
    @NotNull
    @Positive
    private Integer bindTimeoutMs = 5000;

    /**
     * Maximum window size (number of unacknowledged requests allowed).
     * Controls flow control and throughput.
     * Default: 100
     */
    @NotNull
    @Min(1)
    private Integer windowSize = 100;

    /**
     * Request expiry timeout in milliseconds.
     * Requests older than this will be considered expired.
     * Default: 30000ms (30 seconds)
     */
    @NotNull
    @Positive
    private Integer requestExpiryTimeoutMs = 30000;

    /**
     * Window monitor interval in milliseconds.
     * How often to check for expired requests.
     * Default: 15000ms (15 seconds)
     */
    @NotNull
    @Positive
    private Integer windowMonitorIntervalMs = 15000;

    /**
     * Maximum number of concurrent connections supported.
     * Default: 100
     */
    @NotNull
    @Min(1)
    private Integer maxConnectionSize = 100;

    /**
     * Enable non-blocking sockets using Netty NIO.
     * Default: true (recommended for high throughput)
     */
    @NotNull
    private Boolean nonBlockingSocketsEnabled = true;

    /**
     * Default request expiry timeout for session-level operations.
     * Default: 30000ms (30 seconds)
     */
    @NotNull
    @Positive
    private Integer defaultRequestExpiryTimeoutMs = 30000;

    /**
     * Default window monitor interval for session-level operations.
     * Default: 15000ms (15 seconds)
     */
    @NotNull
    @Positive
    private Integer defaultWindowMonitorIntervalMs = 15000;

    /**
     * Enable performance counters for monitoring.
     * Default: true
     */
    @NotNull
    private Boolean countersEnabled = true;

    /**
     * Enable JMX for monitoring and management.
     * Default: true
     */
    @NotNull
    private Boolean jmxEnabled = true;

    /**
     * Thread pool executor configuration for async operations.
     */
    @NotNull
    private ExecutorProperties executor = new ExecutorProperties();

    /**
     * Monitoring configuration for connection health checks.
     */
    @NotNull
    private MonitoringProperties monitoring = new MonitoringProperties();

    /**
     * Session configuration properties.
     */
    @NotNull
    private SessionProperties session = new SessionProperties();

    /**
     * Thread pool executor configuration.
     */
    @Data
    public static class ExecutorProperties {
        /**
         * Core pool size (minimum number of threads).
         * Default: 20
         */
        @NotNull
        @Min(1)
        private Integer corePoolSize = 20;

        /**
         * Maximum pool size (maximum number of threads).
         * Default: 200
         */
        @NotNull
        @Min(1)
        private Integer maxPoolSize = 200;

        /**
         * Keep alive time in seconds for idle threads.
         * Default: 60 seconds
         */
        @NotNull
        @Positive
        private Integer keepAliveSeconds = 60;

        /**
         * Queue capacity for pending tasks.
         * Default: 1000
         */
        @NotNull
        @Min(0)
        private Integer queueCapacity = 1000;

        /**
         * Thread name prefix for identification.
         * Default: "cloudhopper-"
         */
        @NotNull
        private String threadNamePrefix = "cloudhopper-";

        /**
         * Whether to allow core threads to timeout.
         * Default: false
         */
        private Boolean allowCoreThreadTimeOut = false;

        /**
         * Whether to wait for tasks to complete on shutdown.
         * Default: true
         */
        private Boolean waitForTasksToCompleteOnShutdown = true;

        /**
         * Await termination timeout in seconds during shutdown.
         * Default: 60 seconds
         */
        @Positive
        private Integer awaitTerminationSeconds = 60;
    }

    /**
     * Monitoring and health check configuration.
     */
    @Data
    public static class MonitoringProperties {
        /**
         * Enquire link interval in milliseconds (keep-alive).
         * Default: 30000ms (30 seconds)
         */
        @NotNull
        @Positive
        private Integer enquireLinkIntervalMs = 30000;

        /**
         * Enquire link timeout in milliseconds.
         * Default: 10000ms (10 seconds)
         */
        @NotNull
        @Positive
        private Integer enquireLinkTimeoutMs = 10000;

        /**
         * Reconnect delay in milliseconds after connection failure.
         * Default: 5000ms (5 seconds)
         */
        @NotNull
        @Positive
        private Integer reconnectDelayMs = 5000;

        /**
         * Maximum number of reconnection attempts.
         * Default: 5 (0 = infinite)
         */
        @NotNull
        @Min(0)
        private Integer maxReconnectAttempts = 5;

        /**
         * Enable automatic reconnection on connection loss.
         * Default: true
         */
        @NotNull
        private Boolean autoReconnectEnabled = true;

        /**
         * Enable connection health monitoring.
         * Default: true
         */
        @NotNull
        private Boolean healthMonitoringEnabled = true;
    }

    /**
     * Session-level configuration properties.
     */
    @Data
    public static class SessionProperties {
        /**
         * Enable logging of PDU packets for debugging.
         * Default: false (enable only in development)
         */
        private Boolean logPduEnabled = false;

        /**
         * Enable logging of PDU bytes in hex format.
         * Default: false
         */
        private Boolean logBytesEnabled = false;

        /**
         * Default system type for bind operations.
         * Default: "" (empty)
         */
        private String defaultSystemType = "";

        /**
         * Default address range for bind operations.
         * Default: null
         */
        private String defaultAddressRange = null;

        /**
         * Enable async message submission.
         * Default: true (recommended for high throughput)
         */
        private Boolean asyncSubmitEnabled = true;

        /**
         * Maximum time to wait for response in milliseconds.
         * Default: 10000ms (10 seconds)
         */
        @Positive
        private Integer responseTimeoutMs = 10000;
    }
}
