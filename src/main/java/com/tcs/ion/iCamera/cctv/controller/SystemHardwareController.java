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
 * System Hardware Monitor – CPU, RAM, Disk details with real-time utilisation bars.
 */
public class SystemHardwareController implements Initializable {

    // CPU Info
    @FXML private Label lblCpuName;
    @FXML private Label lblCpuCores;
    @FXML private Label lblCpuThreads;
    @FXML private Label lblCpuFreq;
    @FXML private Label lblCpuUsage;
    @FXML private ProgressBar pbCpuSystem;
    @FXML private Label lblProxyCpuUsage;
    @FXML private ProgressBar pbCpuProxy;

    // RAM Info
    @FXML private Label lblRamTotal;
    @FXML private Label lblRamAvail;
    @FXML private Label lblRamType;
    @FXML private Label lblRamUsage;
    @FXML private ProgressBar pbRam;
    @FXML private Label lblProxyRamUsage;

    // Disk
    @FXML private VBox vboxDrives;

    // Status indicator
    @FXML private Label lblOverallHealth;

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
        SystemMetrics sm = store.getSystemMetrics();
        ProxyData pd     = store.getProxyData();

        if (sm == null) { lblCpuName.setText("Waiting for data..."); return; }

        // CPU static info
        lblCpuName.setText(nvl(sm.getCpuName(), "N/A"));
        lblCpuCores.setText("Physical Cores: " + sm.getPhysicalCores());
        lblCpuThreads.setText("Logical Threads: " + sm.getLogicalCores());
        lblCpuFreq.setText(String.format("Max Frequency: %.2f GHz", sm.getCpuMaxFreqGhz()));

        // CPU utilisation
        double cpu = sm.getSystemCpuPercent();
        lblCpuUsage.setText(String.format("System CPU: %.1f%%", cpu));
        pbCpuSystem.setProgress(cpu / 100.0);
        setAlertStyle(lblCpuUsage, cpu);

        if (pd != null) {
            double proxyCpu = pd.getProcessCpuPercent();
            lblProxyCpuUsage.setText(String.format("iCamera Process CPU: %.1f%%", proxyCpu));
            pbCpuProxy.setProgress(proxyCpu / 100.0);
            setAlertStyle(lblProxyCpuUsage, proxyCpu);
        }

        // RAM
        double totalGb  = sm.getTotalRamBytes() / (1024.0 * 1024 * 1024);
        double availGb  = sm.getAvailableRamBytes() / (1024.0 * 1024 * 1024);
        double usedPct  = sm.getMemoryUsedPercent();
        lblRamTotal.setText(String.format("Total RAM: %.1f GB", totalGb));
        lblRamAvail.setText(String.format("Available: %.1f GB", availGb));
        lblRamType.setText("RAM Type: " + nvl(sm.getRamType(), "N/A"));
        lblRamUsage.setText(String.format("Used: %.1f%%", usedPct));
        pbRam.setProgress(usedPct / 100.0);
        setAlertStyle(lblRamUsage, usedPct);

        if (pd != null) {
            lblProxyRamUsage.setText(String.format("iCamera Process RAM: %.1f MB", pd.getProcessMemoryMb()));
        }

        // Drives
        vboxDrives.getChildren().clear();
        for (SystemMetrics.DriveInfo di : sm.getDrives()) {
            VBox driveBox = new VBox(4);
            driveBox.getStyleClass().add("drive-box");

            HBox header = new HBox(8);
            Label lName = new Label(di.getName());
            lName.getStyleClass().add("tile-header");
            String rpmInfo = di.getRpm() != null ? " [" + di.getRpm() + "]" : "";
            lName.setText(di.getName() + rpmInfo);
            header.getChildren().add(lName);

            HBox details = new HBox(16);
            Label lTotal = new Label("Total: " + di.getTotalSpaceMb() + " MB");
            Label lFree  = new Label("Free: " + di.getFreeSpaceMb() + " MB");
            Label lUsed  = new Label(String.format("Used: %.1f%%", di.getUsedPercent()));
            lUsed.getStyleClass().add(di.getUsedPercent() > 85 ? "text-red" : "text-green");
            details.getChildren().addAll(lTotal, lFree, lUsed);

            ProgressBar pb = new ProgressBar(di.getUsedPercent() / 100.0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.getStyleClass().add(di.getUsedPercent() > 85 ? "progress-red" : "progress-normal");

            driveBox.getChildren().addAll(header, details, pb);
            vboxDrives.getChildren().add(driveBox);
        }

        // Overall health
        boolean healthy = sm.isHealthy();
        lblOverallHealth.setText("System Health: " + (healthy ? "STABLE" : "CRITICAL"));
        lblOverallHealth.getStyleClass().removeAll("text-green", "text-red");
        lblOverallHealth.getStyleClass().add(healthy ? "text-green" : "text-red");
    }

    private void setAlertStyle(Label lbl, double val) {
        lbl.getStyleClass().removeAll("text-green", "text-yellow", "text-red");
        lbl.getStyleClass().add(val > 85 ? "text-red" : val > 60 ? "text-yellow" : "text-green");
    }

    private String nvl(String s, String def) { return (s != null && !s.isEmpty()) ? s : def; }
}
