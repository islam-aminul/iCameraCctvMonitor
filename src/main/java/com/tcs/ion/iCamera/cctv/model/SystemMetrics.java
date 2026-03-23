package com.tcs.ion.iCamera.cctv.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * System-level hardware and OS metrics, combined from JMX and OSHI.
 */
public class SystemMetrics {

    private double systemCpuPercent;
    private double totalMemoryMb;
    private double freeMemoryMb;
    private double networkSpeedMbps; // upload speed from JMX

    // Drive details from JMX / OSHI
    private List<DriveInfo> drives = new ArrayList<>();

    // OSHI hardware details
    private String cpuName;
    private int physicalCores;
    private int logicalCores;
    private double cpuMaxFreqGhz;
    private long totalRamBytes;
    private long availableRamBytes;
    private String ramType;         // DDR4, etc.

    // Health status
    private boolean healthy;        // false if any metric exceeds 85%

    private Instant snapshotTime;
    private boolean stale;

    private static final double ALERT_THRESHOLD = 85.0;

    public SystemMetrics() {
        this.snapshotTime = Instant.now();
    }

    public void evaluateHealth() {
        double memUsedPercent = totalMemoryMb > 0
                ? ((totalMemoryMb - freeMemoryMb) / totalMemoryMb) * 100.0
                : 0;
        healthy = systemCpuPercent < ALERT_THRESHOLD && memUsedPercent < ALERT_THRESHOLD;
        for (DriveInfo d : drives) {
            if (d.getUsedPercent() >= ALERT_THRESHOLD) { healthy = false; break; }
        }
    }

    public double getMemoryUsedPercent() {
        if (totalMemoryMb <= 0) return 0;
        return ((totalMemoryMb - freeMemoryMb) / totalMemoryMb) * 100.0;
    }

    // ---- Inner class ----

    public static class DriveInfo {
        private String name;
        private long totalSpaceMb;
        private long freeSpaceMb;
        private String rpm; // "SSD" or RPM number string

        public DriveInfo() {}
        public DriveInfo(String name, long totalSpaceMb, long freeSpaceMb) {
            this.name = name;
            this.totalSpaceMb = totalSpaceMb;
            this.freeSpaceMb = freeSpaceMb;
        }

        public double getUsedPercent() {
            if (totalSpaceMb <= 0) return 0;
            return ((totalSpaceMb - freeSpaceMb) / (double) totalSpaceMb) * 100.0;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getTotalSpaceMb() { return totalSpaceMb; }
        public void setTotalSpaceMb(long totalSpaceMb) { this.totalSpaceMb = totalSpaceMb; }
        public long getFreeSpaceMb() { return freeSpaceMb; }
        public void setFreeSpaceMb(long freeSpaceMb) { this.freeSpaceMb = freeSpaceMb; }
        public String getRpm() { return rpm; }
        public void setRpm(String rpm) { this.rpm = rpm; }
    }

    // ---- Getters & Setters ----

    public double getSystemCpuPercent() { return systemCpuPercent; }
    public void setSystemCpuPercent(double systemCpuPercent) { this.systemCpuPercent = systemCpuPercent; }

    public double getTotalMemoryMb() { return totalMemoryMb; }
    public void setTotalMemoryMb(double totalMemoryMb) { this.totalMemoryMb = totalMemoryMb; }

    public double getFreeMemoryMb() { return freeMemoryMb; }
    public void setFreeMemoryMb(double freeMemoryMb) { this.freeMemoryMb = freeMemoryMb; }

    public double getNetworkSpeedMbps() { return networkSpeedMbps; }
    public void setNetworkSpeedMbps(double networkSpeedMbps) { this.networkSpeedMbps = networkSpeedMbps; }

    public List<DriveInfo> getDrives() { return drives; }
    public void setDrives(List<DriveInfo> drives) { this.drives = drives; }

    public String getCpuName() { return cpuName; }
    public void setCpuName(String cpuName) { this.cpuName = cpuName; }

    public int getPhysicalCores() { return physicalCores; }
    public void setPhysicalCores(int physicalCores) { this.physicalCores = physicalCores; }

    public int getLogicalCores() { return logicalCores; }
    public void setLogicalCores(int logicalCores) { this.logicalCores = logicalCores; }

    public double getCpuMaxFreqGhz() { return cpuMaxFreqGhz; }
    public void setCpuMaxFreqGhz(double cpuMaxFreqGhz) { this.cpuMaxFreqGhz = cpuMaxFreqGhz; }

    public long getTotalRamBytes() { return totalRamBytes; }
    public void setTotalRamBytes(long totalRamBytes) { this.totalRamBytes = totalRamBytes; }

    public long getAvailableRamBytes() { return availableRamBytes; }
    public void setAvailableRamBytes(long availableRamBytes) { this.availableRamBytes = availableRamBytes; }

    public String getRamType() { return ramType; }
    public void setRamType(String ramType) { this.ramType = ramType; }

    public boolean isHealthy() { return healthy; }

    public Instant getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(Instant snapshotTime) { this.snapshotTime = snapshotTime; }

    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }
}
