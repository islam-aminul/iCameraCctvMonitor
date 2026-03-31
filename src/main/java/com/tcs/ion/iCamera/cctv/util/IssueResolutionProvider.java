package com.tcs.ion.iCamera.cctv.util;

import java.util.*;

/**
 * Maps alert parameters to human-readable resolution guidance.
 * Each resolution contains a title, narrowed root cause, and ordered resolution steps
 * that non-technical TC staff can follow.
 */
public final class IssueResolutionProvider {

    private IssueResolutionProvider() {}

    public static class Resolution {
        private final String title;
        private final String rootCause;
        private final List<String> steps;

        public Resolution(String title, String rootCause, List<String> steps) {
            this.title = title;
            this.rootCause = rootCause;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        public String getTitle() { return title; }
        public String getRootCause() { return rootCause; }
        public List<String> getSteps() { return steps; }
    }

    private static final Map<String, Resolution> RESOLUTIONS = new LinkedHashMap<>();

    static {
        // --- Proxy ---
        put("PROXY_STATUS", new Resolution(
                "Proxy Service Down",
                "iCameraProxy Windows service is not running or unreachable.",
                Arrays.asList(
                        "Open Windows Services panel (press Win+R, type services.msc, press Enter).",
                        "Scroll down and find the service named \"iCameraProxy\".",
                        "Right-click on it and select \"Start\".",
                        "Wait 30 seconds and check if the status changes to \"Running\".",
                        "If the service fails to start, check Windows Event Viewer for errors.",
                        "If the issue persists, restart the computer and try again.",
                        "Contact support if the service still does not start."
                )));

        put("PROXY_DEGRADED", new Resolution(
                "Proxy Running in Degraded Mode",
                "iCameraProxy service is running but JMX monitoring port is not responding.",
                Arrays.asList(
                        "Check if the iCameraProxy process is responding (not frozen).",
                        "Verify that JMX port (default 1099) is not blocked by firewall.",
                        "Open Windows Services, right-click \"iCameraProxy\" and select \"Restart\".",
                        "Wait 60 seconds for the service to fully initialize.",
                        "If the problem continues, check for port conflicts with other applications.",
                        "Contact support if the issue persists after restart."
                )));

        // --- HSQLDB ---
        put("HSQLDB_STATUS", new Resolution(
                "Database Service Down",
                "iCameraHSQLDB database service is not running.",
                Arrays.asList(
                        "Open Windows Services panel (press Win+R, type services.msc, press Enter).",
                        "Find the service named \"iCameraHSQLDB\".",
                        "Right-click and select \"Start\".",
                        "Wait 30 seconds for the database to initialize.",
                        "Verify the service status shows \"Running\".",
                        "If it fails to start, check Event Viewer for database errors.",
                        "Contact support if the database service cannot be started."
                )));

        put("HSQLDB_CONFLICT", new Resolution(
                "Database Connection Issue",
                "HSQLDB database is running and reachable, but iCameraProxy cannot connect to it.",
                Arrays.asList(
                        "Open Windows Services and restart the \"iCameraProxy\" service.",
                        "Wait 60 seconds for the proxy to re-establish the database connection.",
                        "If the issue persists, restart the \"iCameraHSQLDB\" service as well.",
                        "Check that no other application is using the HSQLDB port.",
                        "Verify the database port in hsqldb/server.properties matches the proxy configuration.",
                        "Contact support if the connection issue continues."
                )));

        // --- Process Resources ---
        put("PROCESS_CPU", new Resolution(
                "Proxy High CPU Usage",
                "iCameraProxy process is consuming excessive CPU resources.",
                Arrays.asList(
                        "Check the total number of cameras assigned to this proxy (recommended: 25 or fewer).",
                        "Check if any VMS software (e.g., Milestone, Hikvision) is running and competing for resources.",
                        "Open Windows Services and restart the \"iCameraProxy\" service.",
                        "If the CPU usage remains high after restart, reduce the camera count.",
                        "Contact support if the issue persists."
                )));

        put("PROCESS_MEMORY", new Resolution(
                "Proxy High Memory Usage",
                "iCameraProxy process memory consumption exceeds safe threshold.",
                Arrays.asList(
                        "Open Windows Services and restart the \"iCameraProxy\" service.",
                        "Monitor memory usage after restart for 5 minutes.",
                        "If memory grows rapidly again, check Windows Event Viewer for application errors.",
                        "Ensure the system has at least 8 GB RAM available.",
                        "Contact support if the issue recurs frequently."
                )));

        // --- System Resources ---
        put("CPU_USAGE", new Resolution(
                "System CPU Overloaded",
                "Overall system CPU usage exceeds 85%, which may affect iCamera performance.",
                Arrays.asList(
                        "Open Task Manager (Ctrl+Shift+Esc) and check which processes are using the most CPU.",
                        "Close any unnecessary applications (browsers, media players, etc.).",
                        "Check for Windows Updates running in the background.",
                        "If VMS software is detected, consider stopping it during exams.",
                        "Restart the computer if CPU usage does not decrease.",
                        "Contact IT support if the problem persists."
                )));

        put("MEMORY_USAGE", new Resolution(
                "System Memory Critical",
                "System RAM usage exceeds 85%, risking application instability.",
                Arrays.asList(
                        "Open Task Manager (Ctrl+Shift+Esc) and check memory-heavy processes.",
                        "Close all unnecessary applications and browser tabs.",
                        "If memory is still high, restart the computer.",
                        "Ensure the system meets minimum requirements (8 GB RAM recommended).",
                        "Contact IT support if memory usage remains critical after restart."
                )));

        put("DISK_USAGE", new Resolution(
                "Disk Space Low",
                "Drive storage is critically full, which can prevent file recording and uploads.",
                Arrays.asList(
                        "Open File Explorer and check available space on the affected drive.",
                        "Empty the Recycle Bin (right-click Recycle Bin on desktop, select \"Empty\").",
                        "Delete temporary files: press Win+R, type %temp%, press Enter, select all and delete.",
                        "Check the iCamera logs folder for large old log files and delete if not needed.",
                        "Run Disk Cleanup (search for \"Disk Cleanup\" in Start menu).",
                        "Contact IT support if disk space cannot be freed."
                )));

        // --- CCTV ---
        put("CCTV_STATUS", new Resolution(
                "Camera Inactive",
                "One or more CCTV cameras are not recording or uploading properly.",
                Arrays.asList(
                        "Check if the camera is powered on (indicator light should be visible).",
                        "Verify the network cable is properly connected to the camera and switch.",
                        "Try to ping the camera IP address from Command Prompt (ping <camera-ip>).",
                        "Check if the RTSP URL is correctly configured in the proxy.",
                        "Ensure the camera is set to H.264 encoding (not H.265 or MJPEG).",
                        "Restart the camera by unplugging power for 10 seconds, then reconnecting.",
                        "Contact support if the camera remains inactive."
                )));

        put("CCTV_COUNT", new Resolution(
                "Too Many Cameras",
                "Total camera count exceeds the recommended limit of 25 per proxy.",
                Arrays.asList(
                        "Note: Having more than 25 cameras per proxy may cause performance issues.",
                        "Contact support to discuss redistributing cameras across multiple proxy systems.",
                        "Monitor CPU and memory usage closely for signs of overload.",
                        "Consider adding another proxy system if additional cameras are needed."
                )));

        // --- Network ---
        put("UPLOAD_SPEED", new Resolution(
                "Network Upload Stopped",
                "Upload speed to cloud storage is zero, files are not being uploaded.",
                Arrays.asList(
                        "Check if the network cable is properly connected to the system.",
                        "Verify internet connectivity by opening a browser and visiting any website.",
                        "Check if other systems on the network have connectivity.",
                        "Restart the network router/switch if all systems are affected.",
                        "Check Windows firewall settings are not blocking iCamera.",
                        "Contact the network administrator if the issue persists."
                )));

        put("CONNECTIVITY", new Resolution(
                "Cloud Endpoint Unreachable",
                "Cannot connect to TCS iON cloud servers on port 443.",
                Arrays.asList(
                        "Verify internet connectivity by opening a browser.",
                        "Check if the URL is accessible from a browser (https://g01.tcsion.com).",
                        "Ensure firewall allows outbound connections on port 443 (HTTPS).",
                        "Check DNS resolution: open Command Prompt, type nslookup g01.tcsion.com.",
                        "If using a proxy server, ensure it is configured correctly.",
                        "Contact the network administrator to verify firewall and proxy settings."
                )));

        put("SSL_CERTIFICATE", new Resolution(
                "SSL Certificate Issue",
                "SSL/TLS certificate validation failed for a cloud endpoint.",
                Arrays.asList(
                        "Verify the system date and time are correct (incorrect time causes SSL failures).",
                        "Right-click the clock in the taskbar and select \"Adjust date/time\".",
                        "Enable \"Set time automatically\" if it is disabled.",
                        "If the date/time is correct, the server certificate may have expired.",
                        "Contact support with the exact error message for further investigation."
                )));

        // --- MAC Address ---
        put("MAC_ADDRESS", new Resolution(
                "MAC Address Changed",
                "The current network adapter MAC address differs from the previously registered one.",
                Arrays.asList(
                        "Verify no hardware changes were made (e.g., network card replaced).",
                        "If the change is expected, go to Application Details page and click \"Sync MAC\".",
                        "If no hardware changes were made, this could indicate an unauthorized modification.",
                        "Contact support to verify and update the MAC address registration."
                )));
    }

    /**
     * Returns resolution guidance for the given alert parameter.
     * Falls back to a generic resolution if no specific mapping exists.
     */
    public static Resolution getResolution(String parameter) {
        if (parameter == null) return getGenericResolution();
        Resolution r = RESOLUTIONS.get(parameter);
        return r != null ? r : getGenericResolution();
    }

    /**
     * Returns resolution guidance with status-aware title for proxy/HSQLDB alerts.
     */
    public static Resolution getResolution(String parameter, String status) {
        Resolution r = getResolution(parameter);
        if (r != null && status != null && "DEGRADED".equals(status) && "PROXY_STATUS".equals(parameter)) {
            return RESOLUTIONS.get("PROXY_DEGRADED");
        }
        return r;
    }

    private static Resolution getGenericResolution() {
        return new Resolution(
                "System Alert",
                "An issue has been detected that requires attention.",
                Arrays.asList(
                        "Note the alert message and timestamp.",
                        "Check if the issue resolves automatically within a few minutes.",
                        "If the issue persists, restart the affected service.",
                        "Contact support with the alert details if the problem continues."
                ));
    }

    private static void put(String key, Resolution resolution) {
        RESOLUTIONS.put(key, resolution);
    }
}
