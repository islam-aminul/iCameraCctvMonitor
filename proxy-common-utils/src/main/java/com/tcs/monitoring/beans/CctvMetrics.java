package com.tcs.monitoring.beans;

import com.tcs.proxy.database.api.tables.ResourceFieldValues;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds per-camera metrics exposed by the iCamera Proxy MBean.
 * Fields are direct public members (no getters) to match the production class layout.
 */
public class CctvMetrics implements Serializable {

    private static final long serialVersionUID = 1L;

    /** CCTV ID → resource record fields (name, RTSP URL, etc.) */
    public Map<Long, ResourceFieldValues> startHitMap = new HashMap<>();

    /** CCTV ID → reachability flag */
    public Map<Long, Boolean> cctvStatusMap = new HashMap<>();

    /** CCTV ID → epoch-millis of last file modification on the proxy */
    public Map<Long, Long> cctvFileLastModifiedMap = new HashMap<>();

    /** CCTV ID → epoch-millis of last file upload from the proxy */
    public Map<Long, Long> cctvFileLastUploadedMap = new HashMap<>();
}
