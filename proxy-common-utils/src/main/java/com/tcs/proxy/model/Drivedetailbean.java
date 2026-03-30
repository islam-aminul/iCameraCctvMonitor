package com.tcs.proxy.model;

import java.io.Serializable;

/**
 * Drive details as reported by the iCamera Proxy.
 * Carried inside SystemMetrics.diskDetailsList but intentionally not used
 * by the monitor — OSHI provides authoritative local disk information.
 */
public class Drivedetailbean implements Serializable {

    private static final long serialVersionUID = 1L;

    private String driveName;
    private long totalSpaceMb;
    private long freeSpaceMb;

    public Drivedetailbean() {}

    public Drivedetailbean(String driveName, long totalSpaceMb, long freeSpaceMb) {
        this.driveName = driveName;
        this.totalSpaceMb = totalSpaceMb;
        this.freeSpaceMb = freeSpaceMb;
    }

    public String getDriveName() { return driveName; }
    public void setDriveName(String driveName) { this.driveName = driveName; }

    public long getTotalSpaceMb() { return totalSpaceMb; }
    public void setTotalSpaceMb(long totalSpaceMb) { this.totalSpaceMb = totalSpaceMb; }

    public long getFreeSpaceMb() { return freeSpaceMb; }
    public void setFreeSpaceMb(long freeSpaceMb) { this.freeSpaceMb = freeSpaceMb; }
}
