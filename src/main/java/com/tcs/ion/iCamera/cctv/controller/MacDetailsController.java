package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.AlertData;
import com.tcs.ion.iCamera.cctv.model.MacValidationResult;
import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import com.tcs.ion.iCamera.cctv.service.HttpService;
import com.tcs.ion.iCamera.cctv.service.MacValidationService;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * MAC Address details page.
 *
 * <p>Top section shows the current/last MAC from JMX with a mismatch warning
 * and sync button (existing behaviour).
 *
 * <p>Bottom section ("Cloud MAC Validation") allows the operator to trigger an
 * on-demand validation against the remote cloud API.  On completion it displays:
 * <ul>
 *   <li>Destination host (from mac-validation.properties)</li>
 *   <li>Outbound network interface used to reach that host</li>
 *   <li>Locally detected MAC address of that interface</li>
 *   <li>Cloud-registered MAC for this proxy (from the API response)</li>
 *   <li>Alert outcome for all documented scenarios</li>
 *   <li>List of conflicting proxies when the local MAC is shared</li>
 * </ul>
 */
public class MacDetailsController implements Initializable {

    // ---- Existing MAC section fields ---------------------------------------

    @FXML private Label  lblCurrentMac;
    @FXML private Label  lblLastMac;
    @FXML private Label  lblMacMismatch;
    @FXML private Button btnSyncMac;
    @FXML private Pane   paneMacSection;

    // ---- Cloud validation section fields -----------------------------------

    @FXML private Button   btnValidateMac;
    @FXML private Label    lblValidatingSpinner;

    @FXML private Label    lblDestHost;
    @FXML private Label    lblOutboundIface;
    @FXML private Label    lblLocalMac;
    @FXML private Label    lblCloudMac;

    @FXML private Label    lblValidationAlert;

    @FXML private VBox     vboxConflicts;
    @FXML private TextArea txtConflicts;

    // ---- Services ----------------------------------------------------------

    private final DataStore           store             = DataStore.getInstance();
    private final HttpService         httpService       = new HttpService();
    private final MacValidationService validationService = new MacValidationService();

    private Timeline timeline;

    // ---- Initialise --------------------------------------------------------

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refresh();
    }

    // ---- Existing MAC refresh logic ----------------------------------------

    private void refresh() {
        ProxyData pd = store.getProxyData();
        if (pd == null) {
            lblCurrentMac.setText("N/A");
            lblLastMac.setText("N/A");
            lblMacMismatch.setVisible(false);
            btnSyncMac.setVisible(false);
            return;
        }

        lblCurrentMac.setText(nvl(pd.getCurrentMacAddress()));
        lblLastMac.setText(nvl(pd.getLastMacAddress()));

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
            Platform.runLater(() -> {
                btnSyncMac.setDisable(false);
                btnSyncMac.setText("Sync MAC Address");
                Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                alert.setTitle("MAC Sync");
                alert.setHeaderText(ok ? "MAC address synced successfully" : "MAC sync failed");
                alert.showAndWait();
            });
        }).start();
    }

    // ---- Cloud validation --------------------------------------------------

    @FXML
    private void onValidateMac() {
        ProxyData pd = store.getProxyData();
        if (pd == null) {
            showValidationError("No proxy data available. Ensure the JMX connection is active.");
            return;
        }

        long orgId   = pd.getOrgId();
        long proxyId = pd.getProxyId();

        if (orgId <= 0 || proxyId <= 0) {
            showValidationError(
                    "org_id (" + orgId + ") and proxy_id (" + proxyId + ") must both be positive. "
                    + "Ensure the proxy is fully connected via JMX.");
            return;
        }

        // Disable button, show spinner
        btnValidateMac.setDisable(true);
        lblValidatingSpinner.setVisible(true);
        clearValidationDisplay();

        new Thread(() -> {
            MacValidationResult result = validationService.validate(orgId, proxyId);
            Platform.runLater(() -> {
                btnValidateMac.setDisable(false);
                lblValidatingSpinner.setVisible(false);
                displayValidationResult(result);
            });
        }).start();
    }

    /** Populates all validation output labels from a completed {@link MacValidationResult}. */
    private void displayValidationResult(MacValidationResult r) {
        // Network / API context
        lblDestHost.setText(nvl(r.getDestinationHost()));
        lblOutboundIface.setText(nvl(r.getOutboundInterface()));
        lblLocalMac.setText(nvl(r.getLocalMac()));
        lblCloudMac.setText(nvl(r.getCloudMac()));

        // Alert outcome label
        String alertText;
        if (r.getScenario() == MacValidationResult.Scenario.VALIDATION_FAILED) {
            alertText = r.getAlertMessage()
                    + (r.getErrorDetail() != null ? "\nDetail: " + r.getErrorDetail() : "");
        } else {
            alertText = r.getAlertMessage();
        }
        lblValidationAlert.setText(alertText);
        lblValidationAlert.getStyleClass().removeAll(
                "text-green", "text-yellow", "text-red", "detail-value");
        lblValidationAlert.getStyleClass().add(severityStyleClass(r.getAlertSeverity()));
        lblValidationAlert.setVisible(true);

        // Conflicting proxies panel
        List<String> conflicts = r.getConflictingProxies();
        boolean hasConflicts = conflicts != null && !conflicts.isEmpty();
        vboxConflicts.setVisible(hasConflicts);
        vboxConflicts.setManaged(hasConflicts);
        if (hasConflicts) {
            txtConflicts.setText(String.join("\n", conflicts));
        }
    }

    /** Clears all validation output fields (called before a new run). */
    private void clearValidationDisplay() {
        lblDestHost.setText("");
        lblOutboundIface.setText("");
        lblLocalMac.setText("");
        lblCloudMac.setText("");
        lblValidationAlert.setText("");
        lblValidationAlert.setVisible(false);
        vboxConflicts.setVisible(false);
        vboxConflicts.setManaged(false);
        txtConflicts.setText("");
    }

    /** Shows a pre-flight error (before the async call) in the alert label. */
    private void showValidationError(String message) {
        clearValidationDisplay();
        lblValidationAlert.setText(message);
        lblValidationAlert.getStyleClass().removeAll(
                "text-green", "text-yellow", "text-red", "detail-value");
        lblValidationAlert.getStyleClass().add("text-red");
        lblValidationAlert.setVisible(true);
    }

    // ---- Helpers -----------------------------------------------------------

    private static String severityStyleClass(AlertData.Severity severity) {
        if (severity == null) return "detail-value";
        switch (severity) {
            case INFO:     return "text-green";
            case WARNING:  return "text-yellow";
            case CRITICAL: return "text-red";
            default:       return "detail-value";
        }
    }

    private static String nvl(String s) {
        return (s != null && !s.isEmpty()) ? s : "N/A";
    }
}
