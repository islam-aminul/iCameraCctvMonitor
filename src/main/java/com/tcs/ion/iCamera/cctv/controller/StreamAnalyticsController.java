package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.CctvData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.animation.*;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.net.URL;
import java.util.*;

/**
 * Stream Analytics page – real-time ffprobe metrics for all RTSP streams.
 */
public class StreamAnalyticsController implements Initializable {

    @FXML private TextField txtSearch;
    @FXML private TableView<StreamRow> tableStreams;
    @FXML private TableColumn<StreamRow, String>   colName;
    @FXML private TableColumn<StreamRow, String>   colIp;
    @FXML private TableColumn<StreamRow, String>   colRtsp;
    @FXML private TableColumn<StreamRow, String>   colEncoding;
    @FXML private TableColumn<StreamRow, String>   colProfile;
    @FXML private TableColumn<StreamRow, String>   colFps;
    @FXML private TableColumn<StreamRow, String>   colResolution;
    @FXML private TableColumn<StreamRow, String>   colBitrate;
    @FXML private TableColumn<StreamRow, String>   colKeyframe;
    @FXML private TableColumn<StreamRow, String>   colProbeStatus;
    @FXML private TableColumn<StreamRow, String>   colError;
    @FXML private Label lblLastProbeTime;
    @FXML private Label lblTotalStreams;

    private final DataStore store = DataStore.getInstance();
    private final ObservableList<StreamRow> allRows = FXCollections.observableArrayList();
    private FilteredList<StreamRow> filtered;
    private Timeline timeline;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        colName.setCellValueFactory(d -> d.getValue().name);
        colIp.setCellValueFactory(d -> d.getValue().ip);
        colRtsp.setCellValueFactory(d -> d.getValue().rtsp);
        colEncoding.setCellValueFactory(d -> d.getValue().encoding);
        colProfile.setCellValueFactory(d -> d.getValue().profile);
        colFps.setCellValueFactory(d -> d.getValue().fps);
        colResolution.setCellValueFactory(d -> d.getValue().resolution);
        colBitrate.setCellValueFactory(d -> d.getValue().bitrate);
        colKeyframe.setCellValueFactory(d -> d.getValue().keyframe);
        colProbeStatus.setCellValueFactory(d -> d.getValue().probeStatus);
        colError.setCellValueFactory(d -> d.getValue().error);

        // Colour probe status
        colProbeStatus.setCellFactory(col -> new TableCell<StreamRow, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                getStyleClass().removeAll("text-green", "text-red");
                getStyleClass().add("OK".equals(item) ? "text-green" : "text-red");
            }
        });

        // Colour error column red when non-empty
        colError.setCellFactory(col -> new TableCell<StreamRow, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) { setText(null); return; }
                setText(item);
                getStyleClass().removeAll("text-red");
                getStyleClass().add("text-red");
            }
        });

        filtered = new FilteredList<>(allRows, p -> true);
        txtSearch.textProperty().addListener((obs, o, n) -> {
            String lc = n.toLowerCase();
            filtered.setPredicate(r -> lc.isEmpty() ||
                    r.name.get().toLowerCase().contains(lc) ||
                    r.ip.get().toLowerCase().contains(lc) ||
                    r.encoding.get().toLowerCase().contains(lc));
        });
        SortedList<StreamRow> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(tableStreams.comparatorProperty());
        tableStreams.setItems(sorted);
        tableStreams.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        timeline = new Timeline(new KeyFrame(Duration.seconds(5), e -> refresh()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
        refresh();
    }

    private void refresh() {
        Collection<CctvData> cctvs = store.getAllCctv();
        List<StreamRow> rows = new ArrayList<>();
        for (CctvData c : cctvs) {
            rows.add(new StreamRow(c));
        }
        allRows.setAll(rows);
        lblTotalStreams.setText("Streams: " + cctvs.size());
        if (store.getLastPollTime() != null) {
            lblLastProbeTime.setText("Last probe: " + store.getLastPollTime()
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")));
        }
    }

    public static class StreamRow {
        final StringProperty  name = new SimpleStringProperty();
        final StringProperty  ip   = new SimpleStringProperty();
        final StringProperty  rtsp = new SimpleStringProperty();
        final StringProperty  encoding    = new SimpleStringProperty();
        final StringProperty  profile     = new SimpleStringProperty();
        final StringProperty  fps         = new SimpleStringProperty();
        final StringProperty  resolution  = new SimpleStringProperty();
        final StringProperty  bitrate     = new SimpleStringProperty();
        final StringProperty  keyframe    = new SimpleStringProperty();
        final StringProperty  probeStatus = new SimpleStringProperty();
        final StringProperty  error       = new SimpleStringProperty();

        StreamRow(CctvData c) {
            name.set(c.getCctvName());
            ip.set(nvl(c.getIpAddress()));
            rtsp.set(nvl(c.getRtspUrl()));
            encoding.set(nvl(c.getEncoding()));
            profile.set(nvl(c.getStreamProfile()));
            fps.set(c.getFps() > 0 ? String.format("%.2f fps", c.getFps()) : "N/A");
            resolution.set(nvl(c.getResolution()));
            bitrate.set(c.getBitrateKbps() > 0 ? c.getBitrateKbps() + " Kbps" : "N/A");
            keyframe.set(c.getKeyFrameInterval() > 0 ? String.valueOf(c.getKeyFrameInterval()) : "N/A");
            probeStatus.set(c.isFfprobeSuccess() ? "OK" : "FAILED");
            error.set(c.getInactiveReason() != null ? c.getInactiveReason() : "");
        }

        private static String nvl(String s) { return s != null ? s : "N/A"; }
    }
}
