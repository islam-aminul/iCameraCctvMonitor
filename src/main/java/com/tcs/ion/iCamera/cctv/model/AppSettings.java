package com.tcs.ion.iCamera.cctv.model;

import java.util.ArrayList;
import java.util.List;

/**
 * User-configurable application settings (persisted to disk and editable via Settings page).
 */
public class AppSettings {

    // JMX
    private String jmxHost = "localhost";
    private int jmxBasePort = 1999;
    private int jmxMaxPortRetries = 5;

    // Polling
    private int pollIntervalSeconds = 60;

    // ffprobe
    private String ffprobePath = ".\\ffprobe.exe"; // to be updated with actual path

    // Theme
    private String theme = "DARK"; // DARK | LIGHT
    private String accentColor = "#2196F3"; // blue

    // Dashboard tiles visibility
    private List<String> dashboardTiles = new ArrayList<>();

    // Export
    private String exportPath = System.getProperty("user.home") + "\\iCameraExports";
    private String exportFormat = "XLSX"; // XLSX | CSV | JSON

    // Jetty REST port
    private int jettyPort = 8080;

    // Font
    private String fontFamily = "Segoe UI";
    private double fontSize = 13.0;

    public AppSettings() {
        // Default visible dashboard tiles
        dashboardTiles.add("PROXY_STATUS");
        dashboardTiles.add("HSQLDB_STATUS");
        dashboardTiles.add("CCTV_STATUS");
        dashboardTiles.add("SYSTEM_HEALTH");
        dashboardTiles.add("CPU_USAGE");
        dashboardTiles.add("MEMORY_USAGE");
        dashboardTiles.add("DISK_USAGE");
        dashboardTiles.add("NETWORK_STATUS");
        dashboardTiles.add("MAC_DETAILS");
    }

    // ---- Getters & Setters ----

    public String getJmxHost() { return jmxHost; }
    public void setJmxHost(String jmxHost) { this.jmxHost = jmxHost; }

    public int getJmxBasePort() { return jmxBasePort; }
    public void setJmxBasePort(int jmxBasePort) { this.jmxBasePort = jmxBasePort; }

    public int getJmxMaxPortRetries() { return jmxMaxPortRetries; }
    public void setJmxMaxPortRetries(int jmxMaxPortRetries) { this.jmxMaxPortRetries = jmxMaxPortRetries; }

    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public void setPollIntervalSeconds(int pollIntervalSeconds) { this.pollIntervalSeconds = pollIntervalSeconds; }

    public String getFfprobePath() { return ffprobePath; }
    public void setFfprobePath(String ffprobePath) { this.ffprobePath = ffprobePath; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getAccentColor() { return accentColor; }
    public void setAccentColor(String accentColor) { this.accentColor = accentColor; }

    public List<String> getDashboardTiles() { return dashboardTiles; }
    public void setDashboardTiles(List<String> dashboardTiles) { this.dashboardTiles = dashboardTiles; }

    public String getExportPath() { return exportPath; }
    public void setExportPath(String exportPath) { this.exportPath = exportPath; }

    public String getExportFormat() { return exportFormat; }
    public void setExportFormat(String exportFormat) { this.exportFormat = exportFormat; }

    public int getJettyPort() { return jettyPort; }
    public void setJettyPort(int jettyPort) { this.jettyPort = jettyPort; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }
}
