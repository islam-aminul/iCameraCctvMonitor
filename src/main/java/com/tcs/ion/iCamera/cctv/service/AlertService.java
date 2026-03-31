package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Evaluates current DataStore snapshot and raises alerts for anomalous conditions.
 * Uses a "active alerts" set to avoid duplicate alerts for the same condition.
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private static final double ALERT_THRESHOLD = 85.0;

    private final DataStore store = DataStore.getInstance();
    // Track which conditions currently have an active alert (to avoid duplicates)
    private final Set<String> activeAlertKeys = new HashSet<>();

    public void evaluate() {
        evaluateProxy();
        evaluateSystem();
        evaluateCctv();
        evaluateNetwork();
        evaluateUrlChecks();
    }

    private void evaluateProxy() {
        ProxyData pd = store.getProxyData();
        if (pd == null) return;

        String status = pd.getStatus();

        // PROXY_DOWN: service is fully DOWN or UNKNOWN (not DEGRADED, not UP)
        raiseOrClear("PROXY_DOWN", "DOWN".equals(status) || "UNKNOWN".equals(status),
                AlertData.Severity.CRITICAL, AlertData.Category.PROXY,
                "Proxy-" + pd.getProxyId(), "PROXY_STATUS",
                "iCameraProxy is " + status + ". Reason: " + pd.getDownReason());

        // PROXY_DEGRADED: service is RUNNING but JMX is not reachable – degraded mode
        raiseOrClear("PROXY_DEGRADED", "DEGRADED".equals(status),
                AlertData.Severity.WARNING, AlertData.Category.PROXY,
                "Proxy-" + pd.getProxyId(), "PROXY_DEGRADED",
                "iCameraProxy is running degraded – JMX is unavailable. "
                        + "PID: " + pd.getServicePid()
                        + ", Service: " + pd.getServiceStatus()
                        + ". " + pd.getDownReason());

        // HSQLDB_DOWN: iCameraHSQLDB Windows service is not UP
        raiseOrClear("HSQLDB_DOWN", !"UP".equals(pd.getHsqldbStatus()),
                AlertData.Severity.CRITICAL, AlertData.Category.HSQLDB,
                "Proxy-" + pd.getProxyId(), "HSQLDB_STATUS",
                "iCameraHSQLDB service is " + pd.getHsqldbStatus());

        // HSQLDB_CONFLICT: JMX reports DB as DOWN but local service + port check says it is UP
        // This indicates a proxy-side DB connection issue rather than a DB failure
        boolean hsqldbConflict = "DOWN".equals(pd.getHsqldbJmxStatus())
                && "UP".equals(pd.getHsqldbStatus())
                && pd.isHsqldbDirectlyReachable();
        raiseOrClear("HSQLDB_CONFLICT", hsqldbConflict,
                AlertData.Severity.WARNING, AlertData.Category.HSQLDB,
                "Proxy-" + pd.getProxyId(), "HSQLDB_CONFLICT",
                "HSQLDB is reachable locally (port " + pd.getHsqldbPort()
                        + ") but iCameraProxy JMX reports DB as DOWN – "
                        + "possible proxy-side database connection issue");

        raiseOrClear("MAC_MISMATCH", pd.isMacMismatch(),
                AlertData.Severity.WARNING, AlertData.Category.MAC,
                "Proxy-" + pd.getProxyId(), "MAC_ADDRESS",
                "MAC address mismatch: current=" + pd.getCurrentMacAddress()
                        + " last=" + pd.getLastMacAddress());

        raiseOrClear("PROXY_CPU_HIGH", pd.getProcessCpuPercent() > ALERT_THRESHOLD,
                AlertData.Severity.WARNING, AlertData.Category.PROXY,
                "Proxy-" + pd.getProxyId(), "PROCESS_CPU",
                String.format("Proxy CPU usage %.1f%% exceeds %.0f%%",
                        pd.getProcessCpuPercent(), ALERT_THRESHOLD),
                pd.getProcessCpuPercent(), ALERT_THRESHOLD);

        raiseOrClear("PROXY_MEM_HIGH", pd.getProcessMemoryMb() > 2048,
                AlertData.Severity.WARNING, AlertData.Category.PROXY,
                "Proxy-" + pd.getProxyId(), "PROCESS_MEMORY",
                String.format("Proxy memory usage %.0f MB is high", pd.getProcessMemoryMb()),
                pd.getProcessMemoryMb(), 2048);
    }

    private void evaluateSystem() {
        SystemMetrics sm = store.getSystemMetrics();
        if (sm == null) return;

        raiseOrClear("CPU_HIGH", sm.getSystemCpuPercent() > ALERT_THRESHOLD,
                AlertData.Severity.WARNING, AlertData.Category.SYSTEM,
                "System", "CPU_USAGE",
                String.format("System CPU %.1f%% > %.0f%%", sm.getSystemCpuPercent(), ALERT_THRESHOLD),
                sm.getSystemCpuPercent(), ALERT_THRESHOLD);

        raiseOrClear("MEM_HIGH", sm.getMemoryUsedPercent() > ALERT_THRESHOLD,
                AlertData.Severity.WARNING, AlertData.Category.SYSTEM,
                "System", "MEMORY_USAGE",
                String.format("Memory used %.1f%% > %.0f%%", sm.getMemoryUsedPercent(), ALERT_THRESHOLD),
                sm.getMemoryUsedPercent(), ALERT_THRESHOLD);

        // Only alert for the drive where iCamera proxy is installed
        ProxyData pd = store.getProxyData();
        String proxyDrive = null;
        if (pd != null && pd.getInstallPath() != null && pd.getInstallPath().length() >= 2) {
            proxyDrive = pd.getInstallPath().substring(0, 2).toUpperCase();
        }

        for (SystemMetrics.DriveInfo di : sm.getDrives()) {
            if (proxyDrive != null && !di.getName().toUpperCase().startsWith(proxyDrive)) continue;
            if (proxyDrive == null) continue; // Can't determine proxy drive, skip disk alerts

            String key = "DISK_HIGH_" + di.getName();
            raiseOrClear(key, di.getUsedPercent() > ALERT_THRESHOLD,
                    AlertData.Severity.WARNING, AlertData.Category.SYSTEM,
                    "Disk-" + di.getName(), "DISK_USAGE",
                    String.format("Disk %s used %.1f%% > %.0f%%",
                            di.getName(), di.getUsedPercent(), ALERT_THRESHOLD),
                    di.getUsedPercent(), ALERT_THRESHOLD);
        }
    }

    private void evaluateCctv() {
        int total = store.getTotalCctvCount();
        if (total > 25) {
            raiseOrClear("CCTV_COUNT_HIGH", true,
                    AlertData.Severity.WARNING, AlertData.Category.CCTV,
                    "CCTV", "CCTV_COUNT",
                    "Total CCTV count (" + total + ") exceeds recommended limit of 25 per Proxy");
        }

        for (CctvData cctv : store.getAllCctv()) {
            String key = "CCTV_INACTIVE_" + cctv.getCctvId();
            raiseOrClear(key, !cctv.isActive(),
                    AlertData.Severity.WARNING, AlertData.Category.CCTV,
                    "CCTV-" + cctv.getCctvId() + "-" + cctv.getCctvName(), "CCTV_STATUS",
                    "CCTV " + cctv.getCctvName() + " is INACTIVE: " + cctv.getInactiveReason());
        }
    }

    private void evaluateNetwork() {
        SystemMetrics sm = store.getSystemMetrics();
        if (sm == null) return;
        raiseOrClear("NET_ZERO", sm.getNetworkSpeedMbps() == 0,
                AlertData.Severity.WARNING, AlertData.Category.NETWORK,
                "Network", "UPLOAD_SPEED",
                "Upload speed is 0 MB/s – possible network issue");
    }

    private void evaluateUrlChecks() {
        List<UrlCheckResult> results = store.getUrlCheckResults();
        if (results.isEmpty()) return;

        for (UrlCheckResult r : results) {
            String h = r.getHost();

            // Connectivity down → CRITICAL
            raiseOrClear("URL_DOWN_" + h, !r.isReachable(),
                    AlertData.Severity.CRITICAL, AlertData.Category.NETWORK,
                    h, "CONNECTIVITY",
                    "Connectivity to " + h + " is DOWN – host unreachable on port 443");

            // SSL invalid/expired → WARNING (only when host is reachable)
            boolean sslFailed = r.isReachable() && !r.isSslValid();
            String sslMsg = "SSL certificate validation failed for " + h
                    + (r.getErrorMessage().isEmpty() ? "" : ": " + r.getErrorMessage());
            raiseOrClear("SSL_INVALID_" + h, sslFailed,
                    AlertData.Severity.WARNING, AlertData.Category.NETWORK,
                    h, "SSL_CERTIFICATE", sslMsg);
        }
    }

    // Overload without numeric value
    private void raiseOrClear(String key, boolean condition,
                               AlertData.Severity severity, AlertData.Category category,
                               String source, String param, String message) {
        raiseOrClear(key, condition, severity, category, source, param, message, 0, 0);
    }

    private void raiseOrClear(String key, boolean condition,
                               AlertData.Severity severity, AlertData.Category category,
                               String source, String param, String message,
                               double currentVal, double threshold) {
        if (condition) {
            if (!activeAlertKeys.contains(key)) {
                AlertData alert = new AlertData(severity, category, source, param, message);
                alert.setCurrentValue(currentVal);
                alert.setThreshold(threshold);
                store.addAlert(alert);
                activeAlertKeys.add(key);
            }
        } else {
            // Condition cleared – resolve existing alert
            if (activeAlertKeys.contains(key)) {
                for (AlertData a : store.getAlerts()) {
                    if (a.getSource().equals(source) && a.getParameter().equals(param) && !a.isResolved()) {
                        a.setResolved(true);
                    }
                }
                activeAlertKeys.remove(key);
            }
        }
    }
}
