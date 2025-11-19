package com.telemessage.simulators.smpp_cloudhopper.config;

import com.telemessage.simulators.Simulator;
import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.smpp_cloudhopper.CloudhopperSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.Executor;

/**
 * Auto-configuration for Cloudhopper SMPP implementation.
 *
 * <p>This configuration is activated when the property 'cloudhopper.enabled' is set to 'true'.</p>
 *
 * <p>It provides all necessary beans for the Cloudhopper implementation including:</p>
 * <ul>
 *   <li>CloudhopperSimulator - Main SMPP simulator service</li>
 *   <li>ThreadPoolTaskExecutor - Async task execution</li>
 *   <li>Configuration properties binding</li>
 * </ul>
 *
 * <p>Example activation in application.yaml:</p>
 * <pre>
 * cloudhopper:
 *   enabled: true
 * </pre>
 *
 * <p><b>Note:</b> When this configuration is active, the Logica SMPP implementation
 * will be disabled automatically via @ConditionalOnProperty.</p>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 * @see CloudhopperProperties
 * @see CloudhopperSimulator
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "cloudhopper",
    name = "enabled",
    havingValue = "true"
)
@EnableConfigurationProperties(CloudhopperProperties.class)
public class CloudhopperAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  CLOUDHOPPER SMPP ENABLED                                        ║");
        log.info("║  Using modern Fizzed Cloudhopper SMPP 5.0.9 implementation      ║");
        log.info("║  Logica SMPP implementation is DISABLED                         ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");
    }

    /**
     * Creates the CloudhopperSimulator bean (main SMPP simulator).
     *
     * <p>This is the modern Cloudhopper implementation that replaces the legacy
     * SMPPSimulator when cloudhopper.enabled=true.</p>
     *
     * @param properties Cloudhopper configuration properties
     * @param envConfig Environment configuration
     * @param messagesCache Shared message cache service
     * @return Configured CloudhopperSimulator instance
     */
    @Bean(name = "smppSimulator")
    public CloudhopperSimulator cloudhopperSimulator(
            CloudhopperProperties properties,
            EnvConfiguration envConfig,
            MessagesCache messagesCache) {

        log.info("Creating CloudhopperSimulator bean with configuration:");
        log.info("  - Connection Timeout: {}ms", properties.getConnectionTimeoutMs());
        log.info("  - Bind Timeout: {}ms", properties.getBindTimeoutMs());
        log.info("  - Window Size: {}", properties.getWindowSize());
        log.info("  - Max Connections: {}", properties.getMaxConnectionSize());
        log.info("  - Non-Blocking Sockets: {}", properties.getNonBlockingSocketsEnabled());
        log.info("  - JMX Enabled: {}", properties.getJmxEnabled());

        return new CloudhopperSimulator(properties, envConfig, messagesCache);
    }

    /**
     * Creates a dedicated thread pool executor for Cloudhopper async operations.
     *
     * <p>This executor handles:</p>
     * <ul>
     *   <li>Async message submission</li>
     *   <li>PDU processing</li>
     *   <li>Connection monitoring</li>
     *   <li>Reconnection tasks</li>
     * </ul>
     *
     * <p><b>Thread Pool Sizing Guidelines:</b></p>
     * <ul>
     *   <li>Core Pool Size: Min threads always alive (default: 20)</li>
     *   <li>Max Pool Size: Max threads under load (default: 200)</li>
     *   <li>Queue Capacity: Pending tasks buffer (default: 1000)</li>
     * </ul>
     *
     * @param properties CloudhopperProperties with executor configuration
     * @return Configured ThreadPoolTaskExecutor
     */
    @Bean(name = "cloudhopperExecutor")
    public ThreadPoolTaskExecutor cloudhopperExecutor(CloudhopperProperties properties) {
        CloudhopperProperties.ExecutorProperties executorProps = properties.getExecutor();

        log.info("Configuring Cloudhopper Thread Pool Executor:");
        log.info("  - Core Pool Size: {}", executorProps.getCorePoolSize());
        log.info("  - Max Pool Size: {}", executorProps.getMaxPoolSize());
        log.info("  - Queue Capacity: {}", executorProps.getQueueCapacity());
        log.info("  - Keep Alive: {}s", executorProps.getKeepAliveSeconds());
        log.info("  - Thread Prefix: {}", executorProps.getThreadNamePrefix());

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core configuration
        executor.setCorePoolSize(executorProps.getCorePoolSize());
        executor.setMaxPoolSize(executorProps.getMaxPoolSize());
        executor.setQueueCapacity(executorProps.getQueueCapacity());
        executor.setKeepAliveSeconds(executorProps.getKeepAliveSeconds());
        executor.setThreadNamePrefix(executorProps.getThreadNamePrefix());

        // Advanced configuration
        executor.setAllowCoreThreadTimeOut(executorProps.getAllowCoreThreadTimeOut());
        executor.setWaitForTasksToCompleteOnShutdown(
            executorProps.getWaitForTasksToCompleteOnShutdown()
        );
        executor.setAwaitTerminationSeconds(executorProps.getAwaitTerminationSeconds());

        // Rejection policy: CallerRunsPolicy - caller thread executes if queue full
        executor.setRejectedExecutionHandler(
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );

        // Thread factory with custom naming
        executor.setThreadFactory(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(executorProps.getThreadNamePrefix() + thread.threadId());
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

        // Initialize the executor
        executor.initialize();

        log.info("Cloudhopper Thread Pool Executor initialized successfully");

        return executor;
    }

    /**
     * Creates an async executor alias for Spring's @Async annotation support.
     *
     * <p>This allows methods annotated with @Async to use the Cloudhopper thread pool.</p>
     *
     * @param cloudhopperExecutor The main Cloudhopper executor
     * @return The same executor with async alias
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor(ThreadPoolTaskExecutor cloudhopperExecutor) {
        return cloudhopperExecutor;
    }
}
