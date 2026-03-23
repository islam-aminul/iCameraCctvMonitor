package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.ProxyData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Windows Service Control Manager status for iCamera services.
 *
 * Services:
 *   - "iCamera Proxy Service"
 *   - "iCamera HSQLDB Service"
 */
public class WindowsServiceReader {

    private static final Logger log = LoggerFactory.getLogger(WindowsServiceReader.class);

    private static final String PROXY_SERVICE_NAME  = "iCamera Proxy Service";
    private static final String HSQLDB_SERVICE_NAME = "iCamera HSQLDB Service";

    private final DataStore store = DataStore.getInstance();

    // Cache service start times (expensive to query repeatedly)
    private long proxyServiceStartMs  = -1;
    private long hsqldbServiceStartMs = -1;

    public void refresh() {
        ProxyData pd = store.getProxyData();
        if (pd == null) pd = new ProxyData();

        // Proxy service
        ServiceInfo proxyInfo = queryService(PROXY_SERVICE_NAME);
        pd.setServiceStatus(proxyInfo.status);
        pd.setServiceExitCode(proxyInfo.exitCode);
        if ("RUNNING".equals(proxyInfo.status)) {
            pd.setStatus("UP");
            if (proxyServiceStartMs < 0) proxyServiceStartMs = proxyInfo.startTimeMs;
            pd.setStartTimeMillis(proxyServiceStartMs);
            pd.setUptimeMillis(System.currentTimeMillis() - proxyServiceStartMs);
            pd.setDownReason(null);
        } else if ("STOPPED".equals(proxyInfo.status)) {
            pd.setStatus("DOWN");
            pd.setDownReason(determineDownReason(proxyInfo.exitCode));
        } else if ("NOT_FOUND".equals(proxyInfo.status)) {
            pd.setStatus("UNKNOWN");
            pd.setDownReason("SERVICE NOT FOUND");
        }

        // HSQLDB service
        ServiceInfo hsqlInfo = queryService(HSQLDB_SERVICE_NAME);
        pd.setHsqldbStatus("RUNNING".equals(hsqlInfo.status) ? "UP"
                          : "STOPPED".equals(hsqlInfo.status) ? "DOWN" : "UNKNOWN");
        if ("RUNNING".equals(hsqlInfo.status)) {
            if (hsqldbServiceStartMs < 0) hsqldbServiceStartMs = hsqlInfo.startTimeMs;
            pd.setHsqldbStartTimeMillis(hsqldbServiceStartMs);
        }

        store.updateProxyData(pd);
    }

    private ServiceInfo queryService(String serviceName) {
        ServiceInfo info = new ServiceInfo();
        try {
            // sc query "service name"
            ProcessBuilder pb = new ProcessBuilder("sc", "query", serviceName);
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            proc.waitFor();
            String output = sb.toString();

            if (output.contains("FAILED 1060") || output.contains("does not exist")) {
                info.status = "NOT_FOUND";
                return info;
            }

            if (output.contains("RUNNING")) {
                info.status = "RUNNING";
                info.startTimeMs = queryServiceStartTime(serviceName);
            } else if (output.contains("STOPPED")) {
                info.status = "STOPPED";
                info.exitCode = parseExitCode(output);
            } else {
                info.status = "UNKNOWN";
            }
        } catch (Exception e) {
            log.warn("WindowsServiceReader error for '{}': {}", serviceName, e.getMessage());
            info.status = "UNKNOWN";
        }
        return info;
    }

    private long queryServiceStartTime(String serviceName) {
        try {
            // Use WMIC to get start time
            ProcessBuilder pb = new ProcessBuilder("wmic", "service", "where",
                    "Name='" + serviceName + "'", "get", "StartMode,State,ProcessId");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            proc.waitFor();
            // Parse PID, then use tasklist to get start time
            Matcher pidMatcher = Pattern.compile("(\\d{4,})").matcher(sb.toString());
            if (pidMatcher.find()) {
                int pid = Integer.parseInt(pidMatcher.group(1));
                return queryProcessStartTime(pid);
            }
        } catch (Exception e) {
            log.debug("Could not get service start time: {}", e.getMessage());
        }
        return System.currentTimeMillis(); // fallback
    }

    private long queryProcessStartTime(int pid) {
        try {
            ProcessBuilder pb = new ProcessBuilder("wmic", "process", "where",
                    "ProcessId=" + pid, "get", "CreationDate");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            proc.waitFor();
            // WMIC date format: 20260323062345.000000+330
            Pattern wmicDate = Pattern.compile("(\\d{14})");
            Matcher m = wmicDate.matcher(sb.toString());
            if (m.find()) {
                String ds = m.group(1);
                LocalDateTime ldt = LocalDateTime.parse(ds, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
                return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
        } catch (Exception e) {
            log.debug("Could not get process start time for PID {}: {}", pid, e.getMessage());
        }
        return System.currentTimeMillis();
    }

    private int parseExitCode(String scOutput) {
        Matcher m = Pattern.compile("WIN32_EXIT_CODE\\s*:\\s*(\\d+)").matcher(scOutput);
        if (m.find()) { try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {} }
        return -1;
    }

    private String determineDownReason(int exitCode) {
        if (exitCode < 0) return "Unknown – no exit code available";
        switch (exitCode) {
            case 0:  return "Normal stop (exit code 0)";
            case 1:  return "Incorrect function call";
            case 2:  return "File not found";
            case 5:  return "Access denied";
            case 1067: return "Process terminated unexpectedly";
            case 1073: return "Service already running";
            default: return "Stopped with exit code " + exitCode;
        }
    }

    private static class ServiceInfo {
        String status = "UNKNOWN";
        int exitCode = -1;
        long startTimeMs = -1;
    }
}
