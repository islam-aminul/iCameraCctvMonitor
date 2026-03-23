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
 * Dashboard page – tiles showing high-level health summary.
 */
public class DashboardController implements Initializable {

    // --- Proxy Tile ---
    @FXML private Label lblProxyStatus;
    @FXML private Label lblProxyId;
    @FXML private Label lblProxyName;
    @FXML private Pane  paneProxyTile;

    // --- HSQLDB Tile ---
    @FXML private Label lblHsqldbStatus;
    @FXML private Pane  paneHsqldbTile;

    // --- CCTV Tile ---
    @FXML private Label lblCctvActive;
    @FXML private Label lblCctvTotal;
    @FXML private ProgressBar pbCctvActive;

    // --- System Health ---
    @FXML private Label lblSystemHealth;
    @FXML private Pane  paneSystemTile;

    // --- CPU ---
    @FXML private Label       lblCpuPercent;
    @FXML private ProgressBar pbCpu;
    @FXML private Pane        paneCpuTile;

    // --- Memory ---
    @FXML private Label       lblMemPercent;
    @FXML private ProgressBar pbMem;
    @FXML private Pane        paneMemTile;

    // --- Disk ---
    @FXML private Label lblDiskSummary;
    @FXML private VBox  vboxDisks;

    // --- Network ---
    @FXML private Label lblNetworkSpeed;
    @FXML private Pane  paneNetworkTile;

    // --- MAC ---
    @FXML private Label lblCurrentMac;
    @FXML private Label lblLastMac;
    @FXML private Label lblMacWarning;

    // --- TC Code Banner ---
    @FXML private Label lblTcCodeBanner;

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

        // ---- Proxy tile ----
        if (pd != null) {
            lblProxyId.setText("ID: " + pd.getProxyId());
            lblProxyName.setText(pd.getProxyName());
            lblTcCodeBanner.setText("TC Code: " + pd.getTcCode());
            boolean up = "UP".equals(pd.getStatus());
            lblProxyStatus.setText(up ? "UP" : pd.getStatus());
            setStatusStyle(paneProxyTile, lblProxyStatus, up);

            // HSQLDB
            boolean hsqlUp = "UP".equals(pd.getHsqldbStatus());
            lblHsqldbStatus.setText(hsqlUp ? "UP" : pd.getHsqldbStatus());
            setStatusStyle(paneHsqldbTile, lblHsqldbStatus, hsqlUp);

            // MAC
            lblCurrentMac.setText("Current: " + nvl(pd.getCurrentMacAddress()));
            lblLastMac.setText("Last:    " + nvl(pd.getLastMacAddress()));
            lblMacWarning.setVisible(pd.isMacMismatch());
        } else {
            lblProxyStatus.setText("No Data");
        }

        // ---- CCTV tile ----
        int total  = store.getTotalCctvCount();
        long active = store.getActiveCctvCount();
        lblCctvActive.setText("Active: " + active);
        lblCctvTotal.setText("Total:  " + total);
        pbCctvActive.setProgress(total > 0 ? (double) active / total : 0);

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

            // Disk summary
            vboxDisks.getChildren().clear();
            for (SystemMetrics.DriveInfo di : sm.getDrives()) {
                HBox row = new HBox(8);
                Label lName  = new Label(di.getName());
                lName.getStyleClass().add("tile-sublabel");
                ProgressBar pb = new ProgressBar(di.getUsedPercent() / 100.0);
                pb.setPrefWidth(120);
                Label lPct = new Label(String.format("%.1f%%", di.getUsedPercent()));
                lPct.getStyleClass().add(di.getUsedPercent() > 85 ? "text-red" : "text-green");
                row.getChildren().addAll(lName, pb, lPct);
                vboxDisks.getChildren().add(row);
            }

            // Network
            lblNetworkSpeed.setText(String.format("%.2f MB/s", sm.getNetworkSpeedMbps()));

            // System health
            boolean healthy = sm.isHealthy();
            lblSystemHealth.setText(healthy ? "STABLE" : "UNSTABLE");
            setStatusStyle(paneSystemTile, lblSystemHealth, healthy);
        }
    }

    private void setStatusStyle(Pane tile, Label label, boolean ok) {
        tile.getStyleClass().removeAll("tile-up", "tile-down");
        tile.getStyleClass().add(ok ? "tile-up" : "tile-down");
        label.getStyleClass().removeAll("text-green", "text-red");
        label.getStyleClass().add(ok ? "text-green" : "text-red");
    }

    private void applyThreshold(Pane tile, double value) {
        tile.getStyleClass().removeAll("tile-up", "tile-down", "tile-warn");
        tile.getStyleClass().add(value > 85 ? "tile-down" : value > 60 ? "tile-warn" : "tile-up");
    }

    private String nvl(String s) { return s != null ? s : "N/A"; }
}
