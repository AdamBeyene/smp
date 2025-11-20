package com.telemessage.simulators.smpp_cloudhopper.config;

import com.telemessage.simulators.common.conf.EnvConfiguration;
import com.telemessage.simulators.controllers.message.MessagesCache;
import com.telemessage.simulators.smpp.SMPPSimulator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Auto-configuration for Logica SMPP implementation (Legacy).
 *
 * <p>This configuration is activated when:</p>
 * <ul>
 *   <li>The property 'cloudhopper.enabled' is set to 'false', OR</li>
 *   <li>The property 'cloudhopper.enabled' is not specified (default behavior)</li>
 * </ul>
 *
 * <p>This ensures backward compatibility with existing deployments that don't
 * specify the cloudhopper.enabled property.</p>
 *
 * <p>Example configurations:</p>
 * <pre>
 * # Explicitly use Logica (Legacy)
 * cloudhopper:
 *   enabled: false
 *
 * # Or simply omit the property (defaults to Logica)
 * # cloudhopper:
 * #   enabled: true  # commented out or not present
 * </pre>
 *
 * <p><b>Note:</b> When this configuration is active, the Cloudhopper SMPP implementation
 * will be disabled automatically via @ConditionalOnProperty.</p>
 *
 * @author TM QA Team
 * @version 21.0
 * @since 2025-11-19
 * @see SMPPSimulator
 * @see CloudhopperAutoConfiguration
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "cloudhopper",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true  // IMPORTANT: Defaults to Logica if property not specified
)
public class LogicaAutoConfiguration {

    @PostConstruct
    public void init() {
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  LOGICA SMPP ENABLED (LEGACY)                                   ║");
        log.info("║  Using Logica SMPP 3.1.3 implementation                         ║");
        log.info("║  Cloudhopper SMPP implementation is DISABLED                    ║");
        log.info("║                                                                  ║");
        log.info("║  To switch to Cloudhopper, set: cloudhopper.enabled=true       ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");
    }

    /**
     * Creates the legacy Logica SMPP simulator bean.
     *
     * <p>This is the original SMPP implementation using Logica SMPP 3.1.3.</p>
     *
     * <p><b>Features:</b></p>
     * <ul>
     *   <li>Battle-tested in production</li>
     *   <li>Supports all existing configurations</li>
     *   <li>Compatible with all current SMPP connections</li>
     *   <li>Proven performance (300+ msg/s)</li>
     * </ul>
     *
     * <p><b>Known Limitations:</b></p>
     * <ul>
     *   <li>No longer actively maintained (Logica library EOL)</li>
     *   <li>Blocking I/O (less efficient than Cloudhopper's NIO)</li>
     *   <li>Limited scalability compared to Cloudhopper</li>
     * </ul>
     *
     * @param envConfig Environment configuration
     * @param messagesCache Shared message cache service
     * @return Configured SMPPSimulator instance (Logica)
     */
    @Bean(name = "smppSimulator")
    public SMPPSimulator logicaSimulator(
            EnvConfiguration envConfig,
            MessagesCache messagesCache) {
        log.info("Creating Logica SMPPSimulator bean (Legacy implementation)");
        log.info("  - Library: Logica SMPP 3.1.3");
        log.info("  - I/O Mode: Blocking");
        log.info("  - Tested Performance: 300+ msg/s");
        log.info("  - Environment: {}", envConfig.getEnvCurrent());

        SMPPSimulator simulator = new SMPPSimulator(envConfig, messagesCache);
        log.info("Logica SMPP Simulator created successfully");

        return simulator;
    }

    /**
     * Logs a warning if running in production with Logica.
     *
     * <p>This method checks the environment and warns if production systems
     * are still using the legacy Logica implementation.</p>
     */
    @PostConstruct
    public void checkProductionUsage() {
        String env = System.getProperty("spring.profiles.active", "unknown");

        if (env.toLowerCase().contains("prod") || env.toLowerCase().contains("production")) {
            log.warn("╔═══════════════════════════════════════════════════════════════════╗");
            log.warn("║  WARNING: Production environment using LEGACY Logica SMPP        ║");
            log.warn("║                                                                   ║");
            log.warn("║  Consider migrating to Cloudhopper for:                          ║");
            log.warn("║    - Active maintenance and security updates                     ║");
            log.warn("║    - Better performance (non-blocking I/O)                       ║");
            log.warn("║    - Higher scalability (1000+ connections)                      ║");
            log.warn("║                                                                   ║");
            log.warn("║  To enable: cloudhopper.enabled=true                             ║");
            log.warn("║  Instant rollback: cloudhopper.enabled=false                     ║");
            log.warn("╚═══════════════════════════════════════════════════════════════════╝");
        }
    }
}
