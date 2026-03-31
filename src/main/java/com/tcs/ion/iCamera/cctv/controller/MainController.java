package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.AlertData;
import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import com.tcs.ion.iCamera.cctv.util.EmailHelper;
import com.tcs.ion.iCamera.cctv.util.ExcelExporter;
import com.tcs.ion.iCamera.cctv.util.IssueResolutionProvider;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Main shell controller – manages tab-based navigation, enhanced header,
 * active issues panel, and export functionality.
 */
public class MainController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter EXPORT_FMT = DateTimeFormatter.ofPattern("yyyyMMdd HHmm");

    // --- Header ---
    @FXML private Label lblTitle;
    @FXML private Label lblTcCode;
    @FXML private Label lblProxyStatusPill;
    @FXML private Label lblOverallRag;
    @FXML private Label lblClock;
    @FXML private Label lblLastRefresh;
    @FXML private Label lblAlertBadge;

    // --- Tab navigation ---
    @FXML private HBox tabBar;
    @FXML private HBox subTabBar;
    @FXML private Button tabDashboard;
    @FXML private Button tabApplication;
    @FXML private Button tabSystem;
    @FXML private Button tabAlerts;
    @FXML private Button tabDiscovery;
    @FXML private Button tabSettings;

    // --- Content ---
    @FXML private StackPane contentArea;

    // --- Issues panel ---
    @FXML private VBox issuesPanel;
    @FXML private VBox issuesList;
    @FXML private ScrollPane issuesScrollPane;
    @FXML private Label lblIssueCount;
    @FXML private Button btnExport;

    // --- Sub-tab definitions ---
    private static final String[][] APPLICATION_SUBTABS = {
            {"Proxy & HSQLDB",   "application_details"},
            {"CCTV Details",     "cctv_details"},
            {"Stream Analytics", "stream_analytics"},
            {"VMS Detection",    "vms_detection"}
    };

    private static final String[][] SYSTEM_SUBTABS = {
            {"Hardware",        "system_hardware"},
            {"Network Monitor", "network_monitor"}
    };

    private final DataStore store = DataStore.getInstance();
    private Timeline refreshTimeline;
    private Button activeMainTab;
    private Button activeSubTab;
    private String activeMainTabId;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblTitle.setText("iCamera CCTV Monitor");

        // Start refresh cycles
        startRefreshCycle();

        // Default view: Dashboard
        showDashboard();
    }

    // ===== Level 1 Tab Navigation =====

    @FXML
    private void showDashboard() {
        setActiveMainTab(tabDashboard, "dashboard");
        hideSubTabs();
        navigateTo("dashboard");
    }

    @FXML
    private void showApplication() {
        setActiveMainTab(tabApplication, "application");
        showSubTabs(APPLICATION_SUBTABS);
        navigateTo(APPLICATION_SUBTABS[0][1]); // Default to first sub-tab
    }

    @FXML
    private void showSystem() {
        setActiveMainTab(tabSystem, "system");
        showSubTabs(SYSTEM_SUBTABS);
        navigateTo(SYSTEM_SUBTABS[0][1]); // Default to first sub-tab
    }

    @FXML
    private void showAlerts() {
        setActiveMainTab(tabAlerts, "alerts");
        hideSubTabs();
        navigateTo("alerts");
    }

    @FXML
    private void showDiscovery() {
        setActiveMainTab(tabDiscovery, "discovery");
        hideSubTabs();
        navigateTo("discovery");
    }

    @FXML
    private void showSettings() {
        setActiveMainTab(tabSettings, "settings");
        hideSubTabs();
        navigateTo("settings");
    }

    private void setActiveMainTab(Button tab, String tabId) {
        if (activeMainTab != null) activeMainTab.getStyleClass().remove("main-tab-active");
        activeMainTab = tab;
        activeMainTabId = tabId;
        if (tab != null) tab.getStyleClass().add("main-tab-active");
    }

    // ===== Level 2 Sub-Tab Navigation =====

    private void showSubTabs(String[][] subTabs) {
        subTabBar.getChildren().clear();
        subTabBar.setVisible(true);
        subTabBar.setManaged(true);
        activeSubTab = null;

        for (String[] sub : subTabs) {
            Button btn = new Button(sub[0]);
            btn.getStyleClass().add("sub-tab");
            String viewName = sub[1];
            btn.setOnAction(e -> {
                setActiveSubTab(btn);
                navigateTo(viewName);
            });
            subTabBar.getChildren().add(btn);
        }

        // Activate first sub-tab
        if (!subTabBar.getChildren().isEmpty()) {
            Button first = (Button) subTabBar.getChildren().get(0);
            setActiveSubTab(first);
        }
    }

    private void hideSubTabs() {
        subTabBar.setVisible(false);
        subTabBar.setManaged(false);
        subTabBar.getChildren().clear();
        activeSubTab = null;
    }

    private void setActiveSubTab(Button btn) {
        if (activeSubTab != null) activeSubTab.getStyleClass().remove("sub-tab-active");
        activeSubTab = btn;
        if (btn != null) btn.getStyleClass().add("sub-tab-active");
    }

    // ===== Content Loading =====

    private void navigateTo(String viewName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/" + viewName + ".fxml"));
            Node view = loader.load();
            contentArea.getChildren().setAll(view);
        } catch (IOException e) {
            log.error("Failed to load view: {}", viewName, e);
            Label icon = new Label("\u26A0");
            icon.getStyleClass().add("error-icon");
            Label heading = new Label("Unable to Load View");
            heading.getStyleClass().add("error-heading");
            Label detail = new Label("This section could not be displayed.\nPlease check the application logs.");
            detail.getStyleClass().add("error-detail");
            detail.setWrapText(true);
            VBox box = new VBox(14, icon, heading, detail);
            box.getStyleClass().add("error-page");
            box.setAlignment(Pos.CENTER);
            contentArea.getChildren().setAll(box);
        }
    }

    // ===== Header & Issues Panel Refresh =====

    private void startRefreshCycle() {
        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> updateClock()),
                new KeyFrame(Duration.seconds(3), e -> {
                    updateHeader();
                    updateIssuesPanel();
                })
        );
        refreshTimeline.setCycleCount(Animation.INDEFINITE);
        refreshTimeline.play();

        // Immediate first paint
        updateClock();
        updateHeader();
        updateIssuesPanel();
    }

    private void updateClock() {
        lblClock.setText(LocalDateTime.now().format(TIME_FMT));
    }

    private void updateHeader() {
        // TC Code
        ProxyData pd = store.getProxyData();
        if (pd != null) {
            String tc = pd.getTcCode();
            lblTcCode.setText(tc != null && !tc.isEmpty() ? "TC: " + tc : "");

            // Proxy status pill
            String status = pd.getStatus() != null ? pd.getStatus() : "UNKNOWN";
            lblProxyStatusPill.setText(status);
            lblProxyStatusPill.getStyleClass().removeAll("rag-green", "rag-amber", "rag-red");
            switch (status) {
                case "UP":       lblProxyStatusPill.getStyleClass().add("rag-green"); break;
                case "DEGRADED": lblProxyStatusPill.getStyleClass().add("rag-amber"); break;
                default:         lblProxyStatusPill.getStyleClass().add("rag-red"); break;
            }
        } else {
            lblTcCode.setText("");
            lblProxyStatusPill.setText("--");
            lblProxyStatusPill.getStyleClass().removeAll("rag-green", "rag-amber", "rag-red");
        }

        // Last refresh
        if (store.getLastPollTime() != null) {
            lblLastRefresh.setText("Updated: " + DT_FMT.format(store.getLastPollTime()));
        }

        // Overall RAG status
        List<AlertData> unresolved = store.getUnresolvedAlerts();
        boolean hasCritical = false;
        boolean hasWarning = false;
        for (AlertData a : unresolved) {
            if (a.getSeverity() == AlertData.Severity.CRITICAL) hasCritical = true;
            else if (a.getSeverity() == AlertData.Severity.WARNING) hasWarning = true;
        }

        lblOverallRag.getStyleClass().removeAll("rag-green", "rag-amber", "rag-red");
        if (hasCritical) {
            lblOverallRag.setText("CRITICAL");
            lblOverallRag.getStyleClass().add("rag-red");
        } else if (hasWarning) {
            lblOverallRag.setText("WARNING");
            lblOverallRag.getStyleClass().add("rag-amber");
        } else {
            lblOverallRag.setText("OK");
            lblOverallRag.getStyleClass().add("rag-green");
        }

        // Alert badge on Alerts tab
        int alertCount = unresolved.size();
        if (alertCount > 0) {
            lblAlertBadge.setText(String.valueOf(alertCount));
            lblAlertBadge.setVisible(true);
        } else {
            lblAlertBadge.setVisible(false);
        }
    }

    // ===== Active Issues Panel =====

    private void updateIssuesPanel() {
        List<AlertData> unresolved = store.getUnresolvedAlerts();

        // Filter to CRITICAL and WARNING only
        List<AlertData> issues = new ArrayList<>();
        for (AlertData a : unresolved) {
            if (a.getSeverity() == AlertData.Severity.CRITICAL
                    || a.getSeverity() == AlertData.Severity.WARNING) {
                issues.add(a);
            }
        }

        // Sort: CRITICAL first, then by timestamp descending
        issues.sort((a, b) -> {
            int sevCompare = Integer.compare(
                    severityOrder(b.getSeverity()), severityOrder(a.getSeverity()));
            if (sevCompare != 0) return sevCompare;
            return b.getTimestamp().compareTo(a.getTimestamp());
        });

        lblIssueCount.setText(String.valueOf(issues.size()));
        lblIssueCount.getStyleClass().removeAll("issue-count-critical", "issue-count-warning", "issue-count-ok");
        if (issues.stream().anyMatch(a -> a.getSeverity() == AlertData.Severity.CRITICAL)) {
            lblIssueCount.getStyleClass().add("issue-count-critical");
        } else if (!issues.isEmpty()) {
            lblIssueCount.getStyleClass().add("issue-count-warning");
        } else {
            lblIssueCount.getStyleClass().add("issue-count-ok");
        }

        issuesList.getChildren().clear();

        if (issues.isEmpty()) {
            Label noIssues = new Label("No active issues");
            noIssues.getStyleClass().add("no-issues-label");
            noIssues.setMaxWidth(Double.MAX_VALUE);
            noIssues.setAlignment(Pos.CENTER);
            noIssues.setPadding(new Insets(20));
            issuesList.getChildren().add(noIssues);
            return;
        }

        for (AlertData alert : issues) {
            issuesList.getChildren().add(createIssueCard(alert));
        }
    }

    private VBox createIssueCard(AlertData alert) {
        IssueResolutionProvider.Resolution resolution =
                IssueResolutionProvider.getResolution(alert.getParameter());

        boolean isCritical = alert.getSeverity() == AlertData.Severity.CRITICAL;

        // Card container
        VBox card = new VBox(4);
        card.getStyleClass().add("issue-card");
        card.getStyleClass().add(isCritical ? "issue-card-critical" : "issue-card-warning");
        card.setPadding(new Insets(8, 10, 8, 10));

        // Top row: severity pill + age
        HBox topRow = new HBox(6);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label severityPill = new Label(alert.getSeverity().name());
        severityPill.getStyleClass().add("issue-severity-pill");
        severityPill.getStyleClass().add(isCritical ? "severity-critical" : "severity-warning");

        Label age = new Label(formatAge(alert.getTimestamp()));
        age.getStyleClass().add("issue-age");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        topRow.getChildren().addAll(severityPill, spacer, age);

        // Title
        Label title = new Label(resolution.getTitle());
        title.getStyleClass().add("issue-title");
        title.setWrapText(true);
        title.setMaxWidth(260);

        // Root cause (truncated)
        Label cause = new Label(resolution.getRootCause());
        cause.getStyleClass().add("issue-cause");
        cause.setWrapText(true);
        cause.setMaxWidth(260);

        // Expand link to show resolution steps
        Label expandLink = new Label("\u25B8 View Resolution Steps");
        expandLink.getStyleClass().add("issue-expand-link");
        expandLink.setOnMouseClicked(e -> showResolutionDialog(alert, resolution));

        // Tooltip with root cause on the whole card
        Tooltip tooltip = new Tooltip(resolution.getRootCause());
        tooltip.setMaxWidth(280);
        tooltip.setWrapText(true);
        Tooltip.install(card, tooltip);

        card.getChildren().addAll(topRow, title, cause, expandLink);
        return card;
    }

    private void showResolutionDialog(AlertData alert, IssueResolutionProvider.Resolution resolution) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Resolution Steps");
        dialog.setHeaderText(resolution.getTitle());
        dialog.initOwner(contentArea.getScene().getWindow());

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(450);

        // Severity & alert info
        Label severityLabel = new Label("Severity: " + alert.getSeverity().name()
                + "  |  Category: " + alert.getCategory().name());
        severityLabel.getStyleClass().add("detail-value");

        // Root cause
        Label causeHeader = new Label("Root Cause:");
        causeHeader.getStyleClass().add("section-header");
        Label causeText = new Label(resolution.getRootCause());
        causeText.setWrapText(true);
        causeText.getStyleClass().add("detail-value");

        // Resolution steps
        Label stepsHeader = new Label("Resolution Steps:");
        stepsHeader.getStyleClass().add("section-header");

        VBox stepsList = new VBox(6);
        List<String> steps = resolution.getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Label step = new Label((i + 1) + ". " + steps.get(i));
            step.setWrapText(true);
            step.getStyleClass().add("detail-value");
            step.setMaxWidth(420);
            stepsList.getChildren().add(step);
        }

        // Alert message
        Label msgHeader = new Label("Alert Message:");
        msgHeader.getStyleClass().add("section-header");
        Label msgText = new Label(alert.getMessage());
        msgText.setWrapText(true);
        msgText.getStyleClass().add("detail-value");

        content.getChildren().addAll(severityLabel, new Separator(),
                causeHeader, causeText, new Separator(),
                stepsHeader, stepsList, new Separator(),
                msgHeader, msgText);

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(400);
        scroll.getStyleClass().addAll("issues-scroll");

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(500);
        dialog.showAndWait();
    }

    // ===== Export to Excel =====

    @FXML
    private void onExportToExcel() {
        Stage stage = (Stage) contentArea.getScene().getWindow();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save iCamera Report");

        // Default to user's Documents folder
        File documentsDir = new File(System.getProperty("user.home"), "Documents");
        if (documentsDir.exists() && documentsDir.isDirectory()) {
            fileChooser.setInitialDirectory(documentsDir);
        } else {
            fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));
        }

        // Default filename
        String defaultName = "iCamera Report " + LocalDateTime.now().format(EXPORT_FMT) + ".xlsx";
        fileChooser.setInitialFileName(defaultName);
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Workbook (*.xlsx)", "*.xlsx"));

        File selectedFile = fileChooser.showSaveDialog(stage);
        if (selectedFile == null) return; // User cancelled

        try {
            ExcelExporter.exportToFile(store, selectedFile);
            String filePath = selectedFile.getAbsolutePath();

            // Show confirmation dialog
            EmailHelper.showExportConfirmation(filePath, stage);

            // Prepare email
            String subject = EmailHelper.buildSubject(store);
            String body = EmailHelper.buildBody(store);

            if (EmailHelper.isDesktopMailAvailable()) {
                // Open mail client with subject and body
                boolean opened = EmailHelper.openMailClient(subject, body);
                if (opened) {
                    // Show reminder to attach the file
                    Alert reminder = new Alert(Alert.AlertType.INFORMATION);
                    reminder.setTitle("Email Draft Opened");
                    reminder.setHeaderText("Email draft opened in your mail client");
                    reminder.setContentText("Please attach the exported report:\n" + filePath
                            + "\n\nThen click Send.");
                    reminder.initOwner(stage);
                    reminder.showAndWait();
                }
            } else {
                // No mail client: copy to clipboard and show instructions
                EmailHelper.copyToClipboard(subject, body);
                EmailHelper.showFallbackInstructions(filePath, stage);
            }
        } catch (IOException ex) {
            log.error("Export failed", ex);
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Export Failed");
            error.setHeaderText("Could not export report");
            error.setContentText("Error: " + ex.getMessage());
            error.initOwner(stage);
            error.showAndWait();
        }
    }

    // ===== Utility =====

    private String formatAge(Instant timestamp) {
        if (timestamp == null) return "";
        long minutes = ChronoUnit.MINUTES.between(timestamp, Instant.now());
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h " + (minutes % 60) + "m ago";
        long days = hours / 24;
        return days + "d " + (hours % 24) + "h ago";
    }

    private static int severityOrder(AlertData.Severity s) {
        switch (s) {
            case CRITICAL: return 2;
            case WARNING:  return 1;
            default:       return 0;
        }
    }
}
