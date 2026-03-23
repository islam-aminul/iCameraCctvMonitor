package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Main shell controller – manages sidebar navigation and content area.
 */
public class MainController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    @FXML private Label lblTitle;
    @FXML private Label lblLastRefresh;
    @FXML private Label lblJmxStatus;
    @FXML private StackPane contentArea;
    @FXML private Button btnDashboard;
    @FXML private Button btnAppDetails;
    @FXML private Button btnCctvDetails;
    @FXML private Button btnSystemHardware;
    @FXML private Button btnNetworkMonitor;
    @FXML private Button btnAlerts;
    @FXML private Button btnStreamAnalytics;
    @FXML private Button btnVmsDetection;
    @FXML private Button btnDiscovery;
    @FXML private Button btnSettings;
    @FXML private Label lblAlertBadge;
    @FXML private Label lblTcCode;

    private final DataStore store = DataStore.getInstance();
    private Timeline refreshTimeline;
    private Button activeButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblTitle.setText("iCamera CCTV Monitor");
        startStatusRefresh();
        navigateTo("dashboard");
        setActive(btnDashboard);
    }

    // ---- Navigation ----

    @FXML private void showDashboard()       { navigateTo("dashboard");        setActive(btnDashboard); }
    @FXML private void showAppDetails()      { navigateTo("application_details"); setActive(btnAppDetails); }
    @FXML private void showCctvDetails()     { navigateTo("cctv_details");     setActive(btnCctvDetails); }
    @FXML private void showSystemHardware()  { navigateTo("system_hardware");  setActive(btnSystemHardware); }
    @FXML private void showNetworkMonitor()  { navigateTo("network_monitor");  setActive(btnNetworkMonitor); }
    @FXML private void showAlerts()          { navigateTo("alerts");           setActive(btnAlerts); }
    @FXML private void showStreamAnalytics() { navigateTo("stream_analytics"); setActive(btnStreamAnalytics); }
    @FXML private void showVmsDetection()    { navigateTo("vms_detection");    setActive(btnVmsDetection); }
    @FXML private void showDiscovery()       { navigateTo("discovery");        setActive(btnDiscovery); }
    @FXML private void showSettings()        { navigateTo("settings");         setActive(btnSettings); }

    private void navigateTo(String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/" + viewName + ".fxml"));
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load view: {}", viewName, e);
            Label err = new Label("Failed to load: " + viewName);
            err.getStyleClass().add("error-label");
            contentArea.getChildren().setAll(err);
        }
    }

    private void setActive(Button btn) {
        if (activeButton != null) activeButton.getStyleClass().remove("nav-btn-active");
        activeButton = btn;
        if (btn != null) btn.getStyleClass().add("nav-btn-active");
    }

    // ---- Status bar refresh ----

    private void startStatusRefresh() {
        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> updateStatusBar()));
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();
    }

    private void updateStatusBar() {
        // JMX connection indicator
        boolean connected = store.isJmxConnected();
        lblJmxStatus.setText(connected ? "JMX: Connected" : "JMX: Disconnected");
        lblJmxStatus.getStyleClass().removeAll("status-ok", "status-error");
        lblJmxStatus.getStyleClass().add(connected ? "status-ok" : "status-error");

        // Last refresh time
        if (store.getLastPollTime() != null) {
            lblLastRefresh.setText("Last refresh: " + DTF.format(store.getLastPollTime()));
        }

        // TC Code
        if (store.getProxyData() != null) {
            String tc = store.getProxyData().getTcCode();
            lblTcCode.setText(tc != null && !tc.isEmpty() ? "TC: " + tc : "");
        }

        // Alert badge
        int alertCount = store.getUnresolvedAlerts().size();
        if (alertCount > 0) {
            lblAlertBadge.setText(String.valueOf(alertCount));
            lblAlertBadge.setVisible(true);
            // Blinking effect for alerts
            if (alertCount > 0 && !lblAlertBadge.getStyleClass().contains("blinking")) {
                lblAlertBadge.getStyleClass().add("blinking");
            }
        } else {
            lblAlertBadge.setVisible(false);
            lblAlertBadge.getStyleClass().remove("blinking");
        }
    }
}
