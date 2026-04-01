package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.*;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Dashboard page – hierarchical health tiles with tri-state RAG
 * (GREEN/AMBER/RED)
 * supporting DEGRADED status for Proxy and HSQLDB.
 */
public class DashboardController implements Initializable {

    // --- Proxy Tile ---
    @FXML
    private Label lblProxyStatus;
    @FXML
    private Label lblProxyId;
    @FXML
    private Label lblProxyName;
    @FXML
    private VBox paneProxyTile;
    @FXML
    private Label lblServiceStatus;
    @FXML
    private Label lblJmxStatus;

    // --- HSQLDB Tile ---
    @FXML
    private Label lblHsqldbStatus;
    @FXML
    private VBox paneHsqldbTile;
    @FXML
    private Label lblHsqldbServiceStatus;
    @FXML
    private Label lblHsqldbDirect;

    // --- CCTV Tile ---
    @FXML
    private Label lblCctvActive;
    @FXML
    private Label lblCctvTotal;
    @FXML
    private ProgressBar pbCctvActive;
    @FXML
    private Label lblCctvWarning;
    @FXML
    private VBox paneCctvTile;

    // --- CPU ---
    @FXML
    private Label lblCpuPercent;
    @FXML
    private ProgressBar pbCpu;
    @FXML
    private VBox paneCpuTile;

    // --- Memory ---
    @FXML
    private Label lblMemPercent;
    @FXML
    private ProgressBar pbMem;
    @FXML
    private VBox paneMemTile;

    // --- Disk ---
    @FXML
    private Label lblDiskPercent;
    @FXML
    private Label lblDiskSummary;
    @FXML
    private ProgressBar pbDisk;
    @FXML
    private VBox paneDiskTile;

    // --- Network ---
    @FXML
    private Label lblNetworkSpeed;
    @FXML
    private VBox paneNetworkTile;

    // --- MAC ---
    @FXML
    private Label lblCurrentMac;
    @FXML
    private Label lblLastMac;
    @FXML
    private Label lblMacWarning;
    @FXML
    private VBox paneMacTile;

