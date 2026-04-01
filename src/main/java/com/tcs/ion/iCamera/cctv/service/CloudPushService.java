package com.tcs.ion.iCamera.cctv.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tcs.ion.iCamera.cctv.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodically pushes a consolidated monitoring snapshot to the Cloud Data Center.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code POST /iCAMERAStreamingFW/api/v1/monitor/heartbeat} — periodic full snapshot (default every 60 s)</li>
 *   <li>{@code POST /iCAMERAStreamingFW/api/v1/monitor/alert}     — immediate push for CRITICAL alerts</li>
 * </ul>
 *
 * <h3>Retry strategy:</h3>
 * Exponential backoff with jitter on failure: 30 s → 60 s → 120 s → 300 s (cap).
 * Backoff resets on a successful push.
 *
 * <h3>Configuration ({@code application.properties}):</h3>
 * <pre>
 *   cloud.push.enabled=true
 *   cloud.push.interval.seconds=300
 *   cloud.auth.token=&lt;provisioned_token&gt;
 *   cloud.dc.host=g01.tcsion.com
 * </pre>
 */
public class CloudPushService {

    private static final Logger log = LoggerFactory.getLogger(CloudPushService.class);

    private static final String SCHEMA_VERSION = "1.0";
    private static final String AGENT_VERSION  = "2.1.0";

    private static final String HEARTBEAT_PATH = "/iCAMERAStreamingFW/api/v1/monitor/heartbeat";
    private static final String ALERT_PATH     = "/iCAMERAStreamingFW/api/v1/monitor/alert";

    private static final int    MAX_BACKOFF_SECONDS = 300;
    private static final int    INITIAL_BACKOFF_SECONDS = 30;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .create();

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final DataStore   store = DataStore.getInstance();
    private final HttpService httpService;

    // Backoff state
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong    lastPushAttemptMs    = new AtomicLong(0);

    // Server-directed push interval override (0 = use default)
    private volatile int serverDirectedIntervalSeconds = 0;

    // Track last-pushed alert IDs to detect new CRITICAL alerts for immediate push
    private final Set<String> pushedCriticalAlertIds = Collections.synchronizedSet(new HashSet<>());

    public CloudPushService(HttpService httpService) {
        this.httpService = httpService;
    }

    // ---- Public API --------------------------------------------------------

    /**
     * Builds and pushes a full heartbeat snapshot.
     * Called by {@link SchedulerService} on the configured interval.
     */
    public void pushHeartbeat() {
        AppSettings cfg = store.getSettings();
        if (!cfg.isCloudPushEnabled()) {
            log.debug("Cloud push disabled – skipping heartbeat");
            return;
        }

        // Respect backoff on previous failures
        if (!shouldAttempt()) {
            log.debug("Cloud push skipped – in backoff period");
            return;
        }

        try {
            Map<String, Object> payload = buildFullPayload();
            String json = GSON.toJson(payload);

            // Guard payload size (warn if > 500 KB)
            if (json.length() > 500_000) {
                log.warn("Cloud push payload is large ({} bytes) – consider reducing networkHistory",
                        json.length());
            }

            String url = buildUrl(cfg, HEARTBEAT_PATH);
            Map<String, String> headers = buildHeaders(cfg);

            log.info("Pushing heartbeat to {} ({} bytes)", url, json.length());
            String response = httpService.postJson(url, payload, headers);

            if (response != null) {
                handleSuccessResponse(response);
                consecutiveFailures.set(0);
                log.info("Heartbeat push successful");
            } else {
                handleFailure("Null response from cloud");
            }

        } catch (Exception e) {
            handleFailure("Heartbeat push error: " + e.getMessage());
        } finally {
            lastPushAttemptMs.set(System.currentTimeMillis());
        }
    }

