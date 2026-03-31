package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import com.tcs.ion.iCamera.cctv.service.HttpService;
import javafx.animation.*;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * MAC Address details page – shows current/last MAC with mismatch warning and sync.
 */
public class MacDetailsController implements Initializable {

    @FXML private Label lblCurrentMac;
    @FXML private Label lblLastMac;
    @FXML private Label lblMacMismatch;
    @FXML private Button btnSyncMac;
    @FXML private Pane paneMacSection;

    private final DataStore store = DataStore.getInstance();
    private final HttpService httpService = new HttpService();
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
            javafx.application.Platform.runLater(() -> {
                btnSyncMac.setDisable(false);
                btnSyncMac.setText("Sync MAC Address");
                Alert alert = new Alert(ok ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                alert.setTitle("MAC Sync");
                alert.setHeaderText(ok ? "MAC address synced successfully" : "MAC sync failed");
                alert.showAndWait();
            });
        }).start();
    }

    private String nvl(String s) { return (s != null && !s.isEmpty()) ? s : "N/A"; }
}
