package com.tcs.ion.iCamera.cctv.model;

import java.time.Instant;

/**
 * Holds all data retrieved for a single iCamera Proxy instance from the JMX MBean.
 */
public class ProxyData {

    private int proxyId;
    private String proxyName;
    private String tcCode;
    private String status;           // "UP" | "DOWN" | "UNKNOWN"
    private String downReason;
    private long startTimeMillis;    // epoch ms
    private long uptimeMillis;

    // From Windows Service Manager
    private String serviceStatus;    // RUNNING | STOPPED | NOT_FOUND | UNKNOWN
    private int serviceExitCode;

    // Resource utilization (from JMX)
    private double processCpuPercent;
    private double processMemoryMb;

    // MAC address details (from JMX / OS)
    private String currentMacAddress;
    private String lastMacAddress;

    // HSQLDB
    private String hsqldbStatus;     // "UP" | "DOWN" | "UNKNOWN"
    private long hsqldbStartTimeMillis;

    // Snapshot metadata
    private Instant snapshotTime;
    private boolean stale;

    public ProxyData() {
        this.snapshotTime = Instant.now();
        this.stale = false;
    }

    // ---- Getters & Setters ----

    public int getProxyId() { return proxyId; }
    public void setProxyId(int proxyId) { this.proxyId = proxyId; }

    public String getProxyName() { return proxyName; }
    public void setProxyName(String proxyName) { this.proxyName = proxyName; }

    public String getTcCode() { return tcCode; }
    public void setTcCode(String tcCode) { this.tcCode = tcCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDownReason() { return downReason; }
    public void setDownReason(String downReason) { this.downReason = downReason; }

    public long getStartTimeMillis() { return startTimeMillis; }
    public void setStartTimeMillis(long startTimeMillis) { this.startTimeMillis = startTimeMillis; }

    public long getUptimeMillis() { return uptimeMillis; }
    public void setUptimeMillis(long uptimeMillis) { this.uptimeMillis = uptimeMillis; }

    public String getServiceStatus() { return serviceStatus; }
    public void setServiceStatus(String serviceStatus) { this.serviceStatus = serviceStatus; }

    public int getServiceExitCode() { return serviceExitCode; }
    public void setServiceExitCode(int serviceExitCode) { this.serviceExitCode = serviceExitCode; }

    public double getProcessCpuPercent() { return processCpuPercent; }
    public void setProcessCpuPercent(double processCpuPercent) { this.processCpuPercent = processCpuPercent; }

    public double getProcessMemoryMb() { return processMemoryMb; }
    public void setProcessMemoryMb(double processMemoryMb) { this.processMemoryMb = processMemoryMb; }

    public String getCurrentMacAddress() { return currentMacAddress; }
    public void setCurrentMacAddress(String currentMacAddress) { this.currentMacAddress = currentMacAddress; }

    public String getLastMacAddress() { return lastMacAddress; }
    public void setLastMacAddress(String lastMacAddress) { this.lastMacAddress = lastMacAddress; }

    public boolean isMacMismatch() {
        if (currentMacAddress == null || lastMacAddress == null) return false;
        return !currentMacAddress.equalsIgnoreCase(lastMacAddress);
    }

    public String getHsqldbStatus() { return hsqldbStatus; }
    public void setHsqldbStatus(String hsqldbStatus) { this.hsqldbStatus = hsqldbStatus; }

    public long getHsqldbStartTimeMillis() { return hsqldbStartTimeMillis; }
    public void setHsqldbStartTimeMillis(long hsqldbStartTimeMillis) { this.hsqldbStartTimeMillis = hsqldbStartTimeMillis; }

    public Instant getSnapshotTime() { return snapshotTime; }
    public void setSnapshotTime(Instant snapshotTime) { this.snapshotTime = snapshotTime; }

    public boolean isStale() { return stale; }
    public void setStale(boolean stale) { this.stale = stale; }

    /**
     * Returns human-readable uptime string.
     */
    public String getUptimeString() {
        long totalSeconds = uptimeMillis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (days > 0) return String.format("%dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        if (hours > 0) return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        return String.format("%02dm %02ds", minutes, seconds);
    }
}
