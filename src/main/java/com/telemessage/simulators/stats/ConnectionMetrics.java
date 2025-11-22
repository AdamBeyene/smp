package com.telemessage.simulators.stats;

import lombok.Data;

/**
 * Metrics for connection monitoring in V2 pages.
 */
@Data
public class ConnectionMetrics {
    public int totalConnections;
    public int activeConnections;
    public int inactiveConnections;
    public double messagesPerSecond;
    public double averageLatency;
    public int smppConnections;
    public int httpConnections;
    public int boundTransmitters;
    public int boundReceivers;
    public int boundTransceivers;
    public long uptime;
    public long lastActivity;
}