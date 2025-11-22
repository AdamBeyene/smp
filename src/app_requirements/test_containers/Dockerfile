# Runtime-only image - uses pre-built JAR from host's target/ directory
FROM eclipse-temurin:21-jre-jammy

# Install curl for healthcheck
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy pre-built JAR from host's target directory (built by build-and-run.bat)
# The batch script runs 'mvn clean package' on the host before Docker build
# IMPORTANT: Copy specific file to avoid copying .jar.original (thin JAR without dependencies)
COPY target/TM_SMPP_SIM.jar app.jar

# Create directories for config files and logs
RUN mkdir -p /app/config /app/shared/sim/logs/smpp

# Expose ports
# Web UI ports
EXPOSE 8021
# Additional port (application specific)
EXPOSE 9001
# SMPP ports for ESME → SMSC connections
EXPOSE 2775 2776 2777 2778 2779 2780
# SMPP ports for SMSC → SMSC peer routing
EXPOSE 2781 2782 2783 2784 2785 2786
# SMPP ports for Hybrid connections (mixed ESME/SMSC)
EXPOSE 2790 2791

# Set JVM options with verbose logging and non-blocking entropy source
# -Djava.security.egd=file:/dev/./urandom prevents blocking on /dev/random during startup
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8020/actuator/health || exit 1

# Run the application
# Standard Spring Boot JAR execution
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
