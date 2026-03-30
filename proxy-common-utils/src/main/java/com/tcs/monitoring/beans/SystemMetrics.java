package com.tcs.monitoring.beans;

import com.tcs.proxy.model.Drivedetailbean;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Holds system-level metrics exposed by the iCamera Proxy MBean.
 * Fields are direct public members (no getters) to match the production class layout.
 *
 * Note: diskDetailsList is carried in this bean but the monitor deliberately
 * ignores it — OSHI is the authoritative source for local disk information.
 */
public class SystemMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    public double totalCpu;
    public double processCpu;
    public double totalRam;
    public double processRam;
    public double freeRam;
    public double fileUploadSpeed;

    /** Populated by the proxy but intentionally not used by the monitor (OSHI preferred). */
    public ArrayList<Drivedetailbean> diskDetailsList = new ArrayList<>();
}
