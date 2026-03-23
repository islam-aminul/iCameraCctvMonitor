package com.tcs.ion.iCamera.cctv.model;

/**
 * Snapshot of a single OS process for the "top-N" process views.
 */
public class ProcessInfo {

    private int    pid;
    private String name;
    private String path;
    private double cpuPercent;
    private long   memoryBytes;
    private long   diskReadBytesPerSec;
    private long   diskWriteBytesPerSec;
    private String user;

    public ProcessInfo() {}

    public ProcessInfo(int pid, String name, double cpuPercent, long memoryBytes) {
        this.pid = pid;
        this.name = name;
        this.cpuPercent = cpuPercent;
        this.memoryBytes = memoryBytes;
    }

    // ---- Computed display helpers ----

    public String getMemoryDisplay() {
        if (memoryBytes >= 1024L * 1024 * 1024) {
            return String.format("%.1f GB", memoryBytes / (1024.0 * 1024 * 1024));
        }
        return String.format("%.0f MB", memoryBytes / (1024.0 * 1024));
    }

    public String getDiskReadDisplay() {
        return formatBps(diskReadBytesPerSec);
    }

    public String getDiskWriteDisplay() {
        return formatBps(diskWriteBytesPerSec);
    }

    private String formatBps(long bps) {
        if (bps >= 1024 * 1024) return String.format("%.1f MB/s", bps / (1024.0 * 1024));
        if (bps >= 1024)        return String.format("%.1f KB/s", bps / 1024.0);
        return bps + " B/s";
    }

    // ---- Getters & Setters ----

    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public double getCpuPercent() { return cpuPercent; }
    public void setCpuPercent(double cpuPercent) { this.cpuPercent = cpuPercent; }

    public long getMemoryBytes() { return memoryBytes; }
    public void setMemoryBytes(long memoryBytes) { this.memoryBytes = memoryBytes; }

    public long getDiskReadBytesPerSec() { return diskReadBytesPerSec; }
    public void setDiskReadBytesPerSec(long v) { this.diskReadBytesPerSec = v; }

    public long getDiskWriteBytesPerSec() { return diskWriteBytesPerSec; }
    public void setDiskWriteBytesPerSec(long v) { this.diskWriteBytesPerSec = v; }

    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
}
