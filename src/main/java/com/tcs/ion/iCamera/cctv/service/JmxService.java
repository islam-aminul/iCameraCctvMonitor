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
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMISocketFactory;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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

    /**
     * Holds the JMX host that the custom RMI socket factory should connect to.
     * Updated before each connection attempt so the factory always targets the
     * currently configured host, even across reconnect cycles.
     */
    private static final AtomicReference<String> RMI_TARGET_HOST =
            new AtomicReference<>("localhost");

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

    /** True after the first JMX failure has been fully logged; suppresses repeat stack traces. */
    private volatile boolean jmxFailureLogged = false;

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

        // Force all outbound RMI sockets to connect through the configured host
        // instead of following stub redirects to potentially unreachable internal IPs.
        RMI_TARGET_HOST.set(host);
        installLocalSocketFactory();

        for (int i = 0; i <= retries; i++) {
            int port = base + i;
            String url = "service:jmx:rmi:///jndi/rmi://" + host + ":" + port + "/jmxrmi";
            try {
                log.info("Trying JMX connection: {}", url);
                JMXServiceURL serviceUrl = new JMXServiceURL(url);
                connector = JMXConnectorFactory.connect(serviceUrl, buildEnvironment());
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
                jmxFailureLogged = false;   // reset suppression on successful connect
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
            if (!jmxFailureLogged) {
                log.error("JMX poll error: {}", e.getMessage(), e);
                jmxFailureLogged = true;
            } else {
                log.warn("JMX poll error (details suppressed after first occurrence): {}",
                        e.getMessage());
            }
            disconnect();
            return false;
        }
    }

    // ---- Typed-proxy path -------------------------------------------------

    private boolean pollViaTypedProxy(ProxyData pd, SystemMetrics sm) {
        pd.setProxyId((int) proxy.getProxyId());
        pd.setOrgId(proxy.getOrgId());
        pd.setProxyName(proxy.getProxyName() != null ? proxy.getProxyName() : "N/A");
        pd.setTcCode(String.valueOf(proxy.getTcCode()));
        pd.setStatus("UP");
        pd.setHsqldbJmxStatus(proxy.getDbStatusFlag() ? "UP" : "DOWN");

        // --- SystemMetrics (proxy bean type) ---
        // Use fully qualified com.tcs.monitoring.beans.SystemMetrics to avoid
        // a name clash with com.tcs.ion.iCamera.cctv.model.SystemMetrics.
        // Fields are direct public fields (same pattern as CctvMetrics).
        com.tcs.monitoring.beans.SystemMetrics pSm = proxy.getSystemMetrics();
        if (pSm != null) {
            sm.setSystemCpuPercent(pSm.totalCpu);
            pd.setProcessCpuPercent(pSm.processCpu);
            sm.setTotalMemoryMb(pSm.totalRam);
            sm.setFreeMemoryMb(pSm.freeRam);
            pd.setProcessMemoryMb(pSm.processRam);
            sm.setNetworkSpeedMbps(pSm.fileUploadSpeed);
            // pSm.diskDetailsList is intentionally skipped: OSHI is the
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
        // CctvMetrics exposes direct public fields (not getters)
        Map<Long, com.tcs.proxy.database.api.tables.ResourceFieldValues> startHitMap =
                pCm.startHitMap;
        Map<Long, Boolean> statusMap       = pCm.cctvStatusMap;
        Map<Long, Long>    lastModifiedMap = pCm.cctvFileLastModifiedMap;
        Map<Long, Long>    lastUploadedMap = pCm.cctvFileLastUploadedMap;

        if (startHitMap != null && !startHitMap.isEmpty()) {
            for (Map.Entry<Long, com.tcs.proxy.database.api.tables.ResourceFieldValues> entry
                    : startHitMap.entrySet()) {
                long id  = entry.getKey();
                com.tcs.proxy.database.api.tables.ResourceFieldValues rfv = entry.getValue();

                CctvData cctv = new CctvData();
                cctv.setCctvId((int) id);

                if (rfv != null) {
                    // ResourceFieldValues typed getters: getName() → CCTV display name,
                    // getKey3() → RTSP URL stored in the resource key-3 field.
                    String name = rfv.getName();
                    String rtsp = rfv.getKey3();
                    if (name != null) cctv.setCctvName(name);
                    if (rtsp != null) {
                        cctv.setRtspUrl(rtsp);   // also auto-extracts IP from RTSP as fallback
                    }
                    // IP address is auto-extracted from the RTSP URL by setRtspUrl().
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

    // ---- Connection helpers -----------------------------------------------

    /**
     * Installs a global RMI socket factory (once per JVM) that forces all outbound
     * RMI connections through the host stored in {@link #RMI_TARGET_HOST}.
     *
     * This prevents JMX from following stub redirects to internal VPN addresses
     * (e.g. 172.x.x.x) that are unreachable from the client when the proxy runs
     * behind NAT or on a multi-homed system.  The factory is installed at most once;
     * subsequent calls are no-ops because {@link RMISocketFactory#setSocketFactory}
     * throws {@link IOException} if already set.
     */
    private static void installLocalSocketFactory() {
        try {
            if (RMISocketFactory.getSocketFactory() == null) {
                RMISocketFactory.setSocketFactory(new RMISocketFactory() {
                    @Override
                    public Socket createSocket(String ignoredHost, int port) throws IOException {
                        return new Socket(RMI_TARGET_HOST.get(), port);
                    }

                    @Override
                    public ServerSocket createServerSocket(int port) throws IOException {
                        return new ServerSocket(port);
                    }
                });
                log.debug("Custom RMI socket factory installed; connections forced through {}",
                        RMI_TARGET_HOST.get());
            }
        } catch (IOException e) {
            log.warn("Could not install custom RMI socket factory: {}", e.getMessage());
        }
    }

    /**
     * Builds the JMX connector environment map with comprehensive timeout settings.
     * All timeouts are set to 5 000 ms so that connection attempts fail fast when
     * the target JMX service is unavailable or the network is impaired.
     */
    private static Map<String, Object> buildEnvironment() {
        Map<String, Object> env = new HashMap<>();
        // How long (ms) the client waits for a response to an MBean operation request
        env.put("jmx.remote.x.request.waiting.timeout", 5000L);
        // RMI TCP-level timeouts (applied as System properties; safe to set repeatedly)
        System.setProperty("sun.rmi.transport.tcp.responseTimeout",  "5000");
        System.setProperty("sun.rmi.transport.connectionTimeout",    "5000");
        System.setProperty("sun.rmi.transport.tcp.handshakeTimeout", "5000");
        return env;
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
