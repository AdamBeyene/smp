package com.telemessage.simulators.stats;

import lombok.Data;
import java.util.Map;

/**
 * Metrics for messaging monitoring in V2 pages.
 */
@Data
public class MessagingMetrics {
    public int totalMessages;
    public int inboundMessages;
    public int outboundMessages;
    public int deliveryReceipts;
    public int failedMessages;
    public Map<String, Integer> encodingDistribution;
    public Map<String, Integer> providerDistribution;
    public double averageMessageSize;
    public int concatenatedMessages;
    public int binaryMessages;
    public long oldestMessageTime;
    public long newestMessageTime;
}