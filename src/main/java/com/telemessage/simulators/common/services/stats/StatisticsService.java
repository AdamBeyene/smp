package com.telemessage.simulators.common.services.stats;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.List;

@Service
public class StatisticsService {

    private final List<StatsData> statsHistory = new LinkedList<>();


    public void recordStats(StatsData.StatsDataBuilder statsData) {
        statsHistory.add(statsData.build());
        if (statsHistory.size() > 1000) { // Limit history size
            statsHistory.removeFirst();
        }
    }

    public List<StatsData> getStatsHistory() {
        return new LinkedList<>(statsHistory);
    }

    /*static SystemInfo systemInfo = new SystemInfo();
    static HardwareAbstractionLayer hal = systemInfo.getHardware();
    static Runtime runtime = Runtime.getRuntime();
    CentralProcessor processor = hal.getProcessor();*/

    @Scheduled(fixedRate = 6000) // Every minute
    public void captureStats() {
         SystemInfo systemInfo = new SystemInfo();
         HardwareAbstractionLayer hal = systemInfo.getHardware();
         Runtime runtime = Runtime.getRuntime();
        CentralProcessor processor = hal.getProcessor();

        GlobalMemory globalMemory = hal.getMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory =  totalMemory- freeMemory;
        long totalCpuCores = Runtime.getRuntime().availableProcessors();
        double cpuUsage =  processor.getSystemCpuLoad(1000);
        double availableCpuCores =  (int) Math.round(totalCpuCores * (1 - cpuUsage));

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        recordStats(StatsData.builder()
                .timestamp(LocalDateTime.now())
                .memoryMax(totalMemory)
                .cpuLoad(cpuUsage)
                .cpuMax(totalCpuCores)
                .usedMemory(usedMemory)
                .uptime(uptime));
    }
}

