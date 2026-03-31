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
 * Dashboard page – hierarchical health tiles with tri-state RAG (GREEN/AMBER/RED)
 * supporting DEGRADED status for Proxy and HSQLDB.
 */
public class DashboardController implements Initializable {

    // --- Proxy Tile ---
    @FXML private Label lblProxyStatus;
    @FXML private Label lblProxyId;
    @FXML private Label lblProxyName;
    @FXML private VBox paneProxyTile;
    @FXML private Label lblServiceStatus;
    @FXML private Label lblJmxStatus;

    // --- HSQLDB Tile ---
    @FXML private Label lblHsqldbStatus;
    @FXML private VBox paneHsqldbTile;
    @FXML private Label lblHsqldbServiceStatus;
    @FXML private Label lblHsqldbJmxStatus;
    @FXML private Label lblHsqldbDirect;

    // --- CCTV Tile ---
    @FXML private Label lblCctvActive;
    @FXML private Label lblCctvTotal;
    @FXML private ProgressBar pbCctvActive;
    @FXML private Label lblCctvWarning;

    // --- CPU ---
    @FXML private Label lblCpuPercent;
    @FXML private ProgressBar pbCpu;
    @FXML private VBox paneCpuTile;

    // --- Memory ---
    @FXML private Label lblMemPercent;
    @FXML private ProgressBar pbMem;
    @FXML private VBox paneMemTile;

    // --- Disk ---
    @FXML private Label lblDiskSummary;
    @FXML private VBox vboxDisks;
    @FXML private VBox paneDiskTile;

    // --- Network ---
    @FXML private Label lblNetworkSpeed;
    @FXML private VBox paneNetworkTile;

    // --- MAC ---
    @FXML private Label lblCurrentMac;
    @FXML private Label lblLastMac;
    @FXML private Label lblMacWarning;

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

            String hsqlJmx = pd.getHsqldbJmxStatus() != null ? pd.getHsqldbJmxStatus() : "N/A";
            lblHsqldbJmxStatus.setText(hsqlJmx);
            applySubItemColor(lblHsqldbJmxStatus, "UP".equals(hsqlJmx));

            boolean directReachable = pd.isHsqldbDirectlyReachable();
            lblHsqldbDirect.setText(directReachable ? "Reachable" : "Unreachable");
            applySubItemColor(lblHsqldbDirect, directReachable);

            // MAC
            lblCurrentMac.setText("Current: " + nvl(pd.getCurrentMacAddress()));
            lblLastMac.setText("Last:    " + nvl(pd.getLastMacAddress()));
            lblMacWarning.setVisible(pd.isMacMismatch());
        } else {
            lblProxyStatus.setText("Awaiting data...");
            lblProxyId.setText("ID: --");
            lblProxyName.setText("--");
            lblServiceStatus.setText("--");
            lblJmxStatus.setText("--");
            lblHsqldbStatus.setText("--");
            lblHsqldbServiceStatus.setText("--");
            lblHsqldbJmxStatus.setText("--");
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

            // Disk summary — only show proxy install drive
            String proxyDrive = null;
            if (pd != null && pd.getInstallPath() != null && pd.getInstallPath().length() >= 2) {
                proxyDrive = pd.getInstallPath().substring(0, 2).toUpperCase();
            }

            vboxDisks.getChildren().clear();
            boolean driveFound = false;
            for (SystemMetrics.DriveInfo di : sm.getDrives()) {
                if (proxyDrive != null && !di.getName().toUpperCase().startsWith(proxyDrive)) continue;

                HBox row = new HBox(8);
                Label lName = new Label(di.getName());
                lName.getStyleClass().add("tile-sublabel");
                ProgressBar pb = new ProgressBar(di.getUsedPercent() / 100.0);
                pb.setPrefWidth(120);
                Label lPct = new Label(String.format("%.1f%%", di.getUsedPercent()));
                lPct.getStyleClass().add(di.getUsedPercent() > 85 ? "text-red"
                        : di.getUsedPercent() > 60 ? "text-yellow" : "text-green");
                row.getChildren().addAll(lName, pb, lPct);
                vboxDisks.getChildren().add(row);
                applyThreshold(paneDiskTile, di.getUsedPercent());
                driveFound = true;
            }

            // Fallback: if installPath unknown, show all drives
            if (!driveFound && proxyDrive == null) {
                for (SystemMetrics.DriveInfo di : sm.getDrives()) {
                    HBox row = new HBox(8);
                    Label lName = new Label(di.getName());
                    lName.getStyleClass().add("tile-sublabel");
                    ProgressBar pb = new ProgressBar(di.getUsedPercent() / 100.0);
                    pb.setPrefWidth(120);
                    Label lPct = new Label(String.format("%.1f%%", di.getUsedPercent()));
                    lPct.getStyleClass().add(di.getUsedPercent() > 85 ? "text-red"
                            : di.getUsedPercent() > 60 ? "text-yellow" : "text-green");
                    row.getChildren().addAll(lName, pb, lPct);
                    vboxDisks.getChildren().add(row);
                }
            }

            // Network
            lblNetworkSpeed.setText(String.format("%.2f MB/s", sm.getNetworkSpeedMbps()));
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

        if (!"UP".equals(svcStatus)) return "DOWN";

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

    private String nvl(String s) { return s != null ? s : "N/A"; }
}
