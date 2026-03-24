package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.ProxyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.software.os.OSProcess;
import oshi.software.os.OSService;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Windows service status for iCamera services using OSHI (no wmic).
 *
 * Services:
 *   - "iCameraProxy"   (Apache procrun, StartMode=jvm)
 *   - "iCameraHSQLDB"  (Apache procrun, StartMode=java)
 *
 * Strategy:
 *   1. Use OSHI OSService list for status + PID (requires admin; may return empty list for non-admin).
 *   2. If OSHI returns NOT_FOUND, fall back to "sc queryex <name>" which works for non-admin
 *      users (only needs SERVICE_QUERY_STATUS, not SC_MANAGER_ENUMERATE_SERVICE).
 *   3. Use OSHI OSProcess for process start time (PID lookup works for non-admin).
 *   4. Use "sc queryex <name>" WIN32_EXIT_CODE when stopped.
 *   5. Use "sc qc <name>" to read BINARY_PATH_NAME → derive install path.
 *   6. Read {installPath}\hsqldb\server.properties to find HSQLDB port.
 *   7. Socket-connect to HSQLDB port to verify direct reachability.
 *
 * DEGRADED state:
 *   - If iCameraProxy service is RUNNING but JMX is not connected → status = "DEGRADED".
 *   - Expose PID so the UI can show process-level info in degraded mode.
 */
public class WindowsServiceReader {

    private static final Logger log = LoggerFactory.getLogger(WindowsServiceReader.class);

    private static final String PROXY_SERVICE_NAME  = "iCameraProxy";
    private static final String HSQLDB_SERVICE_NAME = "iCameraHSQLDB";

    private static final int HSQLDB_SOCKET_TIMEOUT_MS = 3000;

    private final DataStore store = DataStore.getInstance();

    // OSHI – reuse single instance; creation is inexpensive after first call
    private final SystemInfo     si = new SystemInfo();
    private final OperatingSystem os = si.getOperatingSystem();

    // Cache install path (derived from sc qc once; changes only on reinstall)
    private String cachedInstallPath = null;

    // Cache service start times (avoids repeated process lookups)
    private long proxyServiceStartMs  = -1;
    private long hsqldbServiceStartMs = -1;

    public void refresh() {
        ProxyData pd = store.getProxyData();
        if (pd == null) pd = new ProxyData();

        // ── Install path (lazy, cached) ──────────────────────────────────────
        if (cachedInstallPath == null) {
            cachedInstallPath = queryInstallPath(PROXY_SERVICE_NAME);
            if (cachedInstallPath != null) {
                log.info("iCamera install path resolved: {}", cachedInstallPath);
            }
        }
        pd.setInstallPath(cachedInstallPath);

        // ── iCameraProxy service ─────────────────────────────────────────────
        ServiceInfo proxyInfo = queryServiceViaOshi(PROXY_SERVICE_NAME);
        pd.setServiceStatus(proxyInfo.status);
        pd.setServiceExitCode(proxyInfo.exitCode);
        pd.setServicePid(proxyInfo.pid);

        if ("RUNNING".equals(proxyInfo.status)) {
            // Cache start time on first successful query
            if (proxyServiceStartMs < 0 && proxyInfo.startTimeMs > 0) {
                proxyServiceStartMs = proxyInfo.startTimeMs;
            }
            if (proxyServiceStartMs > 0) {
                pd.setStartTimeMillis(proxyServiceStartMs);
                pd.setUptimeMillis(System.currentTimeMillis() - proxyServiceStartMs);
            }

            // DEGRADED: service is running but JMX could not connect
            if (!store.isJmxConnected()) {
                pd.setStatus("DEGRADED");
                pd.setDownReason("Service is running (PID " + proxyInfo.pid
                        + ") but JMX is unavailable – metrics cannot be collected");
            } else {
                pd.setStatus("UP");
                pd.setDownReason(null);
            }

        } else if ("STOPPED".equals(proxyInfo.status)) {
            pd.setStatus("DOWN");
            pd.setDownReason(determineDownReason(proxyInfo.exitCode));
            proxyServiceStartMs = -1; // reset cache so uptime recalculates on restart

        } else if ("NOT_FOUND".equals(proxyInfo.status)) {
            pd.setStatus("UNKNOWN");
            pd.setDownReason("Service 'iCameraProxy' not found on this machine");

        } else {
            pd.setStatus("UNKNOWN");
            pd.setDownReason("Service status could not be determined");
        }

        // ── iCameraHSQLDB service ────────────────────────────────────────────
        ServiceInfo hsqlInfo = queryServiceViaOshi(HSQLDB_SERVICE_NAME);
        if ("RUNNING".equals(hsqlInfo.status)) {
            pd.setHsqldbStatus("UP");
            if (hsqldbServiceStartMs < 0 && hsqlInfo.startTimeMs > 0) {
                hsqldbServiceStartMs = hsqlInfo.startTimeMs;
            }
            if (hsqldbServiceStartMs > 0) {
                pd.setHsqldbStartTimeMillis(hsqldbServiceStartMs);
            }
        } else if ("STOPPED".equals(hsqlInfo.status)) {
            pd.setHsqldbStatus("DOWN");
            hsqldbServiceStartMs = -1;
        } else if ("NOT_FOUND".equals(hsqlInfo.status)) {
            pd.setHsqldbStatus("UNKNOWN");
        } else {
            pd.setHsqldbStatus("UNKNOWN");
        }

        // ── HSQLDB direct connectivity check ────────────────────────────────
        int hsqldbPort = readHsqldbPort(cachedInstallPath);
        pd.setHsqldbPort(hsqldbPort);
        pd.setHsqldbDirectlyReachable(checkHsqldbPort(hsqldbPort));

        store.updateProxyData(pd);
    }

