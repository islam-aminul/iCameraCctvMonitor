package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared in-memory data store.  All services read from / write to this singleton.
 * Thread-safe via concurrent collections.
 */
public class DataStore {

    private static final Logger log = LoggerFactory.getLogger(DataStore.class);
    private static final DataStore INSTANCE = new DataStore();

    // --- Proxy / System ---
    private volatile ProxyData proxyData;
    private volatile SystemMetrics systemMetrics;

    // --- CCTV cameras (keyed by CCTV ID) ---
    private final ConcurrentHashMap<Integer, CctvData> cctvMap = new ConcurrentHashMap<>();

    // --- Network speed history (last 10 readings) ---
    private final CopyOnWriteArrayList<NetworkDataPoint> networkHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_NETWORK_HISTORY = 10;

    // --- Alerts (append-only ring buffer of last 500) ---
    private final CopyOnWriteArrayList<AlertData> alerts = new CopyOnWriteArrayList<>();
    private static final int MAX_ALERTS = 500;

    // --- Settings ---
    private volatile AppSettings settings = new AppSettings();

    // --- JMX connection info ---
    private volatile String activeJmxUrl;
    private volatile boolean jmxConnected = false;

    // --- Last successful poll time ---
    private volatile Instant lastPollTime;

    // --- URL connectivity & SSL check results ---
    private final CopyOnWriteArrayList<com.tcs.ion.iCamera.cctv.model.UrlCheckResult> urlCheckResults =
            new CopyOnWriteArrayList<>();

    // --- VMS Detection ---
    private final CopyOnWriteArrayList<VmsInfo> vmsData = new CopyOnWriteArrayList<>();
    private volatile Instant lastVmsScanTime;
    // Tracks alert keys already raised to avoid duplicates
    private final java.util.Set<String> raisedAlertKeys =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    private DataStore() {}

    public static DataStore getInstance() { return INSTANCE; }

    // ---- Proxy & System ----

    public ProxyData getProxyData() { return proxyData; }

    public void updateProxyData(ProxyData pd) {
        pd.setSnapshotTime(Instant.now());
        pd.setStale(false);
        this.proxyData = pd;
        this.lastPollTime = Instant.now();
        log.debug("ProxyData updated: proxyId={}", pd.getProxyId());
    }

    public SystemMetrics getSystemMetrics() { return systemMetrics; }

    public void updateSystemMetrics(SystemMetrics sm) {
        sm.setSnapshotTime(Instant.now());
        sm.setStale(false);
        sm.evaluateHealth();
        this.systemMetrics = sm;
    }

    // ---- CCTV ----

    public void updateCctvData(CctvData cctv) {
        cctv.setSnapshotTime(Instant.now());
        cctv.evaluateStatus();
        cctvMap.put(cctv.getCctvId(), cctv);
    }

    public Collection<CctvData> getAllCctv() { return cctvMap.values(); }

    public int getTotalCctvCount() { return cctvMap.size(); }

    public long getActiveCctvCount() {
        return cctvMap.values().stream().filter(CctvData::isActive).count();
    }

    public long getInactiveCctvCount() { return getTotalCctvCount() - getActiveCctvCount(); }

    // ---- Network history ----

    public void addNetworkDataPoint(NetworkDataPoint pt) {
        networkHistory.add(pt);
        while (networkHistory.size() > MAX_NETWORK_HISTORY) {
            networkHistory.remove(0);
        }
    }

    public List<NetworkDataPoint> getNetworkHistory() {
        return Collections.unmodifiableList(networkHistory);
    }

    // ---- Alerts ----

    public void addAlert(AlertData alert) {
        alerts.add(alert);
        while (alerts.size() > MAX_ALERTS) {
            alerts.remove(0);
        }
        log.warn("[ALERT][{}] {} - {}", alert.getSeverity(), alert.getSource(), alert.getMessage());
    }

    public List<AlertData> getAlerts() {
        List<AlertData> copy = new ArrayList<>(alerts);
        Collections.sort(copy, (a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        return copy;
    }

    public List<AlertData> getUnresolvedAlerts() {
        List<AlertData> result = new ArrayList<>();
        for (AlertData a : alerts) {
            if (!a.isResolved()) result.add(a);
        }
        result.sort((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()));
        return result;
    }

    public void resolveAlert(String id) {
        for (AlertData a : alerts) {
            if (a.getId().equals(id)) { a.setResolved(true); break; }
        }
    }

    public void clearResolvedAlerts() {
        alerts.removeIf(AlertData::isResolved);
    }

    // ---- Settings ----

    public AppSettings getSettings() { return settings; }
    public void setSettings(AppSettings settings) { this.settings = settings; }

    // ---- JMX info ----

    public String getActiveJmxUrl() { return activeJmxUrl; }
    public void setActiveJmxUrl(String url) { this.activeJmxUrl = url; }

    public boolean isJmxConnected() { return jmxConnected; }
    public void setJmxConnected(boolean connected) { this.jmxConnected = connected; }

    // ---- Stale marking ----

    /**
     * Mark all data as stale if age exceeds 2 * pollIntervalSeconds.
     */
    public void markStaleIfExpired() {
        long staleThresholdMs = settings.getPollIntervalSeconds() * 2 * 1000L;
        Instant now = Instant.now();

        if (proxyData != null) {
            long ageMs = now.toEpochMilli() - proxyData.getSnapshotTime().toEpochMilli();
            if (ageMs > staleThresholdMs) proxyData.setStale(true);
        }
        if (systemMetrics != null) {
            long ageMs = now.toEpochMilli() - systemMetrics.getSnapshotTime().toEpochMilli();
            if (ageMs > staleThresholdMs) systemMetrics.setStale(true);
        }
        for (CctvData c : cctvMap.values()) {
            long ageMs = now.toEpochMilli() - c.getSnapshotTime().toEpochMilli();
            if (ageMs > staleThresholdMs) c.setStale(true);
        }
    }

    public Instant getLastPollTime() { return lastPollTime; }

    // ---- URL connectivity & SSL ----

    public void updateUrlCheckResults(List<com.tcs.ion.iCamera.cctv.model.UrlCheckResult> results) {
        urlCheckResults.clear();
        urlCheckResults.addAll(results);
    }

    public List<com.tcs.ion.iCamera.cctv.model.UrlCheckResult> getUrlCheckResults() {
        return Collections.unmodifiableList(urlCheckResults);
    }

    // ---- VMS Detection ----

    public void updateVmsData(List<VmsInfo> data) {
        vmsData.clear();
        vmsData.addAll(data);
        lastVmsScanTime = Instant.now();
    }

    public List<VmsInfo> getVmsData() {
        return Collections.unmodifiableList(vmsData);
    }

    public Instant getLastVmsScanTime() { return lastVmsScanTime; }

    /** Signals that VmsDetectionService should run a fresh scan on next scheduler tick. */
    private volatile boolean vmsRescanRequested = false;
    public void triggerVmsRescan() { vmsRescanRequested = true; }
    public boolean isVmsRescanRequested() { return vmsRescanRequested; }
    public void clearVmsRescanFlag() { vmsRescanRequested = false; }

    /**
     * Adds an alert only if the given key has not been raised before.
     * Used by VmsDetectionService to avoid duplicate VMS alerts.
     */
    public void addAlertIfNew(String key, AlertData alert) {
        if (raisedAlertKeys.add(key)) {
            addAlert(alert);
        }
    }

    public void clearAlertKey(String key) { raisedAlertKeys.remove(key); }
}
