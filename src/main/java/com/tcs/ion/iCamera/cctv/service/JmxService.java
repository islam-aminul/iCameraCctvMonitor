package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/**
 * Connects to the iCamera Proxy JMX MBean and extracts all metrics.
 *
 * JMX URL pattern: service:jmx:rmi:///jndi/rmi://{host}:{port}/jmxrmi
 * Default base port: 1999.  Tries up to 5 additional ports if base fails.
 *
 * MBean ObjectName: com.tcs.monitoring.beans.proxy_monitoring_bean:type=monitoring
 */
public class JmxService {

    private static final Logger log = LoggerFactory.getLogger(JmxService.class);

    private static final String MBEAN_NAME = "com.tcs.monitoring.beans.proxy_monitoring_bean:type=monitoring";
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Patterns for parsing composite string attributes
    private static final Pattern CCTV_BLOCK_PATTERN = Pattern.compile(
            "CCTV ID:\\s*(\\d+)\\s*CCTV Name:\\s*(.+?)\\s*RTSP:\\s*(\\S+)\\s*Reachable:\\s*(\\S+)\\s*" +
            "Last file modified at:\\s*(.+?)\\s*Last file uploaded at:\\s*(.+?)(?=CCTV ID:|$)",
            Pattern.DOTALL);

    private static final Pattern DRIVE_PATTERN = Pattern.compile(
            "Drive name:\\s*(\\S+),\\s*Total space available \\(in MB\\):\\s*(\\d+),\\s*Free space available \\(in MB\\):\\s*(\\d+)");

    private JMXConnector connector;
    private MBeanServerConnection mbsc;
    private ObjectName mbeanObjectName;
    private String connectedUrl;

    private final DataStore store = DataStore.getInstance();

    /**
     * Attempts connection on basePort, basePort+1, ..., basePort+maxRetries.
     * Returns true on success.
     */
    public synchronized boolean connect() {
        AppSettings cfg = store.getSettings();
        String host = cfg.getJmxHost();
        int base = cfg.getJmxBasePort();
        int retries = cfg.getJmxMaxPortRetries();

        disconnect();

        for (int i = 0; i <= retries; i++) {
            int port = base + i;
            String url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";
            try {
                log.info("Trying JMX connection: {}", url);
                JMXServiceURL serviceUrl = new JMXServiceURL(url);
                Map<String, Object> env = new HashMap<>();
                connector = JMXConnectorFactory.connect(serviceUrl, env);
                mbsc = connector.getMBeanServerConnection();
                mbeanObjectName = new ObjectName(MBEAN_NAME);
                connectedUrl = url;
                store.setActiveJmxUrl(url);
                store.setJmxConnected(true);
                log.info("JMX connected: {}", url);
                return true;
            } catch (Exception e) {
                log.warn("JMX failed on port {}: {}", port, e.getMessage());
                safeClose();
            }
        }
        store.setJmxConnected(false);
        store.setActiveJmxUrl(null);
        log.error("JMX connection failed on all ports ({}-{})", base, base + retries);
        return false;
    }

    /**
     * Polls all attributes from the MBean and updates DataStore.
     * Returns true on success.
     */
    public synchronized boolean poll() {
        if (mbsc == null) {
            if (!connect()) return false;
        }
        try {
            // Read each attribute individually (some may be "Unavailable" / null)
            String proxyIdStr   = getStringAttr("ProxyId");
            String proxyName    = getStringAttr("ProxyName");
            String tcCode       = getStringAttr("TcCode");
            String systemStr    = getStringAttr("SystemMetrics");
            String cctvStr      = getStringAttr("CctvMetrics");
            // DbStatusFlag: iCameraProxy's view of HSQLDB connectivity ("UP"/"DOWN"/null)
            String dbStatusFlag = getStringAttr("DbStatusFlag");

            // --- Build ProxyData ---
            ProxyData pd = store.getProxyData() != null ? store.getProxyData() : new ProxyData();
            if (proxyIdStr != null && !proxyIdStr.equalsIgnoreCase("Unavailable")) {
                try { pd.setProxyId(Integer.parseInt(proxyIdStr.trim())); } catch (NumberFormatException ignored) {}
            }
            pd.setProxyName(proxyName != null ? proxyName.trim() : "N/A");
            pd.setTcCode(tcCode != null ? tcCode.trim() : "N/A");
            pd.setStatus("UP"); // If we can reach JMX, proxy is up

            // Store DB connectivity status as seen by iCameraProxy
            if (dbStatusFlag != null && !dbStatusFlag.equalsIgnoreCase("Unavailable")) {
                pd.setHsqldbJmxStatus(dbStatusFlag.trim().toUpperCase());
            } else {
                pd.setHsqldbJmxStatus(null);
            }

            // --- Parse SystemMetrics string ---
            SystemMetrics sm = new SystemMetrics();
            if (systemStr != null && !systemStr.equalsIgnoreCase("Unavailable")) {
                parseSystemMetrics(systemStr, pd, sm);
            }

            // --- Parse CctvMetrics string ---
            if (cctvStr != null && !cctvStr.equalsIgnoreCase("Unavailable")) {
                parseCctvMetrics(cctvStr);
            }

            store.updateProxyData(pd);
            store.updateSystemMetrics(sm);
            if (sm.getNetworkSpeedMbps() > 0) {
                store.addNetworkDataPoint(new NetworkDataPoint(sm.getNetworkSpeedMbps()));
            }
            return true;

        } catch (Exception e) {
            log.error("JMX poll error: {}", e.getMessage());
            disconnect();
            return false;
        }
    }

