package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
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
 * Proxy details page – shows proxy status, service, JMX connection, CPU/memory.
 */
public class ProxyDetailsController implements Initializable {

    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private Label lblProxyId;
    @FXML private Label lblProxyName;
    @FXML private Label lblTcCode;
    @FXML private Label lblProxyStatus;
    @FXML private Label lblServiceStatus;
    @FXML private Label lblJmxStatus;
    @FXML private Label lblStartTime;
    @FXML private Label lblUptime;
    @FXML private Label lblDownReason;
    @FXML private Label lblCpuUsage;
    @FXML private Label lblMemUsage;
    @FXML private ProgressBar pbProxyCpu;
    @FXML private ProgressBar pbProxyMem;
    @FXML private Pane paneProxyStatus;
    @FXML private HBox lblDownReasonRow;

    private final DataStore store = DataStore.getInstance();
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

        lblProxyId.setText(String.valueOf(pd.getProxyId()));
        lblProxyName.setText(nvl(pd.getProxyName()));
        lblTcCode.setText(nvl(pd.getTcCode()));

        // Proxy status
        boolean up = "UP".equals(pd.getStatus());
        lblProxyStatus.setText(nvl(pd.getStatus(), "UNKNOWN"));
        styleStatus(paneProxyStatus, lblProxyStatus, up);

        // Service status
        boolean svcRunning = "RUNNING".equals(pd.getServiceStatus());
        lblServiceStatus.setText(nvl(pd.getServiceStatus(), "UNKNOWN"));
        lblServiceStatus.getStyleClass().removeAll("text-green", "text-red", "text-yellow");
        if (!svcRunning) {
            lblServiceStatus.getStyleClass().add("NOT_FOUND".equals(pd.getServiceStatus()) ? "text-yellow" : "text-red");
        } else {
            lblServiceStatus.getStyleClass().add("text-green");
        }

        // JMX Connection Status
        boolean jmxConnected = pd.getSnapshotTime() != null && !pd.isStale();
        lblJmxStatus.setText(jmxConnected ? "Connected" : "Disconnected");
        lblJmxStatus.getStyleClass().removeAll("text-green", "text-red");
        lblJmxStatus.getStyleClass().add(jmxConnected ? "text-green" : "text-red");

        // Start time & uptime
        if (pd.getStartTimeMillis() > 0) {
            lblStartTime.setText(DTF.format(Instant.ofEpochMilli(pd.getStartTimeMillis())));
            lblUptime.setText(pd.getUptimeString());
        } else {
            lblStartTime.setText("N/A");
            lblUptime.setText("N/A");
        }

        // Down reason
        boolean showDownReason = !up || !"RUNNING".equals(pd.getServiceStatus());
        lblDownReasonRow.setVisible(showDownReason);
        lblDownReason.setText(nvl(pd.getDownReason(), "Unknown"));

        // CPU / Memory
        double cpu = pd.getProcessCpuPercent();
        double mem = pd.getProcessMemoryMb();
        lblCpuUsage.setText(String.format("Process CPU: %.2f%%", cpu));
        pbProxyCpu.setProgress(cpu / 100.0);
        lblCpuUsage.getStyleClass().removeAll("text-red", "text-green", "text-yellow");
        lblCpuUsage.getStyleClass().add(cpu > 85 ? "text-red" : cpu > 60 ? "text-yellow" : "text-green");

        lblMemUsage.setText(String.format("Process Memory: %.1f MB", mem));
        pbProxyMem.setProgress(Math.min(mem / 2048.0, 1.0));
        lblMemUsage.getStyleClass().removeAll("text-red", "text-green", "text-yellow");
        lblMemUsage.getStyleClass().add(mem > 2048 ? "text-red" : mem > 1024 ? "text-yellow" : "text-green");
    }

    private void styleStatus(Pane tile, Label label, boolean ok) {
        tile.getStyleClass().removeAll("tile-up", "tile-down");
        tile.getStyleClass().add(ok ? "tile-up" : "tile-down");
        label.getStyleClass().removeAll("text-green", "text-red");
        label.getStyleClass().add(ok ? "text-green" : "text-red");
    }

    private String nvl(String s, String def) { return (s != null && !s.isEmpty()) ? s : def; }
    private String nvl(String s) { return nvl(s, "N/A"); }
}
