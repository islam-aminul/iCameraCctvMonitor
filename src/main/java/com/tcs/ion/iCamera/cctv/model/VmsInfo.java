package com.tcs.ion.iCamera.cctv.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a detected Video Management Software (VMS) installation / running process.
 */
public class VmsInfo {

    public enum VmsVendor {
        MILESTONE("Milestone XProtect"),
        HIKVISION("Hikvision iVMS / HikCentral"),
        DAHUA("Dahua DSS / SmartPSS"),
        AVIGILON("Avigilon Control Center"),
        GENETEC("Genetec Security Center"),
        AXIS("Axis Camera Station"),
        BOSCH("Bosch BVMS"),
        HANWHA("Hanwha Wisenet SSM"),
        PELCO("Pelco VideoXpert / Digital Sentry"),
        EXACQ("Exacq Vision"),
        NUUO("NUUO Crystal"),
        DIGIFORT("Digifort"),
        IVMS("iVMS (Generic)"),
        UNKNOWN("Unknown VMS");

        private final String displayName;
        VmsVendor(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }

    private VmsVendor vendor;
    private String processName;
    private int pid;
    private double cpuPercent;
    private long memoryMb;
    private String status;        // "RUNNING" | "DETECTED" (found in services but not running)
    private String version;       // if detectable
    private String installPath;
    private Instant detectedAt;
    private boolean running;

    public VmsInfo() {
        this.detectedAt = Instant.now();
    }

    // ---- Getters & Setters ----

    public VmsVendor getVendor() { return vendor; }
    public void setVendor(VmsVendor vendor) { this.vendor = vendor; }

    public String getVendorDisplayName() {
        return vendor != null ? vendor.getDisplayName() : "Unknown";
    }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public int getPid() { return pid; }
    public void setPid(int pid) { this.pid = pid; }

    public double getCpuPercent() { return cpuPercent; }
    public void setCpuPercent(double cpuPercent) { this.cpuPercent = cpuPercent; }

    public long getMemoryMb() { return memoryMb; }
    public void setMemoryMb(long memoryMb) { this.memoryMb = memoryMb; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getInstallPath() { return installPath; }
    public void setInstallPath(String installPath) { this.installPath = installPath; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public boolean isRunning() { return running; }
    public void setRunning(boolean running) { this.running = running; }
}
