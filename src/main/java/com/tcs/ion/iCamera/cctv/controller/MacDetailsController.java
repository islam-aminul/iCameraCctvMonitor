package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.AlertData;
import com.tcs.ion.iCamera.cctv.model.MacValidationResult;
import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import com.tcs.ion.iCamera.cctv.service.HttpService;
import com.tcs.ion.iCamera.cctv.service.MacValidationService;
import com.tcs.ion.iCamera.cctv.util.IssueResolutionProvider;
import com.tcs.ion.iCamera.cctv.util.MacValidationConfig;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * MAC Address details page — comprehensive scenario-aware controller.
 *
 * <p>Section 1 (JMX): Live-refreshed current/last MAC from proxy JMX data,
 * with match/mismatch indicator, copy-to-clipboard, and conditional sync button.
 *
 * <p>Section 2 (Cloud Validation): On-demand validation against the remote cloud API.
 * Displays full network context, result grid, scenario-specific banner and
 * dynamic action button whose label/style/behaviour changes per scenario.
 *
 * <p>Section 3 (Conflicts): Rendered as styled cards when conflicting proxies exist.
 *
 * <p>Section 4 (Guidance): Resolution steps pulled from {@link IssueResolutionProvider},
 * collapsible, and scenario-aware.
 */
public class MacDetailsController implements Initializable {

    // ═══════════════════════════════════════════════════════════════════
    //  FXML – Section 1: JMX MAC Address Overview
    // ═══════════════════════════════════════════════════════════════════

    @FXML private Pane      paneMacSection;
    @FXML private Label     lblMacStatus;
    @FXML private Label     lblProxyId;
    @FXML private Label     lblProxyName;
    @FXML private Label     lblCurrentMac;
    @FXML private Label     lblLastMac;
    @FXML private Button    btnCopyCurrentMac;
    @FXML private HBox      hboxMismatchRow;
    @FXML private Label     lblMacMismatch;
    @FXML private Button    btnSyncMac;

    // ═══════════════════════════════════════════════════════════════════
    //  FXML – Section 2: Cloud MAC Validation
    // ═══════════════════════════════════════════════════════════════════

    @FXML private Pane      paneValidation;
    @FXML private Label     lblLastValidated;
    @FXML private Button    btnValidateMac;
    @FXML private Label     lblValidatingSpinner;
    @FXML private Button    btnScenarioAction;
    @FXML private GridPane  gridValidationResult;
    @FXML private Label     lblDestHost;
    @FXML private Label     lblOutboundIface;
    @FXML private Label     lblLocalMac;
    @FXML private Label     lblCloudMac;
    @FXML private Button    btnCopyLocalMac;
    @FXML private Label     lblScenarioBanner;
    @FXML private Label     lblErrorDetail;

    // ═══════════════════════════════════════════════════════════════════
    //  FXML – Section 3: Conflicting Proxies
    // ═══════════════════════════════════════════════════════════════════

    @FXML private Pane      paneConflicts;
    @FXML private Label     lblConflictCount;
    @FXML private VBox      vboxConflictCards;

    // ═══════════════════════════════════════════════════════════════════
    //  FXML – Section 4: Guidance & Resolution
    // ═══════════════════════════════════════════════════════════════════

    @FXML private Pane      paneGuidance;
    @FXML private Label     lblGuidanceTitle;
    @FXML private Hyperlink lnkToggleSteps;
    @FXML private Label     lblGuidanceCause;
    @FXML private VBox      vboxGuidanceSteps;

    // ═══════════════════════════════════════════════════════════════════
    //  Services & state
    // ═══════════════════════════════════════════════════════════════════

    private final DataStore             store             = DataStore.getInstance();
    private final HttpService           httpService       = new HttpService();
    private final MacValidationService  validationService = new MacValidationService();

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    private Timeline          timeline;
    private MacValidationResult lastResult;
    private boolean           guidanceExpanded = false;

    // ═══════════════════════════════════════════════════════════════════
    //  Initialise
    // ═══════════════════════════════════════════════════════════════════

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // JMX refresh every 5 seconds
        timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refreshJmxSection()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refreshJmxSection();

