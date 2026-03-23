package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.VmsInfo;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.net.URL;
import java.util.*;

/**
 * VMS Detection page.
 *
 * Shows all Video Management Software detected on this machine – both running
 * processes and installed Windows services – in a compact scrollable table.
 * No details card; the table shows 2-3 rows visible at a time and is scrollable.
 */
public class VmsDetectionController implements Initializable {

    @FXML private Label  lblScanStatus;
    @FXML private Label  lblTotalDetected;
    @FXML private Label  lblRunning;
    @FXML private Label  lblStopped;
    @FXML private Label  lblLastScan;
    @FXML private Button btnRescan;

    @FXML private TableView<VmsRow>              tableVms;
    @FXML private TableColumn<VmsRow, String>    colVendor;
    @FXML private TableColumn<VmsRow, String>    colProcess;
    @FXML private TableColumn<VmsRow, String>    colStatus;
    @FXML private TableColumn<VmsRow, String>    colPid;
    @FXML private TableColumn<VmsRow, String>    colCpu;
    @FXML private TableColumn<VmsRow, String>    colMemory;
    @FXML private TableColumn<VmsRow, String>    colPath;

    @FXML private VBox   paneNoVms;

    private final DataStore store = DataStore.getInstance();
    private final ObservableList<VmsRow> rows = FXCollections.observableArrayList();
    private Timeline refreshTimeline;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        refreshTimeline = new Timeline(new KeyFrame(Duration.seconds(10), e -> refresh()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
        refresh();
    }

    private void setupTable() {
        colVendor.setCellValueFactory(d -> d.getValue().vendor);
        colProcess.setCellValueFactory(d -> d.getValue().process);
        colStatus.setCellValueFactory(d -> d.getValue().status);
        colPid.setCellValueFactory(d -> d.getValue().pid);
        colCpu.setCellValueFactory(d -> d.getValue().cpu);
        colMemory.setCellValueFactory(d -> d.getValue().memory);
        colPath.setCellValueFactory(d -> d.getValue().path);

        // Status column – coloured dot + text
        colStatus.setCellFactory(col -> new TableCell<VmsRow, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setGraphic(null); setText(null); return; }
                HBox box = new HBox(6);
                box.setAlignment(Pos.CENTER_LEFT);
                Circle dot = new Circle(5);
                boolean running = "RUNNING".equalsIgnoreCase(item);
                dot.setFill(running ? Color.LIME : Color.GRAY);
                Label lbl = new Label(item);
                lbl.getStyleClass().add(running ? "text-green" : "text-yellow");
                box.getChildren().addAll(dot, lbl);
                setGraphic(box);
                setText(null);
            }
        });

        // CPU column – warn if > 20%
        colCpu.setCellFactory(col -> new TableCell<VmsRow, String>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); return; }
                setText(val);
                getStyleClass().removeAll("text-red", "text-yellow", "text-green");
                try {
                    double v = Double.parseDouble(val.replace("%", "").trim());
                    getStyleClass().add(v > 20 ? "text-red" : v > 5 ? "text-yellow" : "text-green");
                } catch (NumberFormatException ignored) {}
            }
        });

        tableVms.setItems(rows);
        tableVms.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void refresh() {
        List<VmsInfo> vmsList = store.getVmsData();
        rows.clear();
        for (VmsInfo vms : vmsList) rows.add(new VmsRow(vms));

        int total   = vmsList.size();
        long running = vmsList.stream().filter(VmsInfo::isRunning).count();
        long stopped = total - running;

        lblTotalDetected.setText("Detected: " + total);
        lblRunning.setText("Running: " + running);
        lblStopped.setText("Stopped/Installed: " + stopped);
        paneNoVms.setVisible(total == 0);
        tableVms.setVisible(total > 0);

        if (total == 0) {
            lblScanStatus.setText("No VMS software detected on this machine");
            lblScanStatus.getStyleClass().removeAll("text-green", "text-red", "text-yellow");
            lblScanStatus.getStyleClass().add("text-green");
        } else if (running > 0) {
            lblScanStatus.setText(running + " VMS software actively running");
            lblScanStatus.getStyleClass().removeAll("text-green", "text-red", "text-yellow");
            lblScanStatus.getStyleClass().add("text-yellow");
        } else {
            lblScanStatus.setText(total + " VMS software installed (none running)");
            lblScanStatus.getStyleClass().removeAll("text-green", "text-red", "text-yellow");
            lblScanStatus.getStyleClass().add("text-white");
        }

        if (store.getLastVmsScanTime() != null) {
            lblLastScan.setText("Last scan: " + store.getLastVmsScanTime()
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
    }

    @FXML
    private void onRescan() {
        btnRescan.setDisable(true);
        btnRescan.setText("Scanning…");
        new Thread(() -> {
            store.triggerVmsRescan();
            refresh();
            javafx.application.Platform.runLater(() -> {
                btnRescan.setDisable(false);
                btnRescan.setText("Re-scan Now");
            });
        }).start();
    }

    // ---- Row model ----

    public static class VmsRow {
        final StringProperty vendor  = new SimpleStringProperty();
        final StringProperty process = new SimpleStringProperty();
        final StringProperty status  = new SimpleStringProperty();
        final StringProperty pid     = new SimpleStringProperty();
        final StringProperty cpu     = new SimpleStringProperty();
        final StringProperty memory  = new SimpleStringProperty();
        final StringProperty path    = new SimpleStringProperty();

        VmsRow(VmsInfo v) {
            vendor.set(v.getVendorDisplayName());
            process.set(nvl(v.getProcessName()));
            status.set(nvl(v.getStatus(), "UNKNOWN"));
            pid.set(v.getPid() > 0 ? String.valueOf(v.getPid()) : "—");
            cpu.set(v.getCpuPercent() > 0 ? String.format("%.1f%%", v.getCpuPercent()) : "—");
            memory.set(v.getMemoryMb() > 0 ? v.getMemoryMb() + " MB" : "—");
            path.set(nvl(v.getInstallPath(), "Unknown"));
        }

        private static String nvl(String s) { return nvl(s, "N/A"); }
        private static String nvl(String s, String d) { return (s != null && !s.isEmpty()) ? s : d; }
    }
}
