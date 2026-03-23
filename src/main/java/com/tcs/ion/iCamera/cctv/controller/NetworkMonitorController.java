package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.NetworkDataPoint;
import com.tcs.ion.iCamera.cctv.model.UrlCheckResult;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Network Monitor page – upload speed graph + URL connectivity & SSL table.
 */
public class NetworkMonitorController implements Initializable {

    // ── Upload speed ────────────────────────────────────────────────────────
    @FXML private Label                             lblCurrentSpeed;
    @FXML private Label                             lblPeakSpeed;
    @FXML private Label                             lblAvgSpeed;
    @FXML private LineChart<String, Number>         chartUploadSpeed;
    @FXML private CategoryAxis                      xAxis;
    @FXML private NumberAxis                        yAxis;
    @FXML private TableView<NetworkRow>             tableHistory;
    @FXML private TableColumn<NetworkRow, String>   colTime;
    @FXML private TableColumn<NetworkRow, String>   colSpeed;

    // ── URL connectivity & SSL ───────────────────────────────────────────────
    @FXML private TableView<UrlRow>                 tableUrlChecks;
    @FXML private TableColumn<UrlRow, String>       urlColHost;
    @FXML private TableColumn<UrlRow, String>       urlColStatus;
    @FXML private TableColumn<UrlRow, String>       urlColHttp;
    @FXML private TableColumn<UrlRow, String>       urlColSsl;
    @FXML private TableColumn<UrlRow, String>       urlColIssuer;
    @FXML private TableColumn<UrlRow, String>       urlColExpiry;
    @FXML private TableColumn<UrlRow, String>       urlColDays;
    @FXML private TableColumn<UrlRow, String>       urlColChecked;

    private final DataStore store = DataStore.getInstance();
    private XYChart.Series<String, Number> series;
    private Timeline timeline;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // ── Chart ────────────────────────────────────────────────────────────
        series = new XYChart.Series<>();
        series.setName("Upload Speed (MB/s)");
        chartUploadSpeed.getData().add(series);
        chartUploadSpeed.setAnimated(false);
        xAxis.setLabel("Time");
        yAxis.setLabel("MB/s");

        // ── Speed history table ──────────────────────────────────────────────
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().time));
        colSpeed.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().speed));

        // ── URL check table ──────────────────────────────────────────────────
        urlColHost.setCellValueFactory(d    -> new SimpleStringProperty(d.getValue().host));
        urlColHttp.setCellValueFactory(d    -> new SimpleStringProperty(d.getValue().httpStatus));
        urlColIssuer.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue().issuer));
        urlColExpiry.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue().expiry));
        urlColDays.setCellValueFactory(d    -> new SimpleStringProperty(d.getValue().daysLeft));
        urlColChecked.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().checked));

        // Connectivity column – colour coded
        urlColStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status));
        urlColStatus.setCellFactory(col -> new TableCell<UrlRow, String>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                getStyleClass().removeAll("text-green", "text-red");
                if (empty || val == null) { setText(null); return; }
                setText(val);
                getStyleClass().add("UP".equals(val) ? "text-green" : "text-red");
            }
        });

        // SSL column – colour coded
        urlColSsl.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().ssl));
        urlColSsl.setCellFactory(col -> new TableCell<UrlRow, String>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                getStyleClass().removeAll("text-green", "text-red");
                if (empty || val == null) { setText(null); return; }
                setText(val);
                if      ("VALID".equals(val))   getStyleClass().add("text-green");
                else if ("INVALID".equals(val)) getStyleClass().add("text-red");
            }
        });

        // Days Left column – colour coded by urgency
        urlColDays.setCellFactory(col -> new TableCell<UrlRow, String>() {
            @Override protected void updateItem(String val, boolean empty) {
                super.updateItem(val, empty);
                getStyleClass().removeAll("text-green", "text-yellow", "text-red");
                if (empty || val == null || "-".equals(val)) { setText(val); return; }
                setText(val);
                try {
                    int days = Integer.parseInt(val);
                    if      (days < 7)  getStyleClass().add("text-red");
                    else if (days < 30) getStyleClass().add("text-yellow");
                    else                getStyleClass().add("text-green");
                } catch (NumberFormatException ignored) {}
            }
        });

        // ── Refresh timeline ─────────────────────────────────────────────────
        timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refresh();
    }

    // ── Refresh ──────────────────────────────────────────────────────────────

    private void refresh() {
        refreshSpeedChart();
        refreshUrlTable();
    }

    private void refreshSpeedChart() {
        List<NetworkDataPoint> history = store.getNetworkHistory();
        if (history.isEmpty()) {
            lblCurrentSpeed.setText("0.00 MB/s");
            return;
        }

        NetworkDataPoint latest = history.get(history.size() - 1);
        lblCurrentSpeed.setText(String.format("%.2f MB/s", latest.getUploadSpeedMbps()));

        double peak = 0, sum = 0;
        for (NetworkDataPoint pt : history) {
            if (pt.getUploadSpeedMbps() > peak) peak = pt.getUploadSpeedMbps();
            sum += pt.getUploadSpeedMbps();
        }
        lblPeakSpeed.setText(String.format("Peak: %.2f MB/s", peak));
        lblAvgSpeed.setText(String.format("Avg:  %.2f MB/s", sum / history.size()));

        series.getData().clear();
        for (NetworkDataPoint pt : history) {
            series.getData().add(new XYChart.Data<>(pt.getTimestampDisplay(), pt.getUploadSpeedMbps()));
        }

        ObservableList<NetworkRow> rows = FXCollections.observableArrayList();
        for (int i = history.size() - 1; i >= 0; i--) {
            NetworkDataPoint pt = history.get(i);
            rows.add(new NetworkRow(pt.getTimestampDisplay(),
                                    String.format("%.2f MB/s", pt.getUploadSpeedMbps())));
        }
        tableHistory.setItems(rows);
    }

    private void refreshUrlTable() {
        List<UrlCheckResult> results = store.getUrlCheckResults();
        ObservableList<UrlRow> rows = FXCollections.observableArrayList();
        for (UrlCheckResult r : results) {
            rows.add(new UrlRow(r));
        }
        tableUrlChecks.setItems(rows);
    }

    // ── Inner row models ──────────────────────────────────────────────────────

    private static class NetworkRow {
        final String time, speed;
        NetworkRow(String time, String speed) { this.time = time; this.speed = speed; }
    }

    static class UrlRow {
        final String  host, status, httpStatus, ssl, issuer, expiry, daysLeft, checked;

        UrlRow(UrlCheckResult r) {
            host       = r.getHost();
            status     = r.isReachable() ? "UP" : "DOWN";
            httpStatus = r.isReachable() && r.getHttpStatus() > 0
                         ? String.valueOf(r.getHttpStatus()) : "-";

            if (!r.isReachable()) {
                ssl = "-";
            } else {
                ssl = r.isSslValid() ? "VALID" : "INVALID";
            }

            issuer   = r.getSslIssuer();
            expiry   = r.getSslExpiryDisplay();
            daysLeft = (r.isReachable() && r.isSslValid())
                       ? String.valueOf(r.getSslDaysLeft()) : "-";
            checked  = r.getCheckedAtDisplay();
        }
    }
}