        // Optional auto-validation from application.properties
        try {
            MacValidationConfig cfg = MacValidationConfig.load();
            int intervalSec = cfg.getMacValidationIntervalSeconds();
            if (intervalSec > 0) {
                Timeline autoValidate = new Timeline(
                        new KeyFrame(Duration.seconds(intervalSec), e -> onValidateMac()));
                autoValidate.setCycleCount(Animation.INDEFINITE);
                // Delay first auto-run by the same interval (don't fire immediately)
                autoValidate.play();
            }
        } catch (Exception ignored) {
            // Config load failure is non-fatal — auto-validation simply disabled
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Section 1 — JMX refresh
    // ═══════════════════════════════════════════════════════════════════

    private void refreshJmxSection() {
        ProxyData pd = store.getProxyData();
        if (pd == null) {
            lblProxyId.setText("N/A");
            lblProxyName.setText("N/A");
            lblCurrentMac.setText("N/A");
            lblLastMac.setText("N/A");
            setMacStatusPill(false, false);
            setMismatchRow(false);
            applyTileBorder(paneMacSection, null);
            return;
        }

        lblProxyId.setText(String.valueOf(pd.getProxyId()));
        lblProxyName.setText(nvl(pd.getProxyName()));
        lblCurrentMac.setText(nvl(pd.getCurrentMacAddress()));
        lblLastMac.setText(nvl(pd.getLastMacAddress()));

        boolean hasCurrent = pd.getCurrentMacAddress() != null && !pd.getCurrentMacAddress().isEmpty();
        boolean mismatch   = pd.isMacMismatch();

        setMacStatusPill(hasCurrent, mismatch);
        setMismatchRow(mismatch);

        if (mismatch) {
            applyTileBorder(paneMacSection, "tile-warn");
        } else if (hasCurrent) {
            applyTileBorder(paneMacSection, "tile-up");
        } else {
            applyTileBorder(paneMacSection, null);
        }
    }

    private void setMacStatusPill(boolean hasMac, boolean mismatch) {
        lblMacStatus.getStyleClass().removeAll(
                "mac-status-match", "mac-status-mismatch", "mac-status-unknown");
        if (!hasMac) {
            lblMacStatus.setText("●  No Data");
            lblMacStatus.getStyleClass().add("mac-status-unknown");
        } else if (mismatch) {
            lblMacStatus.setText("⚠  Mismatch");
            lblMacStatus.getStyleClass().add("mac-status-mismatch");
        } else {
            lblMacStatus.setText("✓  Match");
            lblMacStatus.getStyleClass().add("mac-status-match");
        }
    }

    private void setMismatchRow(boolean visible) {
        hboxMismatchRow.setVisible(visible);
        hboxMismatchRow.setManaged(visible);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Section 1 — Actions
    // ═══════════════════════════════════════════════════════════════════

    @FXML
    private void onCopyCurrentMac() {
        copyToClipboard(lblCurrentMac.getText(), btnCopyCurrentMac);
    }

    @FXML
    private void onSyncMac() {
        ProxyData pd = store.getProxyData();
        if (pd == null) return;

        btnSyncMac.setDisable(true);
        btnSyncMac.setText("Syncing…");

        new Thread(() -> {
            boolean ok = httpService.syncMacAddress(pd.getProxyId(), pd.getCurrentMacAddress());
            Platform.runLater(() -> {
                btnSyncMac.setDisable(false);
                btnSyncMac.setText("Sync MAC Address");
                if (ok) {
                    showCopyToast(btnSyncMac);
                    // Auto re-validate after successful sync
                    onValidateMac();
                } else {
                    showAlert(Alert.AlertType.ERROR, "MAC Sync", "MAC sync failed. Check logs for details.");
                }
            });
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Section 2 — Cloud Validation
    // ═══════════════════════════════════════════════════════════════════

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

        // UI: disable button, show spinner, clear old results
        btnValidateMac.setDisable(true);
        lblValidatingSpinner.setVisible(true);
        clearValidationDisplay();

        new Thread(() -> {
            MacValidationResult result = validationService.validate(orgId, proxyId);
            Platform.runLater(() -> {
                btnValidateMac.setDisable(false);
                lblValidatingSpinner.setVisible(false);
                lblLastValidated.setText("Last validated: " + TIME_FMT.format(Instant.now()));
                displayValidationResult(result);
            });
        }).start();
    }

    /** Populates all validation output from a completed result. */
    private void displayValidationResult(MacValidationResult r) {
        this.lastResult = r;

        // Show result grid
        gridValidationResult.setVisible(true);
        gridValidationResult.setManaged(true);
        lblDestHost.setText(nvl(r.getDestinationHost()));
        lblOutboundIface.setText(nvl(r.getOutboundInterface()));
        lblLocalMac.setText(nvl(r.getLocalMac()));
        lblCloudMac.setText(nvl(r.getCloudMac()));

        // Scenario banner
        configureBanner(r);

        // Error detail (only for VALIDATION_FAILED)
        if (r.getScenario() == MacValidationResult.Scenario.VALIDATION_FAILED
                && r.getErrorDetail() != null) {
            lblErrorDetail.setText("Detail: " + r.getErrorDetail());
            lblErrorDetail.setVisible(true);
            lblErrorDetail.setManaged(true);
        }

        // Tile border based on severity
        applyValidationTileBorder(r.getAlertSeverity());

        // Dynamic action button
        configureScenarioAction(r);

        // Conflicts section
        displayConflicts(r.getConflictingProxies());

        // Guidance section
        displayGuidance(r.getScenario());
    }

    /** Configures the scenario outcome banner text, icon, and style. */
    private void configureBanner(MacValidationResult r) {
        String icon;
        String bannerClass;

        switch (r.getScenario()) {
            case MAC_MATCH_NO_CONFLICT:
                icon = "✓  ";
                bannerClass = "scenario-match";
                break;
            case MAC_MISMATCH_NO_CONFLICT:
            case NO_MAPPING_MAC_REGISTERED_ELSEWHERE:
                icon = "⚠  ";
                bannerClass = "scenario-warning";
                break;
            case MAC_MISMATCH_WITH_CONFLICT:
            case VALIDATION_FAILED:
                icon = "✗  ";
                bannerClass = "scenario-critical";
                break;
            case NO_MAPPING_MAC_FREE:
                icon = "ℹ  ";
                bannerClass = "scenario-match";
                break;
            default:
                icon = "";
                bannerClass = "scenario-match";
        }

        lblScenarioBanner.setText(icon + r.getAlertMessage());
        lblScenarioBanner.getStyleClass().removeAll(
                "scenario-match", "scenario-warning", "scenario-critical");
        lblScenarioBanner.getStyleClass().add(bannerClass);
        lblScenarioBanner.setVisible(true);
        lblScenarioBanner.setManaged(true);
    }

    /** Configures the dynamic action button for scenario-specific follow-up. */
    private void configureScenarioAction(MacValidationResult r) {
        btnScenarioAction.getStyleClass().removeAll(
                "btn-primary", "btn-success", "btn-warning", "btn-danger");

        switch (r.getScenario()) {
            case MAC_MATCH_NO_CONFLICT:
                btnScenarioAction.setText("↻  Re-validate");
                btnScenarioAction.getStyleClass().add("btn-success");
                break;
            case MAC_MISMATCH_NO_CONFLICT:
                btnScenarioAction.setText("⇄  Sync MAC to Cloud");
                btnScenarioAction.getStyleClass().add("btn-warning");
                break;
            case MAC_MISMATCH_WITH_CONFLICT:
                btnScenarioAction.setText("⚠  View Conflicts & Guidance");
                btnScenarioAction.getStyleClass().add("btn-danger");
                break;
            case NO_MAPPING_MAC_FREE:
                btnScenarioAction.setText("＋  Register MAC");
                btnScenarioAction.getStyleClass().add("btn-primary");
                break;
            case NO_MAPPING_MAC_REGISTERED_ELSEWHERE:
                btnScenarioAction.setText("⚠  View Conflicts");
                btnScenarioAction.getStyleClass().add("btn-danger");
                break;
            case VALIDATION_FAILED:
                btnScenarioAction.setText("↻  Retry Validation");
                btnScenarioAction.getStyleClass().add("btn-warning");
                break;
            default:
                btnScenarioAction.setVisible(false);
                btnScenarioAction.setManaged(false);
                return;
        }
        btnScenarioAction.setVisible(true);
        btnScenarioAction.setManaged(true);
    }

    /** Handles dynamic action button click based on last scenario. */
    @FXML
    private void onScenarioAction() {
        if (lastResult == null) return;

        switch (lastResult.getScenario()) {
            case MAC_MATCH_NO_CONFLICT:
            case VALIDATION_FAILED:
                // Re-validate / Retry
                onValidateMac();
                break;
            case MAC_MISMATCH_NO_CONFLICT:
            case NO_MAPPING_MAC_FREE:
                // Sync / Register MAC
                onSyncMac();
                break;
            case MAC_MISMATCH_WITH_CONFLICT:
            case NO_MAPPING_MAC_REGISTERED_ELSEWHERE:
                // Scroll to conflicts + expand guidance
                scrollToConflicts();
                break;
        }
    }

    @FXML
    private void onCopyLocalMac() {
        copyToClipboard(lblLocalMac.getText(), btnCopyLocalMac);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Section 3 — Conflicts
    // ═══════════════════════════════════════════════════════════════════

    private void displayConflicts(List<String> conflicts) {
        boolean hasConflicts = conflicts != null && !conflicts.isEmpty();
        paneConflicts.setVisible(hasConflicts);
        paneConflicts.setManaged(hasConflicts);
        vboxConflictCards.getChildren().clear();

        if (!hasConflicts) return;

        lblConflictCount.setText(String.valueOf(conflicts.size()));

        for (int i = 0; i < conflicts.size(); i++) {
            String proxy = conflicts.get(i);
            HBox card = createConflictCard(i + 1, proxy);
            vboxConflictCards.getChildren().add(card);
        }
    }

    private HBox createConflictCard(int index, String proxyName) {
        Label numLabel = new Label(String.valueOf(index));
        numLabel.getStyleClass().addAll("detail-value", "text-white");
        numLabel.setStyle("-fx-font-weight:bold; -fx-min-width:24; -fx-alignment:CENTER;");

        Label nameLabel = new Label(proxyName);
        nameLabel.getStyleClass().add("detail-value");
        nameLabel.setStyle("-fx-font-weight:bold;");

        Label descLabel = new Label("Shares the same local MAC address");
        descLabel.getStyleClass().add("detail-label");

        VBox textBox = new VBox(2, nameLabel, descLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        HBox card = new HBox(12, numLabel, textBox);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(10, 14, 10, 14));
        card.getStyleClass().addAll("conflict-card");
        return card;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Section 4 — Guidance
    // ═══════════════════════════════════════════════════════════════════

    private void displayGuidance(MacValidationResult.Scenario scenario) {
        String parameter = scenarioToAlertParameter(scenario);
        if (parameter == null) {
            paneGuidance.setVisible(false);
            paneGuidance.setManaged(false);
            return;
        }

        IssueResolutionProvider.Resolution res = IssueResolutionProvider.getResolution(parameter);
        lblGuidanceTitle.setText(res.getTitle());
        lblGuidanceCause.setText(res.getRootCause());

        vboxGuidanceSteps.getChildren().clear();
        List<String> steps = res.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Label stepLabel = new Label((i + 1) + ".  " + steps.get(i));
            stepLabel.setWrapText(true);
            stepLabel.getStyleClass().add("detail-value");
            stepLabel.setStyle("-fx-font-size:12px; -fx-padding:2 0 2 8;");
            vboxGuidanceSteps.getChildren().add(stepLabel);
        }

        guidanceExpanded = false;
        vboxGuidanceSteps.setVisible(false);
        vboxGuidanceSteps.setManaged(false);
        lnkToggleSteps.setText("▸ Show Steps");

        paneGuidance.setVisible(true);
        paneGuidance.setManaged(true);
    }

    @FXML
    private void onToggleGuidance() {
        guidanceExpanded = !guidanceExpanded;
        vboxGuidanceSteps.setVisible(guidanceExpanded);
        vboxGuidanceSteps.setManaged(guidanceExpanded);
        lnkToggleSteps.setText(guidanceExpanded ? "▾ Hide Steps" : "▸ Show Steps");
    }

    /** Maps validation scenarios to IssueResolutionProvider parameter keys. */
    private static String scenarioToAlertParameter(MacValidationResult.Scenario scenario) {
        switch (scenario) {
            case MAC_MISMATCH_NO_CONFLICT:
            case MAC_MISMATCH_WITH_CONFLICT:
                return "MAC_ADDRESS";
            case NO_MAPPING_MAC_REGISTERED_ELSEWHERE:
                return "MAC_ADDRESS";
            case VALIDATION_FAILED:
                return "CONNECTIVITY";
            case MAC_MATCH_NO_CONFLICT:
            case NO_MAPPING_MAC_FREE:
            default:
                // No guidance needed for healthy states
                return null;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private void clearValidationDisplay() {
        gridValidationResult.setVisible(false);
        gridValidationResult.setManaged(false);
        lblDestHost.setText("");
        lblOutboundIface.setText("");
        lblLocalMac.setText("");
        lblCloudMac.setText("");

        lblScenarioBanner.setVisible(false);
        lblScenarioBanner.setManaged(false);
        lblScenarioBanner.setText("");

        lblErrorDetail.setVisible(false);
        lblErrorDetail.setManaged(false);
        lblErrorDetail.setText("");

        btnScenarioAction.setVisible(false);
        btnScenarioAction.setManaged(false);

        paneConflicts.setVisible(false);
        paneConflicts.setManaged(false);
        vboxConflictCards.getChildren().clear();

        paneGuidance.setVisible(false);
        paneGuidance.setManaged(false);

        applyValidationTileBorder(null);
    }

    private void showValidationError(String message) {
        clearValidationDisplay();
        lblScenarioBanner.setText("✗  " + message);
        lblScenarioBanner.getStyleClass().removeAll(
                "scenario-match", "scenario-warning", "scenario-critical");
        lblScenarioBanner.getStyleClass().add("scenario-critical");
        lblScenarioBanner.setVisible(true);
        lblScenarioBanner.setManaged(true);
        applyValidationTileBorder(AlertData.Severity.CRITICAL);
    }

    private void applyValidationTileBorder(AlertData.Severity severity) {
        if (severity == null) {
            applyTileBorder(paneValidation, null);
            return;
        }
        switch (severity) {
            case INFO:     applyTileBorder(paneValidation, "tile-up");   break;
            case WARNING:  applyTileBorder(paneValidation, "tile-warn"); break;
            case CRITICAL: applyTileBorder(paneValidation, "tile-down"); break;
        }
    }

    private void applyTileBorder(Pane pane, String styleClass) {
        pane.getStyleClass().removeAll("tile-up", "tile-warn", "tile-down");
        if (styleClass != null) {
            pane.getStyleClass().add(styleClass);
        }
    }

    private void scrollToConflicts() {
        if (paneConflicts.isVisible()) {
            paneConflicts.requestFocus();
        }
        // Expand guidance if hidden
        if (paneGuidance.isVisible() && !guidanceExpanded) {
            onToggleGuidance();
        }
    }

    private void copyToClipboard(String text, Button sourceButton) {
        if (text == null || text.isEmpty() || "N/A".equals(text)) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        showCopyToast(sourceButton);
    }

    /**
     * Shows a brief "Copied!" Tooltip near the button, then hides after 1.5s.
     * Uses a real Tooltip rather than changing button text, which was unreliable
     * for icon-only buttons like the copy ⎘ button.
     */
    private void showCopyToast(Button anchor) {
        Tooltip toast = new Tooltip("✓ Copied!");
        toast.setStyle("-fx-font-size: 12px; -fx-font-weight: bold;"
                + " -fx-background-color: #2E7D32; -fx-text-fill: white;"
                + " -fx-background-radius: 4; -fx-padding: 4 10 4 10;");

        // Show tooltip near the button
        javafx.geometry.Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds != null) {
            toast.show(anchor, bounds.getMinX(), bounds.getMaxY() + 4);
        } else {
            toast.show(anchor, 0, 0);
        }

        PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(e -> toast.hide());
        pause.play();
    }

    private void showAlert(Alert.AlertType type, String title, String header) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    private static String nvl(String s) {
        return (s != null && !s.isEmpty()) ? s : "N/A";
    }
}
