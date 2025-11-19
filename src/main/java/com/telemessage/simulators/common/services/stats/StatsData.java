package com.telemessage.simulators.common.services.stats;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class StatsData {
    private final LocalDateTime timestamp;
    private final double cpuMax;
    private final double memoryMax;
    private final double cpuLoad;
    private final long usedMemory;
    private final long uptime;
}
