package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * HSQLDB details page – shows HSQLDB service status, JMX report, direct connectivity.
 */
public class HsqldbDetailsController implements Initializable {

    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private Label lblHsqldbStatus;
    @FXML private Label lblHsqldbService;
    @FXML private Label lblHsqldbDirect;
    @FXML private Label lblHsqldbStartTime;
    @FXML private Label lblHsqldbUptime;
    @FXML private Pane paneHsqldbStatus;

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
            lblHsqldbStatus.setText("No JMX data available");
            return;
        }

        // Overall HSQLDB status
        boolean hsqlUp = "UP".equals(pd.getHsqldbStatus());
        lblHsqldbStatus.setText(nvl(pd.getHsqldbStatus(), "UNKNOWN"));
        styleStatus(paneHsqldbStatus, lblHsqldbStatus, hsqlUp);

        // Service status (same as HSQLDB status from Windows service)
        lblHsqldbService.setText(nvl(pd.getHsqldbStatus(), "UNKNOWN"));
        lblHsqldbService.getStyleClass().removeAll("text-green", "text-red");
        lblHsqldbService.getStyleClass().add(hsqlUp ? "text-green" : "text-red");

        // Direct connectivity
        boolean direct = pd.isHsqldbDirectlyReachable();
        lblHsqldbDirect.setText(direct ? "Reachable" : "Unreachable");
        lblHsqldbDirect.getStyleClass().removeAll("text-green", "text-red");
        lblHsqldbDirect.getStyleClass().add(direct ? "text-green" : "text-red");

        // Start time & uptime
        if (pd.getHsqldbStartTimeMillis() > 0) {
            lblHsqldbStartTime.setText(DTF.format(Instant.ofEpochMilli(pd.getHsqldbStartTimeMillis())));
            long uptimeMs = System.currentTimeMillis() - pd.getHsqldbStartTimeMillis();
            lblHsqldbUptime.setText(formatUptime(uptimeMs));
        } else {
            lblHsqldbStartTime.setText("N/A");
            lblHsqldbUptime.setText("N/A");
        }
    }

    private void styleStatus(Pane tile, Label label, boolean ok) {
        tile.getStyleClass().removeAll("tile-up", "tile-down");
        tile.getStyleClass().add(ok ? "tile-up" : "tile-down");
        label.getStyleClass().removeAll("text-green", "text-red");
        label.getStyleClass().add(ok ? "text-green" : "text-red");
    }

    private String nvl(String s, String def) { return (s != null && !s.isEmpty()) ? s : def; }

    private String formatUptime(long ms) {
        long s = ms / 1000, m = s / 60, h = m / 60, d = h / 24;
        if (d > 0) return d + "d " + (h % 24) + "h " + (m % 60) + "m";
        return h + "h " + (m % 60) + "m " + (s % 60) + "s";
    }
}
