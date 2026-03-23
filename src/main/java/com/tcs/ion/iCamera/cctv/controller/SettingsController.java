package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.AppSettings;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import com.tcs.ion.iCamera.cctv.util.SettingsManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.paint.Color;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Settings page – JMX, polling, ffprobe path, theme, export, dashboard customisation.
 */
public class SettingsController implements Initializable {

    @FXML private TextField  txtJmxHost;
    @FXML private TextField  txtJmxPort;
    @FXML private TextField  txtJmxRetries;
    @FXML private TextField  txtPollInterval;
    @FXML private TextField  txtJettyPort;
    @FXML private TextField  txtExportPath;
    @FXML private ComboBox<String> cmbExportFormat;
    @FXML private ComboBox<String> cmbTheme;
    @FXML private TextField  txtFontFamily;
    @FXML private Spinner<Double> spFontSize;
    @FXML private ColorPicker cpAccent;
    @FXML private Label      lblStatus;

    // Dashboard tile toggles
    @FXML private CheckBox cbProxyTile;
    @FXML private CheckBox cbHsqldbTile;
    @FXML private CheckBox cbCctvTile;
    @FXML private CheckBox cbSystemTile;
    @FXML private CheckBox cbCpuTile;
    @FXML private CheckBox cbMemTile;
    @FXML private CheckBox cbDiskTile;
    @FXML private CheckBox cbNetworkTile;
    @FXML private CheckBox cbMacTile;

    private final DataStore store = DataStore.getInstance();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cmbExportFormat.getItems().addAll("XLSX", "CSV", "JSON");
        cmbTheme.getItems().addAll("DARK", "LIGHT");
        spFontSize.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(9, 24, 13, 0.5));

        loadSettings();
    }

    private void loadSettings() {
        AppSettings s = store.getSettings();
        txtJmxHost.setText(s.getJmxHost());
        txtJmxPort.setText(String.valueOf(s.getJmxBasePort()));
        txtJmxRetries.setText(String.valueOf(s.getJmxMaxPortRetries()));
        txtPollInterval.setText(String.valueOf(s.getPollIntervalSeconds()));
        txtJettyPort.setText(String.valueOf(s.getJettyPort()));
        txtExportPath.setText(s.getExportPath());
        cmbExportFormat.setValue(s.getExportFormat());
        cmbTheme.setValue(s.getTheme());
        txtFontFamily.setText(s.getFontFamily());
        spFontSize.getValueFactory().setValue(s.getFontSize());
        try { cpAccent.setValue(Color.web(s.getAccentColor())); } catch (Exception ignored) {}

        // Dashboard tiles
        cbProxyTile.setSelected(s.getDashboardTiles().contains("PROXY_STATUS"));
        cbHsqldbTile.setSelected(s.getDashboardTiles().contains("HSQLDB_STATUS"));
        cbCctvTile.setSelected(s.getDashboardTiles().contains("CCTV_STATUS"));
        cbSystemTile.setSelected(s.getDashboardTiles().contains("SYSTEM_HEALTH"));
        cbCpuTile.setSelected(s.getDashboardTiles().contains("CPU_USAGE"));
        cbMemTile.setSelected(s.getDashboardTiles().contains("MEMORY_USAGE"));
        cbDiskTile.setSelected(s.getDashboardTiles().contains("DISK_USAGE"));
        cbNetworkTile.setSelected(s.getDashboardTiles().contains("NETWORK_STATUS"));
        cbMacTile.setSelected(s.getDashboardTiles().contains("MAC_DETAILS"));
    }

    @FXML
    private void onSaveSettings() {
        try {
            AppSettings s = store.getSettings();
            s.setJmxHost(txtJmxHost.getText().trim());
            s.setJmxBasePort(Integer.parseInt(txtJmxPort.getText().trim()));
            s.setJmxMaxPortRetries(Integer.parseInt(txtJmxRetries.getText().trim()));
            s.setPollIntervalSeconds(Integer.parseInt(txtPollInterval.getText().trim()));
            s.setJettyPort(Integer.parseInt(txtJettyPort.getText().trim()));
            s.setExportPath(txtExportPath.getText().trim());
            s.setExportFormat(cmbExportFormat.getValue());
            s.setTheme(cmbTheme.getValue());
            s.setFontFamily(txtFontFamily.getText().trim());
            s.setFontSize(spFontSize.getValue());
            s.setAccentColor(toHex(cpAccent.getValue()));

            // Dashboard tiles
            s.getDashboardTiles().clear();
            if (cbProxyTile.isSelected())   s.getDashboardTiles().add("PROXY_STATUS");
            if (cbHsqldbTile.isSelected())  s.getDashboardTiles().add("HSQLDB_STATUS");
            if (cbCctvTile.isSelected())    s.getDashboardTiles().add("CCTV_STATUS");
            if (cbSystemTile.isSelected())  s.getDashboardTiles().add("SYSTEM_HEALTH");
            if (cbCpuTile.isSelected())     s.getDashboardTiles().add("CPU_USAGE");
            if (cbMemTile.isSelected())     s.getDashboardTiles().add("MEMORY_USAGE");
            if (cbDiskTile.isSelected())    s.getDashboardTiles().add("DISK_USAGE");
            if (cbNetworkTile.isSelected()) s.getDashboardTiles().add("NETWORK_STATUS");
            if (cbMacTile.isSelected())     s.getDashboardTiles().add("MAC_DETAILS");

            SettingsManager.save(s);
            lblStatus.setText("Settings saved successfully.");
            lblStatus.getStyleClass().removeAll("text-red");
            lblStatus.getStyleClass().add("text-green");
        } catch (NumberFormatException e) {
            lblStatus.setText("Invalid number in settings: " + e.getMessage());
            lblStatus.getStyleClass().removeAll("text-green");
            lblStatus.getStyleClass().add("text-red");
        }
    }

    @FXML
    private void onResetDefaults() {
        store.setSettings(new AppSettings());
        loadSettings();
        lblStatus.setText("Defaults restored. Click Save to persist.");
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int)(c.getRed() * 255), (int)(c.getGreen() * 255), (int)(c.getBlue() * 255));
    }
}
