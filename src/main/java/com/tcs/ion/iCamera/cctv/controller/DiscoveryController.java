package com.tcs.ion.iCamera.cctv.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Discovery page – currently under development.
 * Placeholder for upcoming auto-discovery feature.
 */
public class DiscoveryController implements Initializable {

    @FXML private Label lblUnderDevelopment;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        lblUnderDevelopment.setText(
                "CCTV Auto-Discovery\n\n" +
                "This feature is currently under development.\n\n" +
                "Upcoming capabilities:\n" +
                "  • Automatic CCTV discovery across configured network subnets\n" +
                "  • ONVIF and RTSP probe support\n" +
                "  • Credential-based authentication (username/password)\n" +
                "  • Network range scanner with CCTV fingerprinting\n" +
                "  • Bulk import of discovered cameras\n\n" +
                "Please check back in a future release.");
    }
}
