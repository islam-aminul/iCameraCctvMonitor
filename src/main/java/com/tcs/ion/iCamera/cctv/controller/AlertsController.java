package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.AlertData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import com.tcs.ion.iCamera.cctv.service.HttpService;
import javafx.animation.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Alerts page – filterable/searchable table of all alerts with blink effect.
 * Alerts can be resolved here and optionally pushed to cloud.
 */
public class AlertsController implements Initializable {

    @FXML private Label           lblTotalAlerts;
    @FXML private Label           lblCritical;
    @FXML private Label           lblWarnings;
    @FXML private TextField       txtSearch;
    @FXML private ComboBox<String> cmbFilter;
    @FXML private TableView<AlertData> tableAlerts;
    @FXML private TableColumn<AlertData, String> colTime;
    @FXML private TableColumn<AlertData, String> colSeverity;
    @FXML private TableColumn<AlertData, String> colCategory;
    @FXML private TableColumn<AlertData, String> colSource;
    @FXML private TableColumn<AlertData, String> colMessage;
    @FXML private TableColumn<AlertData, String> colResolved;
    @FXML private Button btnClearResolved;
    @FXML private Button btnExportAlerts;
    @FXML private Button btnPushToCloud;

    private final DataStore store = DataStore.getInstance();
    private final HttpService httpService = new HttpService();
    private final ObservableList<AlertData> allAlerts = FXCollections.observableArrayList();
    private FilteredList<AlertData> filtered;
    private Timeline timeline;
    private Timeline blinkTimeline;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Filter combo
        cmbFilter.getItems().addAll("ALL", "CRITICAL", "WARNING", "INFO", "UNRESOLVED");
        cmbFilter.setValue("UNRESOLVED");

        // Table columns
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTimestampDisplay()));
        colSeverity.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSeverity().name()));
        colCategory.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory().name()));
        colSource.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSource()));
        colMessage.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getMessage()));
        colResolved.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isResolved() ? "Yes" : "No"));

        // Colour severity column
        colSeverity.setCellFactory(col -> new TableCell<AlertData, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                getStyleClass().removeAll("text-red", "text-yellow", "text-blue");
                switch (item) {
                    case "CRITICAL": getStyleClass().add("text-red"); break;
                    case "WARNING":  getStyleClass().add("text-yellow"); break;
                    default:         getStyleClass().add("text-blue"); break;
                }
            }
        });

        // Row factory – highlight unresolved CRITICAL
        tableAlerts.setRowFactory(tv -> new TableRow<AlertData>() {
            @Override protected void updateItem(AlertData item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("row-critical", "row-warning", "row-resolved");
                if (!empty && item != null) {
                    if (item.isResolved()) getStyleClass().add("row-resolved");
                    else if (item.getSeverity() == AlertData.Severity.CRITICAL) getStyleClass().add("row-critical");
                    else if (item.getSeverity() == AlertData.Severity.WARNING)  getStyleClass().add("row-warning");
                }
            }
        });

        // Filtered list
        filtered = new FilteredList<>(allAlerts, p -> true);
        SortedList<AlertData> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tableAlerts.comparatorProperty());
        tableAlerts.setItems(sorted);

        // Filter predicates
        cmbFilter.valueProperty().addListener((obs, o, n) -> applyFilter());
        txtSearch.textProperty().addListener((obs, o, n) -> applyFilter());

        // Refresh
        timeline = new Timeline(new KeyFrame(Duration.seconds(3), e -> refresh()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refresh();

        tableAlerts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void applyFilter() {
        String filterVal = cmbFilter.getValue();
        String search = txtSearch.getText().toLowerCase();
        filtered.setPredicate(a -> {
            boolean matchFilter = true;
            if ("CRITICAL".equals(filterVal)) matchFilter = a.getSeverity() == AlertData.Severity.CRITICAL;
            else if ("WARNING".equals(filterVal)) matchFilter = a.getSeverity() == AlertData.Severity.WARNING;
            else if ("INFO".equals(filterVal)) matchFilter = a.getSeverity() == AlertData.Severity.INFO;
            else if ("UNRESOLVED".equals(filterVal)) matchFilter = !a.isResolved();
            boolean matchSearch = search.isEmpty() ||
                    a.getMessage().toLowerCase().contains(search) ||
                    a.getSource().toLowerCase().contains(search) ||
                    a.getCategory().name().toLowerCase().contains(search);
            return matchFilter && matchSearch;
        });
    }

    private void refresh() {
        List<AlertData> current = store.getAlerts();
        allAlerts.setAll(current);

        long critical = current.stream().filter(a -> !a.isResolved() && a.getSeverity() == AlertData.Severity.CRITICAL).count();
        long warnings = current.stream().filter(a -> !a.isResolved() && a.getSeverity() == AlertData.Severity.WARNING).count();
        long unresolved = current.stream().filter(a -> !a.isResolved()).count();

        lblTotalAlerts.setText("Total: " + current.size() + "  Unresolved: " + unresolved);
        lblCritical.setText("Critical: " + critical);
        lblWarnings.setText("Warnings: " + warnings);

        // Blink critical count
        if (critical > 0) {
            lblCritical.getStyleClass().removeAll("text-red");
            lblCritical.getStyleClass().add("text-red");
            if (!lblCritical.getStyleClass().contains("blinking")) lblCritical.getStyleClass().add("blinking");
        } else {
            lblCritical.getStyleClass().remove("blinking");
        }
    }

    @FXML private void onResolveSelected() {
        AlertData selected = tableAlerts.getSelectionModel().getSelectedItem();
        if (selected != null) {
            store.resolveAlert(selected.getId());
            refresh();
        }
    }

    @FXML private void onClearResolved() {
        store.clearResolvedAlerts();
        refresh();
    }

    @FXML private void onExportAlerts() {
        StringBuilder sb = new StringBuilder();
        for (AlertData a : store.getAlerts()) sb.append(a.toJson()).append("\n");
        // Show in dialog
        TextArea ta = new TextArea(sb.toString());
        ta.setEditable(false);
        ta.setPrefRowCount(20);
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Alerts JSON Export");
        dialog.setHeaderText("Alert data in JSON format");
        dialog.getDialogPane().setContent(ta);
        dialog.showAndWait();
    }

    @FXML private void onPushToCloud() {
        List<AlertData> unresolvedAlerts = store.getUnresolvedAlerts();
        if (unresolvedAlerts.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, "No unresolved alerts to push.").showAndWait();
            return;
        }
        for (AlertData a : unresolvedAlerts) {
            httpService.pushAlertToCloud(a.toJson());
        }
        new Alert(Alert.AlertType.INFORMATION, unresolvedAlerts.size() + " alerts pushed to cloud endpoint.").showAndWait();
    }
}