    // ── OSHI-based service query ─────────────────────────────────────────────

    /**
     * Queries service status and PID via OSHI first.
     * OSHI's getServices() calls OpenSCManager with SC_MANAGER_ENUMERATE_SERVICE which
     * requires admin rights – it returns an empty list for non-admin users, causing every
     * service to appear as NOT_FOUND. When that happens, falls back to "sc queryex" which
     * only needs SERVICE_QUERY_STATUS and works for normal (non-admin) users querying a
     * specific service by name.
     */
    private ServiceInfo queryServiceViaOshi(String serviceName) {
        ServiceInfo info = new ServiceInfo();
        try {
            List<OSService> services = os.getServices();
            for (OSService svc : services) {
                if (serviceName.equalsIgnoreCase(svc.getName())) {
                    OSService.State state = svc.getState();
                    if (state == OSService.State.RUNNING) {
                        info.status = "RUNNING";
                        info.pid    = svc.getProcessID();
                        if (info.pid > 0) {
                            OSProcess proc = os.getProcess(info.pid);
                            if (proc != null) {
                                info.startTimeMs = proc.getStartTime();
                            }
                        }
                    } else if (state == OSService.State.STOPPED) {
                        info.status   = "STOPPED";
                        info.exitCode = queryScEx(serviceName).exitCode;
                    } else {
                        info.status = "UNKNOWN";
                    }
                    return info;
                }
            }
            // Service not found in OSHI list – likely running as SYSTEM / non-admin enumeration
            // blocked. Fall back to sc queryex which works without admin for a named service.
            log.debug("'{}' not in OSHI service list (non-admin?); falling back to sc queryex", serviceName);
            return queryScEx(serviceName);
        } catch (Exception e) {
            log.warn("OSHI service query failed for '{}': {} – falling back to sc queryex",
                    serviceName, e.getMessage());
            return queryScEx(serviceName);
        }
    }

    // ── sc queryex fallback (works for non-admin on named service) ────────────

    /**
     * Runs "sc queryex {serviceName}" and parses STATE, WIN32_EXIT_CODE, and PID.
     * sc queryex only needs SERVICE_QUERY_STATUS access (no admin required for a specific
     * service name), unlike OSHI getServices() which needs SC_MANAGER_ENUMERATE_SERVICE.
     * If the service is RUNNING and a PID is returned, OSHI OSProcess is used for start time.
     */
    private ServiceInfo queryScEx(String serviceName) {
        ServiceInfo info = new ServiceInfo();
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "queryex", serviceName);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            proc.waitFor();
            String output = sb.toString();

            // sc returns "FAILED 1060" when the service does not exist
            if (output.contains("1060") || output.toLowerCase().contains("does not exist")) {
                info.status = "NOT_FOUND";
                return info;
            }

