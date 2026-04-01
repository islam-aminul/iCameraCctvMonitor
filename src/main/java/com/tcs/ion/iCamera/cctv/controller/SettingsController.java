package com.tcs.ion.iCamera.cctv.controller;

import com.tcs.ion.iCamera.cctv.model.AppSettings;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import com.tcs.ion.iCamera.cctv.util.SettingsManager;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Settings page – JMX Connection (disabled), Services (disabled), Appearance (live changes).
 */
public class SettingsController implements Initializable {

    @FXML private TextField  txtJmxHost;
    @FXML private TextField  txtJmxPort;
    @FXML private TextField  txtJmxRetries;
    @FXML private TextField  txtPollInterval;
    @FXML private TextField  txtJettyPort;
    @FXML private ComboBox<String> cmbTheme;
    @FXML private ComboBox<String> cmbFontFamily;
    @FXML private Spinner<Double> spFontSize;
    @FXML private ColorPicker cpAccent;

    private final DataStore store = DataStore.getInstance();
    private boolean initializing = true;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Theme options
        cmbTheme.getItems().addAll("DARK", "LIGHT");

        // Font family dropdown populated from system fonts
        cmbFontFamily.getItems().addAll(Font.getFamilies());

        // Font size spinner
        spFontSize.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(9, 24, 13, 0.5));

        // Load current settings
        loadSettings();

        // Add live-change listeners for Appearance controls
        cmbFontFamily.valueProperty().addListener((obs, o, n) -> applyAppearanceLive());
        spFontSize.valueProperty().addListener((obs, o, n) -> applyAppearanceLive());
        cpAccent.valueProperty().addListener((obs, o, n) -> applyAppearanceLive());
        cmbTheme.valueProperty().addListener((obs, o, n) -> applyAppearanceLive());

        initializing = false;
    }

    private void loadSettings() {
        AppSettings s = store.getSettings();
        txtJmxHost.setText(s.getJmxHost());
        txtJmxPort.setText(String.valueOf(s.getJmxBasePort()));
        txtJmxRetries.setText(String.valueOf(s.getJmxMaxPortRetries()));
        txtPollInterval.setText(String.valueOf(s.getPollIntervalSeconds()));
        txtJettyPort.setText(String.valueOf(s.getJettyPort()));
        cmbTheme.setValue(s.getTheme());
        cmbFontFamily.setValue(s.getFontFamily());
        spFontSize.getValueFactory().setValue(s.getFontSize());
        try { cpAccent.setValue(Color.web(s.getAccentColor())); } catch (Exception ignored) {}
    }

    private void applyAppearanceLive() {
        if (initializing) return;

        String font = cmbFontFamily.getValue();
        double size = spFontSize.getValue();

        // Save to settings
        AppSettings s = store.getSettings();
        if (font != null) s.setFontFamily(font);
        s.setFontSize(size);
        s.setAccentColor(toHex(cpAccent.getValue()));
        s.setTheme(cmbTheme.getValue());
        SettingsManager.save(s);

        // Apply to scene immediately
        if (cmbFontFamily.getScene() != null && cmbFontFamily.getScene().getRoot() != null) {
            cmbFontFamily.getScene().getRoot().setStyle(
                    "-fx-font-family: \"" + (font != null ? font : "Segoe UI") + "\"; " +
                    "-fx-font-size: " + size + "px;");
        }
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int)(c.getRed() * 255), (int)(c.getGreen() * 255), (int)(c.getBlue() * 255));
    }
}