    /**
     * Checks for new CRITICAL alerts and pushes them immediately.
     * Called more frequently (e.g. every 10 s) to catch critical alerts fast.
     */
    public void pushCriticalAlertsIfAny() {
        AppSettings cfg = store.getSettings();
        if (!cfg.isCloudPushEnabled()) return;

        List<AlertData> unresolvedAlerts = store.getUnresolvedAlerts();
        List<AlertData> newCritical = new ArrayList<>();

        for (AlertData alert : unresolvedAlerts) {
            if (alert.getSeverity() == AlertData.Severity.CRITICAL
                    && !pushedCriticalAlertIds.contains(alert.getId())) {
                newCritical.add(alert);
            }
        }

        if (newCritical.isEmpty()) return;

        try {
            Map<String, Object> payload = buildAlertPayload(newCritical);
            String url = buildUrl(cfg, ALERT_PATH);
            Map<String, String> headers = buildHeaders(cfg);

            log.warn("Pushing {} critical alert(s) immediately", newCritical.size());
            String response = httpService.postJson(url, payload, headers);

            if (response != null) {
                for (AlertData a : newCritical) {
                    pushedCriticalAlertIds.add(a.getId());
                }
                log.info("Critical alert push successful ({} alerts)", newCritical.size());
            }

        } catch (Exception e) {
            log.error("Critical alert push failed: {}", e.getMessage());
        }
    }

    /**
     * Returns the effective push interval, considering server-directed overrides.
     */
    public int getEffectivePushIntervalSeconds() {
        if (serverDirectedIntervalSeconds > 0) {
            return serverDirectedIntervalSeconds;
        }
        return store.getSettings().getCloudPushIntervalSeconds();
    }

    // ---- Payload Builders --------------------------------------------------

    private Map<String, Object> buildFullPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();

        ProxyData pd = store.getProxyData();

        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("proxyId", pd != null ? pd.getProxyId() : 0);
        payload.put("orgId", pd != null ? pd.getOrgId() : 0);
        payload.put("tcCode", pd != null ? pd.getTcCode() : "N/A");
        payload.put("proxyName", pd != null ? pd.getProxyName() : "N/A");
        payload.put("agentVersion", AGENT_VERSION);
        payload.put("timestamp", ISO_UTC.format(Instant.now()));

        payload.put("proxy", buildProxySection(pd));
        payload.put("systemMetrics", buildSystemMetricsSection());
        payload.put("cameras", buildCamerasSection());
        payload.put("networkHistory", buildNetworkHistorySection());
        payload.put("alerts", buildAlertsSection());
        payload.put("connectivity", buildConnectivitySection());

        // VMS detection — only included when VMS software is actually detected
        List<Map<String, Object>> vmsData = buildVmsSection();
        if (!vmsData.isEmpty()) {
            payload.put("vmsDetected", true);
            payload.put("vmsDetection", vmsData);
        } else {
            payload.put("vmsDetected", false);
        }

        payload.put("macValidation", null); // on-demand, not included in periodic push

