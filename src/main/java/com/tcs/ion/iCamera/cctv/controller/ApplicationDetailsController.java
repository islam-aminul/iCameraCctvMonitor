package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import com.tcs.ion.iCamera.cctv.service.HttpService;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Application Details page – Proxy, HSQLDB, service status, MAC details.
 */
public class ApplicationDetailsController implements Initializable {

    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    // Proxy section
    @FXML private Label lblProxyId;
    @FXML private Label lblProxyName;
    @FXML private Label lblTcCode;
    @FXML private Label lblProxyStatus;
    @FXML private Label lblServiceStatus;
    @FXML private Label lblStartTime;
    @FXML private Label lblUptime;
    @FXML private Label lblDownReason;
    @FXML private Label lblCpuUsage;
    @FXML private Label lblMemUsage;
    @FXML private ProgressBar pbProxyCpu;
    @FXML private ProgressBar pbProxyMem;
    @FXML private Pane  paneProxyStatus;
    @FXML private Label lblDownReasonRow;

    // HSQLDB section
    @FXML private Label lblHsqldbStatus;
    @FXML private Label lblHsqldbUptime;
    @FXML private Label lblHsqldbStartTime;
    @FXML private Pane  paneHsqldbStatus;

    // MAC section
    @FXML private Label  lblCurrentMac;
    @FXML private Label  lblLastMac;
    @FXML private Button btnSyncMac;
    @FXML private Pane   paneMacSection;
    @FXML private Label  lblMacMismatch;

    private final DataStore store = DataStore.getInstance();
    private final HttpService httpService = new HttpService();
    private Timeline timeline;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refresh();
    }

    private void refresh() {
        ProxyData pd = store.getProxyData();
        if (pd == null) {
            lblProxyStatus.setText("No JMX data available");
            return;
        }

        lblProxyId.setText("Proxy ID: " + pd.getProxyId());
        lblProxyName.setText("Name: " + pd.getProxyName());
        lblTcCode.setText("TC Code: " + pd.getTcCode());

        // Proxy status
        boolean up = "UP".equals(pd.getStatus());
        lblProxyStatus.setText("Proxy: " + pd.getStatus());
        styleStatus(paneProxyStatus, lblProxyStatus, up);

        // Service status (from Windows SCM)
        boolean svcRunning = "RUNNING".equals(pd.getServiceStatus());
        lblServiceStatus.setText("Service: " + nvl(pd.getServiceStatus(), "UNKNOWN"));
        if (!svcRunning) {
            lblServiceStatus.getStyleClass().removeAll("text-green", "text-red", "text-yellow");
            lblServiceStatus.getStyleClass().add("NOT_FOUND".equals(pd.getServiceStatus()) ? "text-yellow" : "text-red");
        } else {
            lblServiceStatus.getStyleClass().removeAll("text-red", "text-yellow");
            lblServiceStatus.getStyleClass().add("text-green");
        }

        // Start time & uptime
        if (pd.getStartTimeMillis() > 0) {
            lblStartTime.setText("Started: " + DTF.format(Instant.ofEpochMilli(pd.getStartTimeMillis())));
            lblUptime.setText("Uptime: " + pd.getUptimeString());
        } else {
            lblStartTime.setText("Started: N/A");
            lblUptime.setText("Uptime: N/A");
        }

        // Down reason
        boolean showDownReason = !up || !"RUNNING".equals(pd.getServiceStatus());
        lblDownReasonRow.setVisible(showDownReason);
        lblDownReason.setText("Reason: " + nvl(pd.getDownReason(), "Unknown"));
        lblDownReason.getStyleClass().add("text-red");

        // CPU / Memory
        double cpu = pd.getProcessCpuPercent();
        double mem = pd.getProcessMemoryMb();
        lblCpuPercent(cpu);
        lblMemMb(mem);

        // HSQLDB
        boolean hsqlUp = "UP".equals(pd.getHsqldbStatus());
        lblHsqldbStatus.setText("HSQLDB: " + nvl(pd.getHsqldbStatus(), "UNKNOWN"));
        styleStatus(paneHsqldbStatus, lblHsqldbStatus, hsqlUp);
        if (pd.getHsqldbStartTimeMillis() > 0) {
            lblHsqldbStartTime.setText("Started: " + DTF.format(Instant.ofEpochMilli(pd.getHsqldbStartTimeMillis())));
            long uptimeMs = System.currentTimeMillis() - pd.getHsqldbStartTimeMillis();
            lblHsqldbUptime.setText("Uptime: " + formatUptime(uptimeMs));
        }

        // MAC
        lblCurrentMac.setText("Current MAC: " + nvl(pd.getCurrentMacAddress(), "N/A"));
        lblLastMac.setText("Last MAC:    " + nvl(pd.getLastMacAddress(), "N/A"));
        boolean mismatch = pd.isMacMismatch();
        lblMacMismatch.setVisible(mismatch);
        btnSyncMac.setVisible(mismatch);
    }

    @FXML
    private void onSyncMac() {
        ProxyData pd = store.getProxyData();
        if (pd == null) return;
        btnSyncMac.setDisable(true);
        btnSyncMac.setText("Syncing...");
        new Thread(() -> {
            boolean ok = httpService.syncMacAddress(pd.getProxyId(), pd.getCurrentMacAddress());
            javafx.application.Platform.runLater(() -> {
                btnSyncMac.setDisable(false);
                btnSyncMac.setText("Sync MAC");
                Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                alert.setTitle("MAC Sync");
                alert.setHeaderText(ok ? "MAC address synced successfully" : "MAC sync failed");
                alert.showAndWait();
            });
        }).start();
    }

    private void styleStatus(Pane tile, Label label, boolean ok) {
        tile.getStyleClass().removeAll("tile-up", "tile-down");
        tile.getStyleClass().add(ok ? "tile-up" : "tile-down");
        label.getStyleClass().removeAll("text-green", "text-red");
        label.getStyleClass().add(ok ? "text-green" : "text-red");
    }

    private void lblCpuPercent(double val) {
        lblCpuUsage.setText(String.format("Process CPU: %.2f%%", val));
        pbProxyCpu.setProgress(val / 100.0);
        lblCpuUsage.getStyleClass().removeAll("text-red", "text-green", "text-yellow");
        lblCpuUsage.getStyleClass().add(val > 85 ? "text-red" : val > 60 ? "text-yellow" : "text-green");
    }

    private void lblMemMb(double mb) {
        lblMemUsage.setText(String.format("Process Memory: %.1f MB", mb));
        pbProxyMem.setProgress(Math.min(mb / 2048.0, 1.0));
        lblMemUsage.getStyleClass().removeAll("text-red", "text-green", "text-yellow");
        lblMemUsage.getStyleClass().add(mb > 2048 ? "text-red" : mb > 1024 ? "text-yellow" : "text-green");
    }

    private String nvl(String s, String def) { return (s != null && !s.isEmpty()) ? s : def; }
    private String nvl(String s) { return nvl(s, "N/A"); }

    private String formatUptime(long ms) {
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h " + (m % 60) + "m";
        return h + "h " + (m % 60) + "m " + (s % 60) + "s";
    }
}
