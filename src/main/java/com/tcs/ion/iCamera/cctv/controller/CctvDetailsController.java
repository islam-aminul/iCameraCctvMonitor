package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.CctvData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;

/**
 * CCTV Details page – sortable/filterable table showing only JMX-sourced data.
 */
public class CctvDetailsController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(CctvDetailsController.class);

    @FXML private Label  lblTotal;
    @FXML private Label  lblActive;
    @FXML private Label  lblInactive;
    @FXML private Label  lblWarning;
    @FXML private TextField txtSearch;
    @FXML private TableView<CctvRow> tableCctv;
    @FXML private TableColumn<CctvRow, String>     colName;
    @FXML private TableColumn<CctvRow, String>     colIp;
    @FXML private TableColumn<CctvRow, String>     colRtsp;
    @FXML private TableColumn<CctvRow, String>     colReachable;
    @FXML private TableColumn<CctvRow, String>     colFileGen;
    @FXML private TableColumn<CctvRow, String>     colFileUpload;
    @FXML private TableColumn<CctvRow, String>     colStatus;

    private final DataStore store = DataStore.getInstance();
    private final ObservableList<CctvRow> allRows = FXCollections.observableArrayList();
    private FilteredList<CctvRow> filteredRows;
    private Timeline timeline;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTable();
        filteredRows = new FilteredList<>(allRows, p -> true);
        SortedList<CctvRow> sorted = new SortedList<>(filteredRows);
        sorted.comparatorProperty().bind(tableCctv.comparatorProperty());
        tableCctv.setItems(sorted);

        txtSearch.textProperty().addListener((obs, old, val) -> {
            String lc = val.toLowerCase();
            filteredRows.setPredicate(row ->
                    lc.isEmpty() ||
                    row.getName().getValue().toLowerCase().contains(lc) ||
                    row.getIp().getValue().toLowerCase().contains(lc) ||
                    row.getRtsp().getValue().toLowerCase().contains(lc) ||
                    row.getStatus().getValue().toLowerCase().contains(lc));
        });

        timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refresh();
    }

    private void setupTable() {
        colName.setCellValueFactory(d -> d.getValue().getName());
        colIp.setCellValueFactory(d -> d.getValue().getIp());
        colRtsp.setCellValueFactory(d -> d.getValue().getRtsp());
        colReachable.setCellValueFactory(d -> d.getValue().getReachable());
        colFileGen.setCellValueFactory(d -> d.getValue().getFileGen());
        colFileUpload.setCellValueFactory(d -> d.getValue().getFileUpload());
        colStatus.setCellValueFactory(d -> d.getValue().getStatus());

        // Colour status column
        colStatus.setCellFactory(col -> new TableCell<CctvRow, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                HBox box = new HBox(5);
                box.setAlignment(Pos.CENTER_LEFT);
                Circle dot = new Circle(5);
                dot.setFill("ACTIVE".equalsIgnoreCase(item) ? Color.LIME : Color.RED);
                Label lbl = new Label(item);
                lbl.getStyleClass().add("ACTIVE".equalsIgnoreCase(item) ? "text-green" : "text-red");
                box.getChildren().addAll(dot, lbl);
                setGraphic(box);
                setText(null);
            }
        });

        // Colour binary Yes/No columns
        colouriseBoolCol(colReachable);
        colouriseBoolCol(colFileGen);
        colouriseBoolCol(colFileUpload);

        tableCctv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private void colouriseBoolCol(TableColumn<CctvRow, String> col) {
        col.setCellFactory(c -> new TableCell<CctvRow, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                getStyleClass().removeAll("text-green", "text-red", "text-white");
                boolean yes = "Yes".equalsIgnoreCase(item) || "Active".equalsIgnoreCase(item);
                getStyleClass().add(yes ? "text-green" : "text-red");
            }
        });
    }

    private void refresh() {
        Collection<CctvData> cctvs = store.getAllCctv();
        int total = cctvs.size(), active = 0;
        List<CctvRow> rows = new ArrayList<>();

        for (CctvData c : cctvs) {
            log.debug("CCTV ID: {}", c.getCctvId());
            if (c.isActive()) active++;
            rows.add(new CctvRow(c));
        }

        allRows.setAll(rows);
        lblTotal.setText("Total: " + total);
        lblActive.setText("Active: " + active);
        lblInactive.setText("Inactive: " + (total - active));
        lblWarning.setVisible(total > 25);
        lblWarning.setText("\u26A0 Warning: " + total + " CCTVs exceed recommended limit of 25");
    }

    // ---- Row model (JMX-only fields) ----

    public static class CctvRow {
        private final StringProperty  name    = new SimpleStringProperty();
        private final StringProperty  ip      = new SimpleStringProperty();
        private final StringProperty  rtsp    = new SimpleStringProperty();
        private final StringProperty  reachable  = new SimpleStringProperty();
        private final StringProperty  fileGen    = new SimpleStringProperty();
        private final StringProperty  fileUpload = new SimpleStringProperty();
        private final StringProperty  status     = new SimpleStringProperty();

        public CctvRow(CctvData c) {
            name.set(c.getCctvName());
            ip.set(c.getIpAddress());
            rtsp.set(c.getRtspUrl());
            reachable.set(c.isReachable() ? "Yes" : "No");
            fileGen.set(c.isFileGenerationActive() ? "Active" : "Stale");
            fileUpload.set(c.isFileUploadActive() ? "Active" : "Stale");
            status.set(c.isActive() ? "ACTIVE" : "INACTIVE");
        }

        public StringProperty  getName()     { return name; }
        public StringProperty  getIp()       { return ip; }
        public StringProperty  getRtsp()     { return rtsp; }
        public StringProperty  getReachable(){ return reachable; }
        public StringProperty  getFileGen()  { return fileGen; }
        public StringProperty  getFileUpload(){ return fileUpload; }
        public StringProperty  getStatus()   { return status; }
    }
}
