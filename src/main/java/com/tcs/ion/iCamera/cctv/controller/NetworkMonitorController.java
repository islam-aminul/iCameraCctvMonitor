package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.NetworkDataPoint;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Network Monitor page – upload speed graph (last 10 cycles) + current speed.
 */
public class NetworkMonitorController implements Initializable {

    @FXML private Label           lblCurrentSpeed;
    @FXML private Label           lblPeakSpeed;
    @FXML private Label           lblAvgSpeed;
    @FXML private LineChart<String, Number> chartUploadSpeed;
    @FXML private CategoryAxis    xAxis;
    @FXML private NumberAxis      yAxis;
    @FXML private TableView<NetworkRow> tableHistory;
    @FXML private TableColumn<NetworkRow, String> colTime;
    @FXML private TableColumn<NetworkRow, String> colSpeed;

    private final DataStore store = DataStore.getInstance();
    private XYChart.Series<String, Number> series;
    private Timeline timeline;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        series = new XYChart.Series<>();
        series.setName("Upload Speed (MB/s)");
        chartUploadSpeed.getData().add(series);
        chartUploadSpeed.setAnimated(false);
        chartUploadSpeed.setLegendVisible(true);
        xAxis.setLabel("Time");
        yAxis.setLabel("MB/s");

        colTime.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().time));
        colSpeed.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().speed));

        timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refresh();
    }

    private void refresh() {
        List<NetworkDataPoint> history = store.getNetworkHistory();
        if (history.isEmpty()) {
            lblCurrentSpeed.setText("0.00 MB/s");
            return;
        }

        // Current
        NetworkDataPoint latest = history.get(history.size() - 1);
        lblCurrentSpeed.setText(String.format("%.2f MB/s", latest.getUploadSpeedMbps()));

        // Peak & Avg
        double peak = 0, sum = 0;
        for (NetworkDataPoint pt : history) {
            if (pt.getUploadSpeedMbps() > peak) peak = pt.getUploadSpeedMbps();
            sum += pt.getUploadSpeedMbps();
        }
        lblPeakSpeed.setText(String.format("Peak: %.2f MB/s", peak));
        lblAvgSpeed.setText(String.format("Avg:  %.2f MB/s", sum / history.size()));

        // Chart
        series.getData().clear();
        for (NetworkDataPoint pt : history) {
            series.getData().add(new XYChart.Data<>(pt.getTimestampDisplay(), pt.getUploadSpeedMbps()));
        }

        // Table
        javafx.collections.ObservableList<NetworkRow> rows = javafx.collections.FXCollections.observableArrayList();
        for (int i = history.size() - 1; i >= 0; i--) {
            NetworkDataPoint pt = history.get(i);
            rows.add(new NetworkRow(pt.getTimestampDisplay(), String.format("%.2f MB/s", pt.getUploadSpeedMbps())));
        }
        tableHistory.setItems(rows);
    }

    private static class NetworkRow {
        final String time, speed;
        NetworkRow(String time, String speed) { this.time = time; this.speed = speed; }
    }
}