    private final DataStore store = DataStore.getInstance();
    private Timeline refreshTimeline;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(3), e -> refresh()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
        refresh(); // immediate first paint
    }

    private void refresh() {
        ProxyData pd = store.getProxyData();
        SystemMetrics sm = store.getSystemMetrics();

        // ---- Proxy tile (tri-state: UP/DEGRADED/DOWN) ----
        if (pd != null) {
            lblProxyId.setText("ID: " + pd.getProxyId());
            lblProxyName.setText(pd.getProxyName());

            String status = pd.getStatus() != null ? pd.getStatus() : "UNKNOWN";
            lblProxyStatus.setText(status);
            applyTriState(paneProxyTile, lblProxyStatus, status);

            // Sub-items: service status and JMX
            String svcStatus = pd.getServiceStatus() != null ? pd.getServiceStatus() : "UNKNOWN";
            lblServiceStatus.setText(svcStatus);
            applySubItemColor(lblServiceStatus, "RUNNING".equals(svcStatus));

            boolean jmxConnected = store.isJmxConnected();
            lblJmxStatus.setText(jmxConnected ? "Connected" : "Disconnected");
            applySubItemColor(lblJmxStatus, jmxConnected);

            // HSQLDB (tri-state based on multi-layer status)
            String hsqldbOverall = determineHsqldbOverallStatus(pd);
            lblHsqldbStatus.setText(hsqldbOverall);
            applyTriState(paneHsqldbTile, lblHsqldbStatus, hsqldbOverall);

            // HSQLDB sub-items
            String hsqlSvc = pd.getHsqldbStatus() != null ? pd.getHsqldbStatus() : "UNKNOWN";
            lblHsqldbServiceStatus.setText(hsqlSvc);
            applySubItemColor(lblHsqldbServiceStatus, "UP".equals(hsqlSvc));

            boolean directReachable = pd.isHsqldbDirectlyReachable();
            lblHsqldbDirect.setText(directReachable ? "Reachable" : "Unreachable");
            applySubItemColor(lblHsqldbDirect, directReachable);

            // MAC
            lblCurrentMac.setText("Current: " + nvl(pd.getCurrentMacAddress()));
            lblLastMac.setText("Last:    " + nvl(pd.getLastMacAddress()));
            lblMacWarning.setVisible(pd.isMacMismatch());

            // MAC tile coloring
            paneMacTile.getStyleClass().removeAll("tile-up", "tile-down", "tile-warn");
            paneMacTile.getStyleClass().add(pd.isMacMismatch() ? "tile-down" : "tile-up");
        } else {
            lblProxyStatus.setText("Awaiting data...");
            lblProxyId.setText("ID: --");
            lblProxyName.setText("--");
            lblServiceStatus.setText("--");
            lblJmxStatus.setText("--");
            lblHsqldbStatus.setText("--");
            lblHsqldbServiceStatus.setText("--");
            lblHsqldbDirect.setText("--");
            lblCurrentMac.setText("Current: --");
            lblLastMac.setText("Last: --");
            lblMacWarning.setVisible(false);
        }

        // ---- CCTV tile ----
        int total = store.getTotalCctvCount();
        long active = store.getActiveCctvCount();
        lblCctvActive.setText("Active: " + active);
        lblCctvTotal.setText("Total:  " + total);
        pbCctvActive.setProgress(total > 0 ? (double) active / total : 0);

        if (total > 25) {
            lblCctvWarning.setText("\u26A0 " + total + " cameras exceed recommended limit of 25");
            lblCctvWarning.setVisible(true);
            lblCctvWarning.setManaged(true);
        } else {
            lblCctvWarning.setVisible(false);
            lblCctvWarning.setManaged(false);
        }

        // CCTV tile coloring based on active ratio
        if (total > 0) {
            double activePct = (double) active / total * 100.0;
            applyThreshold(paneCctvTile, 100.0 - activePct); // invert: 0% inactive = green
        }

        // ---- System / CPU / Memory ----
        if (sm != null) {
            double cpu = sm.getSystemCpuPercent();
            double mem = sm.getMemoryUsedPercent();

            lblCpuPercent.setText(String.format("%.1f%%", cpu));
            pbCpu.setProgress(cpu / 100.0);
            applyThreshold(paneCpuTile, cpu);

            lblMemPercent.setText(String.format("%.1f%%", mem));
            pbMem.setProgress(mem / 100.0);
            applyThreshold(paneMemTile, mem);

            // Disk summary — only show proxy install drive, styled like CPU/Memory
            String proxyDrive = null;
            if (pd != null && pd.getInstallPath() != null && pd.getInstallPath().length() >= 2) {
                proxyDrive = pd.getInstallPath().substring(0, 2).toUpperCase();
            }

            SystemMetrics.DriveInfo targetDrive = null;
            for (SystemMetrics.DriveInfo di : sm.getDrives()) {
                if (proxyDrive != null && !di.getName().toUpperCase().startsWith(proxyDrive))
                    continue;
                targetDrive = di;
                break;
            }
            // Fallback: first drive if install path unknown
            if (targetDrive == null && !sm.getDrives().isEmpty()) {
                targetDrive = sm.getDrives().get(0);
            }

            if (targetDrive != null) {
                double usedPct = targetDrive.getUsedPercent();
                long usedGb = (targetDrive.getTotalSpaceMb() - targetDrive.getFreeSpaceMb()) / 1024;
                long totalGb = targetDrive.getTotalSpaceMb() / 1024;
                // Normalize drive letter: strip trailing backslash (e.g. "C:\" -> "C:")
                String driveName = targetDrive.getName();
                if (driveName != null && driveName.endsWith("\\")) {
                    driveName = driveName.substring(0, driveName.length() - 1);
                }

                lblDiskPercent.setText(String.format("%.1f%%", usedPct));
                lblDiskSummary.setText(driveName + ": " + usedGb + " / " + totalGb + " GB Used");
                pbDisk.setProgress(usedPct / 100.0);
                applyThreshold(paneDiskTile, usedPct);
            }
            // else: keep previous values – avoids flicker and tile resize

            // Network tile coloring based on speed
            double speed = sm.getNetworkSpeedMbps();
            lblNetworkSpeed.setText(String.format("%.2f MB/s", speed));
            paneNetworkTile.getStyleClass().removeAll("tile-up", "tile-down", "tile-warn");
            if (speed < 1.0) {
                paneNetworkTile.getStyleClass().add("tile-down");
            } else if (speed < 5.0) {
                paneNetworkTile.getStyleClass().add("tile-warn");
            } else {
                paneNetworkTile.getStyleClass().add("tile-up");
            }
        }
    }

    /**
     * Determines overall HSQLDB status from multi-layer checks.
     * UP = service UP + JMX reports UP + directly reachable
     * DEGRADED = service UP but JMX reports DOWN (or conflict scenario)
     * DOWN = service DOWN
     */
    private String determineHsqldbOverallStatus(ProxyData pd) {
        String svcStatus = pd.getHsqldbStatus();
        String jmxStatus = pd.getHsqldbJmxStatus();
        boolean directReachable = pd.isHsqldbDirectlyReachable();

        if (!"UP".equals(svcStatus))
            return "DOWN";

        // Service is UP
        if ("DOWN".equals(jmxStatus) && directReachable) {
            // Service running, DB reachable, but proxy can't connect = DEGRADED
            return "DEGRADED";
        }
        if ("DOWN".equals(jmxStatus)) {
            return "DEGRADED";
        }
        return "UP";
    }

    /**
     * Applies tri-state coloring: UP=GREEN, DEGRADED=AMBER, DOWN/UNKNOWN=RED
     */
    private void applyTriState(Region tile, Label label, String status) {
        tile.getStyleClass().removeAll("tile-up", "tile-down", "tile-warn");
        label.getStyleClass().removeAll("text-green", "text-red", "text-yellow");

        switch (status) {
            case "UP":
                tile.getStyleClass().add("tile-up");
                label.getStyleClass().add("text-green");
                break;
            case "DEGRADED":
                tile.getStyleClass().add("tile-warn");
                label.getStyleClass().add("text-yellow");
                break;
            default: // DOWN, UNKNOWN
                tile.getStyleClass().add("tile-down");
                label.getStyleClass().add("text-red");
                break;
        }
    }

    /**
     * Colors sub-item labels (service, JMX, etc.) based on OK/not-OK.
     */
    private void applySubItemColor(Label label, boolean ok) {
        label.getStyleClass().removeAll("text-green", "text-red");
        label.getStyleClass().add(ok ? "text-green" : "text-red");
    }

    private void applyThreshold(Region tile, double value) {
        tile.getStyleClass().removeAll("tile-up", "tile-down", "tile-warn");
        tile.getStyleClass().add(value > 85 ? "tile-down" : value > 60 ? "tile-warn" : "tile-up");
    }

    private String nvl(String s) {
        return s != null ? s : "N/A";
    }
}
