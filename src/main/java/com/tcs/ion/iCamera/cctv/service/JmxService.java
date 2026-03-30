package com.tcs.ion.iCamera.cctv.service;

import com.tcs.ion.iCamera.cctv.model.AppSettings;
import com.tcs.ion.iCamera.cctv.model.CctvData;
import com.tcs.ion.iCamera.cctv.model.NetworkDataPoint;
import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.model.SystemMetrics;
import com.tcs.monitoring.beans.ProxyMonitoringMBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.JMX;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connects to the iCamera Proxy JMX MBean and extracts all metrics.
 *
 * JMX URL pattern: service:jmx:rmi:///jndi/rmi://{host}:{port}/jmxrmi
 * Default base port: 1099. Scans ports 1099–1104 (configurable via AppSettings).
 *
 * MBean ObjectName: com.tcs.monitoring.beans.proxy_monitoring_bean:type=monitoring
 *
 * Primary path: typed MBean proxy via JMX.newMBeanProxy() against ProxyMonitoringMBean,
 * giving direct deserialization of CctvMetrics and SystemMetrics without reflection.
 * Fallback path: raw getAttribute() + string-regex parsing, preserved for resilience.
 */
public class JmxService {

    private static final Logger log = LoggerFactory.getLogger(JmxService.class);

    private static final String MBEAN_NAME =
            "com.tcs.monitoring.beans.proxy_monitoring_bean:type=monitoring";
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Fallback patterns – used only when typed proxy access fails at runtime
    private static final Pattern CCTV_BLOCK_PATTERN = Pattern.compile(
            "CCTV ID:\\s*(\\d+)\\s*CCTV Name:\\s*(.+?)\\s*RTSP:\\s*(\\S+)\\s*Reachable:\\s*(\\S+)\\s*" +
            "Last file modified at:\\s*(.+?)\\s*Last file uploaded at:\\s*(.+?)(?=CCTV ID:|$)",
            Pattern.DOTALL);

    private static final Pattern DRIVE_PATTERN = Pattern.compile(
            "Drive name:\\s*(\\S+),\\s*Total space available \\(in MB\\):\\s*(\\d+)," +
            "\\s*Free space available \\(in MB\\):\\s*(\\d+)");

    private JMXConnector connector;
    private MBeanServerConnection mbsc;
    private ObjectName mbeanObjectName;
    private ProxyMonitoringMBean proxy;   // typed proxy; null when unavailable
    private String connectedUrl;

    private final DataStore store = DataStore.getInstance();

    // ---- Connection -------------------------------------------------------

