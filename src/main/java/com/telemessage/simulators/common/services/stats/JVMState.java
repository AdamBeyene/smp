package com.telemessage.simulators.common.services.stats;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

import java.util.HashMap;
import java.util.Map;

public class JVMState {

    static SystemInfo systemInfo = new SystemInfo();
    static HardwareAbstractionLayer hal = systemInfo.getHardware();
    static Runtime runtime = Runtime.getRuntime();


    public static Map<String, String> getJvmStates() {
        Map<String, String> jvmStates = new HashMap<>();
        try {
            // Heap
            long heapUsed = runtime.totalMemory() - runtime.freeMemory();
            long heapAvailable = runtime.freeMemory();

            // Total physical memory
            GlobalMemory memory = hal.getMemory();
            long totalPhysicalMemory = memory.getTotal();
            long usedPhysicalMemory = memory.getTotal() - memory.getAvailable();

            // Total CPU cores
            CentralProcessor processor = hal.getProcessor();
            int totalCpuCores = processor.getLogicalProcessorCount();

            // CPU usage
            double cpuUsage = processor.getSystemCpuLoad(1000);
            int availableCpuCores = (int) Math.round(totalCpuCores * (1 - cpuUsage));

            jvmStates.put("Heap Memory Used", bytesToMB(heapUsed) + " MB");
            jvmStates.put("Heap Memory Available", bytesToMB(heapAvailable) + " MB");
            jvmStates.put("Memory Total", bytesToMB(totalPhysicalMemory) + " MB");
            jvmStates.put("Memory Used", bytesToMB(usedPhysicalMemory) + " MB");
            jvmStates.put("CPU Total Cores", String.valueOf(totalCpuCores));
            jvmStates.put("CPU Usage", String.format("%.2f%%", cpuUsage * 100));
            jvmStates.put("CPU Available Cores", String.valueOf(availableCpuCores));
        } catch (Exception e) {
            // Log the exception (you can use a logging framework like SLF4J)
            System.err.println("Error retrieving JVM states: " + e.getMessage());
//            e.printStackTrace();
        }
        return jvmStates;
    }

    private static String bytesToMB(long bytes) {
        return String.valueOf(bytes / (1024 * 1024));
    }

}