        return payload;
    }

    private Map<String, Object> buildAlertPayload(List<AlertData> criticalAlerts) {
        Map<String, Object> payload = new LinkedHashMap<>();

        ProxyData pd = store.getProxyData();

        payload.put("schemaVersion", SCHEMA_VERSION);
        payload.put("proxyId", pd != null ? pd.getProxyId() : 0);
        payload.put("orgId", pd != null ? pd.getOrgId() : 0);
        payload.put("tcCode", pd != null ? pd.getTcCode() : "N/A");
        payload.put("proxyName", pd != null ? pd.getProxyName() : "N/A");
        payload.put("agentVersion", AGENT_VERSION);
        payload.put("timestamp", ISO_UTC.format(Instant.now()));

        // Only alerts section populated for immediate push
        List<Map<String, Object>> alertsList = new ArrayList<>();
        for (AlertData a : criticalAlerts) {
            alertsList.add(buildAlertMap(a));
        }
        payload.put("alerts", alertsList);

        return payload;
    }

    // ---- Section Builders --------------------------------------------------

    private Map<String, Object> buildProxySection(ProxyData pd) {
        Map<String, Object> proxy = new LinkedHashMap<>();
        if (pd == null) {
            proxy.put("status", "UNKNOWN");
            return proxy;
        }

        proxy.put("proxyId", pd.getProxyId());
        proxy.put("orgId", pd.getOrgId());
        proxy.put("proxyName", pd.getProxyName());
        proxy.put("tcCode", pd.getTcCode());
        proxy.put("status", pd.getStatus());
        proxy.put("downReason", pd.getDownReason());
        proxy.put("startTimeUtc", pd.getStartTimeMillis() > 0
                ? ISO_UTC.format(Instant.ofEpochMilli(pd.getStartTimeMillis())) : null);
        proxy.put("uptimeSeconds", pd.getUptimeMillis() / 1000);
        proxy.put("serviceStatus", pd.getServiceStatus());
        proxy.put("processCpuPercent", round2(pd.getProcessCpuPercent()));
        proxy.put("processMemoryMb", round2(pd.getProcessMemoryMb()));
        proxy.put("currentMacAddress", pd.getCurrentMacAddress());
        proxy.put("macMismatch", pd.isMacMismatch());

        // HSQLDB sub-object
        Map<String, Object> hsqldb = new LinkedHashMap<>();
        hsqldb.put("serviceStatus", pd.getHsqldbStatus());
        hsqldb.put("jmxStatus", pd.getHsqldbJmxStatus());
        hsqldb.put("port", pd.getHsqldbPort());
        hsqldb.put("directlyReachable", pd.isHsqldbDirectlyReachable());
        hsqldb.put("startTimeUtc", pd.getHsqldbStartTimeMillis() > 0
                ? ISO_UTC.format(Instant.ofEpochMilli(pd.getHsqldbStartTimeMillis())) : null);
        proxy.put("hsqldb", hsqldb);

        proxy.put("snapshotTimeUtc", ISO_UTC.format(pd.getSnapshotTime()));
        proxy.put("stale", pd.isStale());

        return proxy;
    }

    private Map<String, Object> buildSystemMetricsSection() {
        SystemMetrics sm = store.getSystemMetrics();
        Map<String, Object> metrics = new LinkedHashMap<>();
        if (sm == null) {
            metrics.put("healthy", false);
            return metrics;
        }

        metrics.put("cpuPercent", round2(sm.getSystemCpuPercent()));
        metrics.put("memoryTotalMb", round2(sm.getTotalMemoryMb()));
        metrics.put("memoryFreeMb", round2(sm.getFreeMemoryMb()));
        metrics.put("memoryUsedPercent", round2(sm.getMemoryUsedPercent()));
        metrics.put("networkUploadMbps", round2(sm.getNetworkSpeedMbps()));
        metrics.put("healthy", sm.isHealthy());

        // Drives
        List<Map<String, Object>> drives = new ArrayList<>();
        for (SystemMetrics.DriveInfo di : sm.getDrives()) {
            Map<String, Object> drive = new LinkedHashMap<>();
            drive.put("name", di.getName());
            drive.put("totalMb", di.getTotalSpaceMb());
            drive.put("freeMb", di.getFreeSpaceMb());
            drive.put("usedPercent", round2(di.getUsedPercent()));
            drive.put("type", di.getRpm());
            drives.add(drive);
        }
        metrics.put("drives", drives);

        // Hardware profile (static info)
        Map<String, Object> hw = new LinkedHashMap<>();
        hw.put("cpuName", sm.getCpuName());
        hw.put("physicalCores", sm.getPhysicalCores());
        hw.put("logicalCores", sm.getLogicalCores());
        hw.put("cpuMaxFreqGhz", round2(sm.getCpuMaxFreqGhz()));
        hw.put("totalRamBytes", sm.getTotalRamBytes());
        hw.put("ramType", sm.getRamType());
        metrics.put("hardwareProfile", hw);

        metrics.put("snapshotTimeUtc", ISO_UTC.format(sm.getSnapshotTime()));
        metrics.put("stale", sm.isStale());

        return metrics;
    }

    private List<Map<String, Object>> buildCamerasSection() {
        List<Map<String, Object>> cameras = new ArrayList<>();
        for (CctvData cctv : store.getAllCctv()) {
            Map<String, Object> cam = new LinkedHashMap<>();
            cam.put("cctvId", cctv.getCctvId());
            cam.put("cctvName", cctv.getCctvName());
            cam.put("active", cctv.isActive());
            cam.put("inactiveReason", cctv.getInactiveReason());
            cam.put("reachable", cctv.isReachable());
            cam.put("lastFileGeneratedUtc", cctv.getLastFileModifiedMillis() > 0
                    ? ISO_UTC.format(Instant.ofEpochMilli(cctv.getLastFileModifiedMillis())) : null);
            cam.put("lastFileUploadedUtc", cctv.getLastFileUploadedMillis() > 0
                    ? ISO_UTC.format(Instant.ofEpochMilli(cctv.getLastFileUploadedMillis())) : null);

            // Stream analytics (excludes rtspUrl and ipAddress for security)
            Map<String, Object> stream = new LinkedHashMap<>();
            stream.put("encoding", cctv.getEncoding());
            stream.put("profile", cctv.getStreamProfile());
            stream.put("fps", round2(cctv.getFps()));
            stream.put("bitrateKbps", cctv.getBitrateKbps());
            stream.put("resolution", cctv.getResolution());
            stream.put("probeSuccess", cctv.isFfprobeSuccess());
            cam.put("streamAnalytics", stream);

            cam.put("snapshotTimeUtc", ISO_UTC.format(cctv.getSnapshotTime()));
            cam.put("stale", cctv.isStale());

            cameras.add(cam);
        }
        return cameras;
    }

    private List<Map<String, Object>> buildNetworkHistorySection() {
        List<Map<String, Object>> history = new ArrayList<>();
        for (NetworkDataPoint pt : store.getNetworkHistory()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("timestampUtc", ISO_UTC.format(pt.getTimestamp()));
            entry.put("uploadMbps", round2(pt.getUploadSpeedMbps()));
            history.add(entry);
        }
        return history;
    }

    private List<Map<String, Object>> buildAlertsSection() {
        List<Map<String, Object>> alertsList = new ArrayList<>();
        for (AlertData a : store.getUnresolvedAlerts()) {
            alertsList.add(buildAlertMap(a));
        }
        return alertsList;
    }

    private Map<String, Object> buildAlertMap(AlertData a) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("id", a.getId());
        alert.put("severity", a.getSeverity().name());
        alert.put("category", a.getCategory().name());
        alert.put("source", a.getSource());
        alert.put("parameter", a.getParameter());
        alert.put("message", a.getMessage());
        alert.put("currentValue", a.getCurrentValue());
        alert.put("threshold", a.getThreshold());
        alert.put("timestampUtc", ISO_UTC.format(a.getTimestamp()));
        alert.put("acknowledged", a.isAcknowledged());
        alert.put("resolved", a.isResolved());
        return alert;
    }

    private List<Map<String, Object>> buildConnectivitySection() {
        List<Map<String, Object>> results = new ArrayList<>();
        for (UrlCheckResult r : store.getUrlCheckResults()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("host", r.getHost());
            entry.put("reachable", r.isReachable());
            entry.put("httpStatus", r.getHttpStatus());

            if (r.isReachable() && r.isSslValid()) {
                Map<String, Object> ssl = new LinkedHashMap<>();
                ssl.put("valid", true);
                ssl.put("issuer", r.getSslIssuer());
                ssl.put("expiryDate", r.getSslExpiry() != null ? r.getSslExpiry().toString() : null);
                ssl.put("daysLeft", r.getSslDaysLeft());
                entry.put("ssl", ssl);
            } else if (r.isReachable()) {
                Map<String, Object> ssl = new LinkedHashMap<>();
                ssl.put("valid", false);
                ssl.put("error", r.getErrorMessage());
                entry.put("ssl", ssl);
            } else {
                entry.put("ssl", null);
            }

            entry.put("checkedAtUtc", ISO_UTC.format(r.getCheckedAt()));
            results.add(entry);
        }
        return results;
    }

    private List<Map<String, Object>> buildVmsSection() {
        List<Map<String, Object>> vmsList = new ArrayList<>();
        for (VmsInfo vms : store.getVmsData()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("vendor", vms.getVendor() != null ? vms.getVendor().name() : "UNKNOWN");
            entry.put("vendorDisplayName", vms.getVendorDisplayName());
            entry.put("processName", vms.getProcessName());
            entry.put("status", vms.getStatus());
            entry.put("running", vms.isRunning());
            entry.put("cpuPercent", round2(vms.getCpuPercent()));
            entry.put("memoryMb", vms.getMemoryMb());
            entry.put("detectedAtUtc", ISO_UTC.format(vms.getDetectedAt()));
            vmsList.add(entry);
        }
        return vmsList;
    }

    // ---- HTTP helpers ------------------------------------------------------

    private String buildUrl(AppSettings cfg, String path) {
        String host = cfg.getCloudDcHost();
        if (host == null || host.isEmpty()) host = "g01.tcsion.com";
        return "https://" + host + path;
    }

    private Map<String, String> buildHeaders(AppSettings cfg) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("X-Timestamp", ISO_UTC.format(Instant.now()));
        headers.put("X-Agent-Version", AGENT_VERSION);
        headers.put("User-Agent", "iCameraCctvMonitor/" + AGENT_VERSION);

        ProxyData pd = store.getProxyData();
        if (pd != null) {
            headers.put("X-Proxy-Id", String.valueOf(pd.getProxyId()));
        }

        String token = cfg.getCloudAuthToken();
        if (token != null && !token.isEmpty()) {
            headers.put("Authorization", "Bearer " + token);
        }

        return headers;
    }

    // ---- Backoff logic -----------------------------------------------------

    private boolean shouldAttempt() {
        int failures = consecutiveFailures.get();
        if (failures == 0) return true;

        int backoffSeconds = Math.min(INITIAL_BACKOFF_SECONDS * (1 << (failures - 1)), MAX_BACKOFF_SECONDS);
        // Add jitter: ±20% of backoff
        int jitter = (int) (backoffSeconds * 0.2 * (Math.random() * 2 - 1));
        int effectiveBackoff = backoffSeconds + jitter;

        long elapsed = System.currentTimeMillis() - lastPushAttemptMs.get();
        return elapsed >= effectiveBackoff * 1000L;
    }

    private void handleFailure(String reason) {
        int failures = consecutiveFailures.incrementAndGet();
        int backoff = Math.min(INITIAL_BACKOFF_SECONDS * (1 << (failures - 1)), MAX_BACKOFF_SECONDS);
        log.error("Cloud push failed (attempt {}): {} – next retry in ~{}s", failures, reason, backoff);
    }

    private void handleSuccessResponse(String responseBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = GSON.fromJson(responseBody, Map.class);
            if (resp == null) return;

            // Check for server-directed interval override
            Object nextInterval = resp.get("nextPushIntervalSeconds");
            if (nextInterval instanceof Number) {
                int interval = ((Number) nextInterval).intValue();
                if (interval > 0 && interval != serverDirectedIntervalSeconds) {
                    log.info("Server directed push interval change: {} -> {}s",
                            serverDirectedIntervalSeconds, interval);
                    serverDirectedIntervalSeconds = interval;
                }
            }

            // Check for directives
            Object dirObj = resp.get("directives");
            if (dirObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> directives = (Map<String, Object>) dirObj;

                if (Boolean.TRUE.equals(directives.get("runMacValidation"))) {
                    log.info("Server directive: run MAC validation");
                    // Future: trigger MacValidationService
                }
                if (Boolean.TRUE.equals(directives.get("forceVmsScan"))) {
                    log.info("Server directive: force VMS rescan");
                    store.triggerVmsRescan();
                }
            }

        } catch (Exception e) {
            log.debug("Could not parse cloud response: {}", e.getMessage());
        }
    }

    // ---- Utilities ---------------------------------------------------------

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