    /**
     * Attempts connection on basePort, basePort+1, ..., basePort+maxRetries (default 1099–1104).
     * After a successful TCP connect the MBean is validated via queryNames() before a typed
     * proxy is created with JMX.newMBeanProxy().  Returns true on success.
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
                connector = JMXConnectorFactory.connect(serviceUrl, new HashMap<String, Object>());
                mbsc = connector.getMBeanServerConnection();
                mbeanObjectName = new ObjectName(MBEAN_NAME);

                // Validate the MBean is actually registered before proceeding
                Set<ObjectName> found = mbsc.queryNames(mbeanObjectName, null);
                if (found == null || found.isEmpty()) {
                    log.warn("MBean {} not found on port {}", MBEAN_NAME, port);
                    safeClose();
                    continue;
                }

                // Typed proxy – cleanest and most reliable access pattern
                proxy = JMX.newMBeanProxy(mbsc, mbeanObjectName, ProxyMonitoringMBean.class);

                connectedUrl = url;
                store.setActiveJmxUrl(url);
                store.setJmxConnected(true);
                log.info("JMX connected with typed proxy: {}", url);
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

    // ---- Poll -------------------------------------------------------------

    /**
     * Polls all attributes from the MBean and updates DataStore.
     * Tries typed proxy first; falls back to raw string-attribute parsing.
     * Returns true on success.
     */
    public synchronized boolean poll() {
        if (mbsc == null) {
            if (!connect()) return false;
        }
        try {
            ProxyData pd = store.getProxyData() != null ? store.getProxyData() : new ProxyData();
            SystemMetrics sm = new SystemMetrics();

            boolean typedOk = false;
            if (proxy != null) {
                try {
                    typedOk = pollViaTypedProxy(pd, sm);
                } catch (Exception e) {
                    log.warn("Typed proxy poll failed, falling back to string parsing: {}",
                            e.getMessage());
                }
            }

            if (!typedOk) {
                pollViaStringAttributes(pd, sm);
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

    // ---- Typed-proxy path -------------------------------------------------

    private boolean pollViaTypedProxy(ProxyData pd, SystemMetrics sm) {
        pd.setProxyId((int) proxy.getProxyId());
        pd.setProxyName(proxy.getProxyName() != null ? proxy.getProxyName() : "N/A");
        pd.setTcCode(String.valueOf(proxy.getTcCode()));
        pd.setStatus("UP");
        pd.setHsqldbJmxStatus(proxy.getDbStatusFlag() ? "UP" : "DOWN");

        // --- SystemMetrics (proxy bean type) ---
        // Use fully qualified com.tcs.monitoring.beans.SystemMetrics to avoid
        // a name clash with com.tcs.ion.iCamera.cctv.model.SystemMetrics.
        com.tcs.monitoring.beans.SystemMetrics pSm = proxy.getSystemMetrics();
        if (pSm != null) {
            sm.setSystemCpuPercent(pSm.getTotalCpu());
            pd.setProcessCpuPercent(pSm.getProcessCpu());
            sm.setTotalMemoryMb(pSm.getTotalRam());
            sm.setFreeMemoryMb(pSm.getFreeRam());
            pd.setProcessMemoryMb(pSm.getProcessRam());
            sm.setNetworkSpeedMbps(pSm.getFileUploadSpeed());
            // pSm.getDiskDetailsList() is intentionally skipped: OSHI is the
            // authoritative source for local disk information and avoids the
            // unnecessary remote-parsing complexity that JMX would introduce.
        }

        // --- CctvMetrics (proxy bean type) ---
        com.tcs.monitoring.beans.CctvMetrics pCm = proxy.getCctvMetrics();
        if (pCm != null) {
            extractCctvFromTyped(pCm);
        }

        return true;
    }

    /**
     * Iterates startHitMap from CctvMetrics to extract CCTV metadata exactly in
     * the spirit of the working example's printCctvDetails flow.
     * Falls back to cctvStatusMap when startHitMap does not provide sufficient detail.
     */
    private void extractCctvFromTyped(com.tcs.monitoring.beans.CctvMetrics pCm) {
        Map<Long, com.tcs.proxy.database.api.tables.ResourceFieldValues> startHitMap =
                pCm.getStartHitMap();
        Map<Long, Boolean> statusMap           = pCm.getCctvStatusMap();
        Map<Long, Long>    lastModifiedMap     = pCm.getCctvFileLastModifiedMap();
        Map<Long, Long>    lastUploadedMap     = pCm.getCctvFileLastUploadedMap();

        if (startHitMap != null && !startHitMap.isEmpty()) {
            for (Map.Entry<Long, com.tcs.proxy.database.api.tables.ResourceFieldValues> entry
                    : startHitMap.entrySet()) {
                long id  = entry.getKey();
                com.tcs.proxy.database.api.tables.ResourceFieldValues rfv = entry.getValue();

                CctvData cctv = new CctvData();
                cctv.setCctvId((int) id);

                if (rfv != null) {
                    // ResourceFieldValues exposes typed getters for the resource record fields.
                    // Adjust method names below to match the actual ResourceFieldValues API once
                    // the Proxy-Common-Utils JAR is available (e.g. getRtspUrl() / getRtspIp()).
                    String name = rfv.getResourceName();
                    String rtsp = rfv.getRtspUrl();
                    if (name != null) cctv.setCctvName(name);
                    if (rtsp != null) cctv.setRtspUrl(rtsp);
                }

                // Reachability from cctvStatusMap
                if (statusMap != null) {
                    cctv.setReachable(Boolean.TRUE.equals(statusMap.get(id)));
                }

                cctv.setLastFileModifiedMillis(longOrMinus1(lastModifiedMap, id));
                cctv.setLastFileUploadedMillis(longOrMinus1(lastUploadedMap, id));

                store.updateCctvData(cctv);
            }
        } else if (statusMap != null) {
            // Fallback: cctvStatusMap provides at least reachability + timestamps
            for (Map.Entry<Long, Boolean> entry : statusMap.entrySet()) {
                long id = entry.getKey();
                CctvData cctv = new CctvData();
                cctv.setCctvId((int) id);
                cctv.setReachable(Boolean.TRUE.equals(entry.getValue()));
                cctv.setLastFileModifiedMillis(longOrMinus1(lastModifiedMap, id));
                cctv.setLastFileUploadedMillis(longOrMinus1(lastUploadedMap, id));
                store.updateCctvData(cctv);
            }
        }
    }

    private long longOrMinus1(Map<Long, Long> map, long key) {
        if (map == null) return -1L;
        Long val = map.get(key);
        return val != null ? val : -1L;
    }

    // ---- String-parsing fallback path -------------------------------------

    private void pollViaStringAttributes(ProxyData pd, SystemMetrics sm) {
        String proxyIdStr   = getStringAttr("ProxyId");
        String proxyName    = getStringAttr("ProxyName");
        String tcCode       = getStringAttr("TcCode");
        String systemStr    = getStringAttr("SystemMetrics");
        String cctvStr      = getStringAttr("CctvMetrics");
        String dbStatusFlag = getStringAttr("DbStatusFlag");

        if (proxyIdStr != null && !proxyIdStr.equalsIgnoreCase("Unavailable")) {
            try { pd.setProxyId(Integer.parseInt(proxyIdStr.trim())); }
            catch (NumberFormatException ignored) {}
        }
        pd.setProxyName(proxyName != null ? proxyName.trim() : "N/A");
        pd.setTcCode(tcCode != null ? tcCode.trim() : "N/A");
        pd.setStatus("UP");

        if (dbStatusFlag != null && !dbStatusFlag.equalsIgnoreCase("Unavailable")) {
            pd.setHsqldbJmxStatus(dbStatusFlag.trim().toUpperCase());
        } else {
            pd.setHsqldbJmxStatus(null);
        }

        if (systemStr != null && !systemStr.equalsIgnoreCase("Unavailable")) {
            parseSystemMetrics(systemStr, pd, sm);
        }
        if (cctvStr != null && !cctvStr.equalsIgnoreCase("Unavailable")) {
            parseCctvMetrics(cctvStr);
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

    private void parseSystemMetrics(String raw, ProxyData pd, SystemMetrics sm) {
        sm.setSystemCpuPercent(parseDouble(raw,
                "System CPU utilization \\(in %\\):\\s*([\\d.]+)"));
        pd.setProcessCpuPercent(parseDouble(raw,
                "Process CPU Utilization \\(in %\\):\\s*([\\d.]+)"));
        sm.setTotalMemoryMb(parseDouble(raw,
                "Total Memory available \\(in MB\\):\\s*([\\d.]+)"));
        sm.setFreeMemoryMb(parseDouble(raw,
                "Free memory.*?\\(in MB\\):\\s*([\\d.]+)"));
        pd.setProcessMemoryMb(parseDouble(raw,
                "Process memory utilization \\(in MB\\):\\s*([\\d.]+)"));
        sm.setNetworkSpeedMbps(parseDouble(raw,
                "Network speed \\(in MB/s\\):\\s*([\\d.]+)"));

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

    private void parseCctvMetrics(String raw) {
        String normalised = raw.replace("\r\n", "\n").replace("\r", "\n");
        Matcher m = CCTV_BLOCK_PATTERN.matcher(normalised);
        while (m.find()) {
            CctvData cctv = new CctvData();
            try { cctv.setCctvId(Integer.parseInt(m.group(1).trim())); }
            catch (NumberFormatException ignored) {}
            cctv.setCctvName(m.group(2).trim());
            cctv.setRtspUrl(m.group(3).trim());

            String reachable = m.group(4).trim();
            cctv.setReachable("Yes".equalsIgnoreCase(reachable) || "true".equalsIgnoreCase(reachable));

            cctv.setLastFileModifiedMillis(parseTimestamp(m.group(5).trim()));
            cctv.setLastFileUploadedMillis(parseTimestamp(m.group(6).trim()));

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

    // ---- Lifecycle --------------------------------------------------------

    public synchronized void disconnect() {
        proxy = null;
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
