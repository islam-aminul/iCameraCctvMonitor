package com.tcs.monitoring.beans;

import com.tcs.proxy.database.api.tables.ResourceFieldValues;
import com.tcs.proxy.model.Drivedetailbean;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;

/**
 * Dummy implementation of ProxyMonitoringMBean registered on the platform
 * MBean server under:
 *   com.tcs.monitoring.beans.proxy_monitoring_bean:type=monitoring
 *
 * Run as a standalone JMX server for local development / compile verification:
 *
 *   mvn exec:java                    → port 1099 (default)
 *   mvn exec:java -Dexec.args="1100" → custom port
 *
 * The monitor (iCameraCctvMonitor) will connect, find the MBean, create a
 * typed proxy and receive the dummy values defined below.
 */
public class ProxyMonitoring implements ProxyMonitoringMBean {

    private static final String MBEAN_OBJECT_NAME =
            "com.tcs.monitoring.beans.proxy_monitoring_bean:type=monitoring";

    // ---- Dummy data -------------------------------------------------------

    private final CctvMetrics  cctvMetrics  = buildDummyCctvMetrics();
    private final SystemMetrics systemMetrics = buildDummySystemMetrics();

    private static CctvMetrics buildDummyCctvMetrics() {
        CctvMetrics cm = new CctvMetrics();

        long now = System.currentTimeMillis();

        // Camera 1
        cm.startHitMap.put(101L, new ResourceFieldValues("Camera-Front-Gate",
                "rtsp://192.168.1.101:554/stream1"));
        cm.cctvStatusMap.put(101L, true);
        cm.cctvFileLastModifiedMap.put(101L, now - 30_000L);   // 30 s ago
        cm.cctvFileLastUploadedMap.put(101L, now - 45_000L);

        // Camera 2
        cm.startHitMap.put(102L, new ResourceFieldValues("Camera-Parking-A",
                "rtsp://192.168.1.102:554/stream1"));
        cm.cctvStatusMap.put(102L, true);
        cm.cctvFileLastModifiedMap.put(102L, now - 60_000L);
        cm.cctvFileLastUploadedMap.put(102L, now - 75_000L);

        // Camera 3 – intentionally unreachable / stale
        cm.startHitMap.put(103L, new ResourceFieldValues("Camera-Lobby1",
                "rtsp://192.168.1.103:554/stream1"));
        cm.cctvStatusMap.put(103L, false);
        cm.cctvFileLastModifiedMap.put(103L, now - 600_000L);  // 10 min ago
        cm.cctvFileLastUploadedMap.put(103L, now - 610_000L);

        // Camera 3 – intentionally unreachable / stale
        cm.startHitMap.put(104L, new ResourceFieldValues("Camera-Lobby2",
                "rtsp://192.168.1.104:554/stream1"));
        cm.cctvStatusMap.put(104L, false);
        cm.cctvFileLastModifiedMap.put(104L, now - 600_000L);  // 10 min ago
        cm.cctvFileLastUploadedMap.put(104L, now - 610_000L);

        return cm;
    }

    private static SystemMetrics buildDummySystemMetrics() {
        SystemMetrics sm = new SystemMetrics();
        sm.totalCpu       = 42.5;
        sm.processCpu     = 3.2;
        sm.totalRam       = 16384.0;   // MB
        sm.processRam     = 512.0;     // MB
        sm.freeRam        = 5120.0;    // MB
        sm.fileUploadSpeed = 8.4;      // MB/s

        sm.diskDetailsList.add(new Drivedetailbean("C:\\", 228949, 47862));
        sm.diskDetailsList.add(new Drivedetailbean("D:\\", 512000, 210000));

        return sm;
    }

    // ---- MBean interface implementation -----------------------------------

    @Override public long   getProxyId()               { return 12345L; }
    @Override public long   getTcCode()                { return 1001L; }
    @Override public long   getOrgId()                 { return 999L; }
    @Override public String getProxyName()             { return "DummyProxy-01"; }
    @Override public boolean getProxyStatusFlag()      { return true; }
    @Override public boolean getConnectionAttemptedFlag() { return true; }
    @Override public boolean getDbStatusFlag()         { return true; }
    @Override public boolean hasCctvAdapter()          { return true; }
    @Override public boolean hasRecordingAdapter()     { return false; }

    @Override
    public CctvMetrics getCctvMetrics() { return cctvMetrics; }

    @Override
    public SystemMetrics getSystemMetrics() { return systemMetrics; }

    // ---- Standalone JMX server entry point --------------------------------

    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 1099;

        // Start RMI registry on the requested port
        LocateRegistry.createRegistry(port);

        // Register MBean on the platform server
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(MBEAN_OBJECT_NAME);
        mbs.registerMBean(new ProxyMonitoring(), name);

        // Expose via JMX/RMI connector
        String url = "service:jmx:rmi:///jndi/rmi://localhost:" + port + "/jmxrmi";
        JMXServiceURL serviceUrl = new JMXServiceURL(url);
        JMXConnectorServer cs =
                JMXConnectorServerFactory.newJMXConnectorServer(serviceUrl, null, mbs);
        cs.start();

        System.out.println("Dummy JMX server started.");
        System.out.println("URL : " + url);
        System.out.println("Press Ctrl+C to stop.");

        Thread.currentThread().join(); // block until killed
    }
}
