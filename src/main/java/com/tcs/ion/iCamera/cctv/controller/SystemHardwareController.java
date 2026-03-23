package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.*;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.net.URL;
import java.util.*;

/**
 * System Hardware Monitor – CPU, RAM, Disk details with real-time utilisation bars
 * and top-5 process tables by CPU, Memory, and Disk I/O.
 */
public class SystemHardwareController implements Initializable {

    // --- CPU Info ---
    @FXML private Label lblCpuName;
    @FXML private Label lblCpuCores;
    @FXML private Label lblCpuThreads;
    @FXML private Label lblCpuFreq;
    @FXML private Label lblCpuUsage;
    @FXML private ProgressBar pbCpuSystem;
    @FXML private Label lblProxyCpuUsage;
    @FXML private ProgressBar pbCpuProxy;

    // --- RAM Info ---
    @FXML private Label lblRamTotal;
    @FXML private Label lblRamAvail;
    @FXML private Label lblRamType;
    @FXML private Label lblRamUsage;
    @FXML private ProgressBar pbRam;
    @FXML private Label lblProxyRamUsage;

    // --- Disk ---
    @FXML private VBox vboxDrives;

    // --- Overall health ---
    @FXML private Label lblOverallHealth;

    // --- Top-5 CPU processes ---
    @FXML private TableView<ProcRow>           tblTopCpu;
    @FXML private TableColumn<ProcRow, String> cpuColPid;
    @FXML private TableColumn<ProcRow, String> cpuColName;
    @FXML private TableColumn<ProcRow, String> cpuColUser;
    @FXML private TableColumn<ProcRow, String> cpuColCpu;
    @FXML private TableColumn<ProcRow, String> cpuColMem;

    // --- Top-5 Memory processes ---
    @FXML private TableView<ProcRow>           tblTopMem;
    @FXML private TableColumn<ProcRow, String> memColPid;
    @FXML private TableColumn<ProcRow, String> memColName;
    @FXML private TableColumn<ProcRow, String> memColUser;
    @FXML private TableColumn<ProcRow, String> memColMem;
    @FXML private TableColumn<ProcRow, String> memColCpu;

    // --- Top-5 Disk I/O processes ---
    @FXML private TableView<ProcRow>           tblTopDisk;
    @FXML private TableColumn<ProcRow, String> diskColPid;
    @FXML private TableColumn<ProcRow, String> diskColName;
    @FXML private TableColumn<ProcRow, String> diskColUser;
    @FXML private TableColumn<ProcRow, String> diskColRead;
    @FXML private TableColumn<ProcRow, String> diskColWrite;
    @FXML private TableColumn<ProcRow, String> diskColMem;

    private final DataStore store = DataStore.getInstance();
    private Timeline timeline;