            // Parse STATE:  "STATE              : 4  RUNNING"
            Matcher stateMatcher = Pattern.compile("STATE\\s*:\\s*\\d+\\s+(\\w+)").matcher(output);
            if (stateMatcher.find()) {
                String stateWord = stateMatcher.group(1);
                if ("RUNNING".equals(stateWord)) {
                    info.status = "RUNNING";
                    // Parse PID: "PID                : 1234"
                    Matcher pidMatcher = Pattern.compile("PID\\s*:\\s*(\\d+)").matcher(output);
                    if (pidMatcher.find()) {
                        info.pid = Integer.parseInt(pidMatcher.group(1));
                        if (info.pid > 0) {
                            OSProcess oproc = os.getProcess(info.pid);
                            if (oproc != null) info.startTimeMs = oproc.getStartTime();
                        }
                    }
                } else if ("STOPPED".equals(stateWord)) {
                    info.status   = "STOPPED";
                    info.exitCode = parseExitCode(output);
                } else {
                    info.status = "UNKNOWN";
                }
            } else {
                log.warn("sc queryex for '{}' returned unexpected output: {}", serviceName, output.trim());
                info.status = "UNKNOWN";
            }
        } catch (Exception e) {
            log.warn("sc queryex failed for '{}': {}", serviceName, e.getMessage());
            info.status = "UNKNOWN";
        }
        return info;
    }

    private int parseExitCode(String scOutput) {
        Matcher m = Pattern.compile("WIN32_EXIT_CODE\\s*:\\s*(\\d+)").matcher(scOutput);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private String determineDownReason(int exitCode) {
        if (exitCode < 0) return "Stopped – exit code unknown";
        switch (exitCode) {
            case 0:    return "Normal stop (exit code 0)";
            case 1:    return "Incorrect function call (exit code 1)";
            case 2:    return "File not found (exit code 2)";
            case 5:    return "Access denied (exit code 5)";
            case 1067: return "Process terminated unexpectedly (exit code 1067)";
            case 1073: return "Service already running (exit code 1073)";
            default:   return "Stopped with exit code " + exitCode;
        }
    }

    // ── Install path via sc qc ────────────────────────────────────────────────

    /**
     * Runs "sc qc <serviceName>" and extracts the base install directory from
     * BINARY_PATH_NAME. Services are launched via Apache procrun:
     *   C:\iCamera\procrun\amd64\prunsrv.exe //RS//iCameraProxy
     * → install path = C:\iCamera  (everything before \procrun\)
     */
    private String queryInstallPath(String serviceName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sc", "qc", serviceName);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            proc.waitFor();

            Matcher m = Pattern.compile("BINARY_PATH_NAME\\s*:\\s*(.+)").matcher(sb.toString());
            if (m.find()) {
                // Strip any procrun arguments that follow the exe path (e.g. //RS//iCameraProxy)
                String rawPath = m.group(1).trim();
                String exePath = rawPath.split("\\s+//")[0].trim();

                // Locate \procrun\ segment to derive the install root
                int idx = exePath.toLowerCase().indexOf("\\procrun\\");
                if (idx > 0) {
                    return exePath.substring(0, idx);
                }
                // Fallback: three levels up from prunsrv.exe (amd64 → procrun → install)
                File f = new File(exePath);
                if (f.exists() && f.getParentFile() != null
                        && f.getParentFile().getParentFile() != null
                        && f.getParentFile().getParentFile().getParentFile() != null) {
                    return f.getParentFile().getParentFile().getParentFile().getAbsolutePath();
                }
            }
        } catch (Exception e) {
            log.debug("Could not determine install path from sc qc '{}': {}", serviceName, e.getMessage());
        }
        return null;
    }

    // ── HSQLDB server.properties ──────────────────────────────────────────────

    /**
     * Reads {installPath}\hsqldb\server.properties and extracts the server port.
     * Checks for "server.port" (single server) and "server.port.0" (multi-server config).
     */
    private int readHsqldbPort(String installPath) {
        if (installPath == null) return -1;
        File propsFile = new File(installPath + "\\hsqldb\\server.properties");
        if (!propsFile.exists()) {
            log.debug("HSQLDB server.properties not found at: {}", propsFile.getAbsolutePath());
            return -1;
        }
        try (FileInputStream fis = new FileInputStream(propsFile)) {
            Properties props = new Properties();
            props.load(fis);
            String port = props.getProperty("server.port");
            if (port == null) port = props.getProperty("server.port.0");
            if (port != null) {
                int p = Integer.parseInt(port.trim());
                log.debug("HSQLDB port from server.properties: {}", p);
                return p;
            }
        } catch (Exception e) {
            log.warn("Failed to read HSQLDB server.properties: {}", e.getMessage());
        }
        return -1;
    }

    /**
     * Attempts a TCP socket connect to localhost:{port} within HSQLDB_SOCKET_TIMEOUT_MS.
     * Returns true if HSQLDB is accepting connections on that port.
     */
    private boolean checkHsqldbPort(int port) {
        if (port <= 0) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", port), HSQLDB_SOCKET_TIMEOUT_MS);
            log.debug("HSQLDB port {} is reachable", port);
            return true;
        } catch (Exception e) {
            log.debug("HSQLDB port {} not reachable: {}", port, e.getMessage());
            return false;
        }
    }

    // ── Internal data holder ──────────────────────────────────────────────────

    private static class ServiceInfo {
        String status    = "UNKNOWN";
        int    exitCode  = -1;
        int    pid       = 0;
        long   startTimeMs = -1;
    }
}