    private String getStringAttr(String attrName) {
        try {
            Object val = mbsc.getAttribute(mbeanObjectName, attrName);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.debug("Attribute {} not available: {}", attrName, e.getMessage());
            return null;
        }
    }

    /**
     * Parses the SystemMetrics composite string from the MBean.
     *
     * Sample:
     * System CPU utilization (in %): 55.68
     * Process CPU Utilization (in %): 1.51
     * Total Memory available (in MB): 16144.69
     * Free memory: (in MB): 3550.40
     * Process memory utilization (in MB): 693.30
     * Network speed (in MB/s): 11.0
     * Drive details: [Drive name: C:\, Total space available (in MB): 228949, Free space available (in MB): 47862]
     */
    private void parseSystemMetrics(String raw, ProxyData pd, SystemMetrics sm) {
        sm.setSystemCpuPercent(parseDouble(raw, "System CPU utilization \\(in %\\):\\s*([\\d.]+)"));
        pd.setProcessCpuPercent(parseDouble(raw, "Process CPU Utilization \\(in %\\):\\s*([\\d.]+)"));
        sm.setTotalMemoryMb(parseDouble(raw, "Total Memory available \\(in MB\\):\\s*([\\d.]+)"));
        sm.setFreeMemoryMb(parseDouble(raw, "Free memory.*?\\(in MB\\):\\s*([\\d.]+)"));
        pd.setProcessMemoryMb(parseDouble(raw, "Process memory utilization \\(in MB\\):\\s*([\\d.]+)"));
        sm.setNetworkSpeedMbps(parseDouble(raw, "Network speed \\(in MB/s\\):\\s*([\\d.]+)"));

        // Parse drives
        Matcher dm = DRIVE_PATTERN.matcher(raw);
        List<SystemMetrics.DriveInfo> drives = new ArrayList<>();
        while (dm.find()) {
            SystemMetrics.DriveInfo di = new SystemMetrics.DriveInfo();
            di.setName(dm.group(1));
            di.setTotalSpaceMb(Long.parseLong(dm.group(2)));
            di.setFreeSpaceMb(Long.parseLong(dm.group(3)));
            drives.add(di);
        }
        sm.setDrives(drives);
    }

    /**
     * Parses the CctvMetrics composite string from the MBean and updates DataStore.
     */
    private void parseCctvMetrics(String raw) {
        // Normalise line endings
        String normalised = raw.replace("\r\n", "\n").replace("\r", "\n");

        Matcher m = CCTV_BLOCK_PATTERN.matcher(normalised);
        while (m.find()) {
            CctvData cctv = new CctvData();
            try { cctv.setCctvId(Integer.parseInt(m.group(1).trim())); } catch (NumberFormatException ignored) {}
            cctv.setCctvName(m.group(2).trim());
            cctv.setRtspUrl(m.group(3).trim());  // also extracts IP

            String reachable = m.group(4).trim();
            cctv.setReachable("Yes".equalsIgnoreCase(reachable) || "true".equalsIgnoreCase(reachable));

            cctv.setLastFileModifiedMillis(parseTimestamp(m.group(5).trim()));
            cctv.setLastFileUploadedMillis(parseTimestamp(m.group(6).trim()));

            // ffprobe data will be filled in by FfprobeService asynchronously
            store.updateCctvData(cctv);
        }
    }

    private long parseTimestamp(String s) {
        if (s == null || s.isEmpty() || "Not found".equalsIgnoreCase(s.trim())) return -1L;
        try {
            LocalDateTime ldt = LocalDateTime.parse(s.trim(), DTF);
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return -1L;
        }
    }

    private double parseDouble(String src, String pattern) {
        try {
            Matcher m = Pattern.compile(pattern).matcher(src);
            if (m.find()) return Double.parseDouble(m.group(1));
        } catch (Exception ignored) {}
        return 0.0;
    }

    public synchronized void disconnect() {
        safeClose();
        mbsc = null;
        mbeanObjectName = null;
        connectedUrl = null;
        store.setJmxConnected(false);
    }

    private void safeClose() {
        if (connector != null) {
            try { connector.close(); } catch (IOException ignored) {}
            connector = null;
        }
    }

    public boolean isConnected() { return mbsc != null; }
    public String getConnectedUrl() { return connectedUrl; }
}
