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
                "Automatically discovers CCTV cameras across configured network subnets\n" +
                "using ONVIF and RTSP protocols, with support for credential-based\n" +
                "authentication and bulk import of discovered cameras.\n\n" +
                "This feature will be available in a future release.");
    }
}