    private final ObservableList<ProcRow> cpuRows  = FXCollections.observableArrayList();
    private final ObservableList<ProcRow> memRows  = FXCollections.observableArrayList();
    private final ObservableList<ProcRow> diskRows = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupProcessTables();
        timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refresh();
    }

    private void setupProcessTables() {
        // CPU table
        cpuColPid.setCellValueFactory(d -> d.getValue().pid);
        cpuColName.setCellValueFactory(d -> d.getValue().name);
        cpuColUser.setCellValueFactory(d -> d.getValue().user);
        cpuColCpu.setCellValueFactory(d -> d.getValue().cpu);
        cpuColMem.setCellValueFactory(d -> d.getValue().memory);
        applyAlertColouring(cpuColCpu, 20.0, 5.0);
        tblTopCpu.setItems(cpuRows);
        tblTopCpu.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Memory table
        memColPid.setCellValueFactory(d -> d.getValue().pid);
        memColName.setCellValueFactory(d -> d.getValue().name);
        memColUser.setCellValueFactory(d -> d.getValue().user);
        memColMem.setCellValueFactory(d -> d.getValue().memory);
        memColCpu.setCellValueFactory(d -> d.getValue().cpu);
        tblTopMem.setItems(memRows);
        tblTopMem.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Disk table
        diskColPid.setCellValueFactory(d -> d.getValue().pid);
        diskColName.setCellValueFactory(d -> d.getValue().name);
        diskColUser.setCellValueFactory(d -> d.getValue().user);
        diskColRead.setCellValueFactory(d -> d.getValue().diskRead);
        diskColWrite.setCellValueFactory(d -> d.getValue().diskWrite);
        diskColMem.setCellValueFactory(d -> d.getValue().memory);
        tblTopDisk.setItems(diskRows);
        tblTopDisk.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    /**
     * Applies red/yellow/green colouring to a string column where the value is a % number.
     */
    private void applyAlertColouring(TableColumn<ProcRow, String> col, double redThreshold, double yellowThreshold) {
        col.setCellFactory(c -> new TableCell<ProcRow, String>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); return; }
                setText(val);
                getStyleClass().removeAll("text-red", "text-yellow", "text-green");
                try {
                    double v = Double.parseDouble(val.replace("%", "").trim());
                    getStyleClass().add(v >= redThreshold ? "text-red"
                                      : v >= yellowThreshold ? "text-yellow" : "text-green");
                } catch (NumberFormatException ignored) {}
            }
        });
    }

    private void refresh() {
        SystemMetrics sm = store.getSystemMetrics();
        ProxyData pd     = store.getProxyData();

        if (sm == null) { lblCpuName.setText("Waiting for data…"); return; }

        // ---- CPU static info ----
        lblCpuName.setText(nvl(sm.getCpuName(), "N/A"));
        lblCpuCores.setText("Physical Cores: " + sm.getPhysicalCores());
        lblCpuThreads.setText("Logical Threads: " + sm.getLogicalCores());
        lblCpuFreq.setText(String.format("Max Frequency: %.2f GHz", sm.getCpuMaxFreqGhz()));

        // ---- CPU utilisation ----
        double cpu = sm.getSystemCpuPercent();
        lblCpuUsage.setText(String.format("System CPU: %.1f%%", cpu));
        pbCpuSystem.setProgress(cpu / 100.0);
        setAlertStyle(lblCpuUsage, cpu, 85, 60);

        if (pd != null) {
            double proxyCpu = pd.getProcessCpuPercent();
            lblProxyCpuUsage.setText(String.format("iCamera Process CPU: %.1f%%", proxyCpu));
            pbCpuProxy.setProgress(proxyCpu / 100.0);
            setAlertStyle(lblProxyCpuUsage, proxyCpu, 85, 60);
        }

        // ---- RAM ----
        double totalGb = sm.getTotalRamBytes() / (1024.0 * 1024 * 1024);
        double availGb = sm.getAvailableRamBytes() / (1024.0 * 1024 * 1024);
        double usedPct = sm.getMemoryUsedPercent();
        lblRamTotal.setText(String.format("Total RAM: %.1f GB", totalGb));
        lblRamAvail.setText(String.format("Available: %.1f GB", availGb));
        lblRamType.setText("RAM Type: " + nvl(sm.getRamType(), "N/A"));
        lblRamUsage.setText(String.format("Used: %.1f%%", usedPct));
        pbRam.setProgress(usedPct / 100.0);
        setAlertStyle(lblRamUsage, usedPct, 85, 60);

        if (pd != null) {
            lblProxyRamUsage.setText(String.format("iCamera Process RAM: %.1f MB", pd.getProcessMemoryMb()));
        }

        // ---- Drives ----
        vboxDrives.getChildren().clear();
        for (SystemMetrics.DriveInfo di : sm.getDrives()) {
            VBox driveBox = new VBox(4);
            driveBox.getStyleClass().add("drive-box");

            String rpmInfo = di.getRpm() != null ? "  [" + di.getRpm() + "]" : "";
            Label lName = new Label(di.getName() + rpmInfo);
            lName.getStyleClass().add("tile-header");

            HBox details = new HBox(20);
            Label lTotal = new Label("Total: " + formatMb(di.getTotalSpaceMb()));
            Label lFree  = new Label("Free: "  + formatMb(di.getFreeSpaceMb()));
            Label lUsed  = new Label(String.format("Used: %.1f%%", di.getUsedPercent()));
            lUsed.getStyleClass().add(di.getUsedPercent() > 85 ? "text-red"
                    : di.getUsedPercent() > 60 ? "text-yellow" : "text-green");
            details.getChildren().addAll(lTotal, lFree, lUsed);

            ProgressBar pb = new ProgressBar(di.getUsedPercent() / 100.0);
            pb.setMaxWidth(Double.MAX_VALUE);
            pb.getStyleClass().add(di.getUsedPercent() > 85 ? "progress-red" : "progress-normal");

            driveBox.getChildren().addAll(lName, details, pb);
            vboxDrives.getChildren().add(driveBox);
        }

        // ---- Overall health ----
        boolean healthy = sm.isHealthy();
        lblOverallHealth.setText("System Health: " + (healthy ? "STABLE" : "CRITICAL"));
        lblOverallHealth.getStyleClass().removeAll("text-green", "text-red");
        lblOverallHealth.getStyleClass().add(healthy ? "text-green" : "text-red");

        // ---- Top-5 process tables ----
        cpuRows.setAll(toRows(sm.getTopCpuProcesses()));
        memRows.setAll(toRows(sm.getTopMemoryProcesses()));
        diskRows.setAll(toRows(sm.getTopDiskIoProcesses()));
    }

    private List<ProcRow> toRows(List<ProcessInfo> procs) {
        List<ProcRow> rows = new ArrayList<>();
        for (ProcessInfo p : procs) rows.add(new ProcRow(p));
        return rows;
    }

    private void setAlertStyle(Label lbl, double val, double red, double yellow) {
        lbl.getStyleClass().removeAll("text-green", "text-yellow", "text-red");
        lbl.getStyleClass().add(val >= red ? "text-red" : val >= yellow ? "text-yellow" : "text-green");
    }

    private String nvl(String s, String def) { return (s != null && !s.isEmpty()) ? s : def; }

    private String formatMb(long mb) {
        if (mb >= 1024 * 1024) return String.format("%.1f TB", mb / (1024.0 * 1024));
        if (mb >= 1024)        return String.format("%.1f GB", mb / 1024.0);
        return mb + " MB";
    }

    // ---- Row model shared by all three tables ----

    public static class ProcRow {
        final StringProperty pid      = new SimpleStringProperty();
        final StringProperty name     = new SimpleStringProperty();
        final StringProperty user     = new SimpleStringProperty();
        final StringProperty cpu      = new SimpleStringProperty();
        final StringProperty memory   = new SimpleStringProperty();
        final StringProperty diskRead  = new SimpleStringProperty();
        final StringProperty diskWrite = new SimpleStringProperty();

        ProcRow(ProcessInfo p) {
            pid.set(String.valueOf(p.getPid()));
            name.set(p.getName());
            user.set(nvl(p.getUser()));
            cpu.set(p.getCpuPercent() > 0.01 ? String.format("%.1f%%", p.getCpuPercent()) : "< 0.1%");
            memory.set(p.getMemoryDisplay());
            diskRead.set(p.getDiskReadDisplay());
            diskWrite.set(p.getDiskWriteDisplay());
        }

        private static String nvl(String s) { return s != null ? s : "—"; }
    }
}
