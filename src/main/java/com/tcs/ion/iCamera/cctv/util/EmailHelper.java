package com.tcs.ion.iCamera.cctv.util;

import com.tcs.ion.iCamera.cctv.model.AlertData;
import com.tcs.ion.iCamera.cctv.model.ProxyData;
import com.tcs.ion.iCamera.cctv.model.SystemMetrics;
import com.tcs.ion.iCamera.cctv.service.DataStore;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Utility for composing email reports and handling mail client detection.
 * Supports desktop mail client (mailto:) or clipboard fallback for webmail users.
 */
public final class EmailHelper {

    private static final Logger log = LoggerFactory.getLogger(EmailHelper.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private EmailHelper() {}

    /**
     * Builds email subject line from current DataStore state.
     */
    public static String buildSubject(DataStore store) {
        ProxyData pd = store.getProxyData();
        String tcCode = pd != null && pd.getTcCode() != null ? pd.getTcCode() : "Unknown";
        String ragStatus = computeRagStatus(store);
        String timestamp = LocalDateTime.now().format(DTF);
        return "iCamera Report - TC " + tcCode + " - " + ragStatus + " - " + timestamp;
    }

    /**
     * Builds a plain-text email body with an inline status summary.
     */
    public static String buildBody(DataStore store) {
        ProxyData pd = store.getProxyData();
        SystemMetrics sm = store.getSystemMetrics();
        List<AlertData> unresolved = store.getUnresolvedAlerts();

        long critical = unresolved.stream()
                .filter(a -> a.getSeverity() == AlertData.Severity.CRITICAL).count();
        long warnings = unresolved.stream()
                .filter(a -> a.getSeverity() == AlertData.Severity.WARNING).count();

        StringBuilder sb = new StringBuilder();
        sb.append("iCamera CCTV Monitor - Status Report\n");
        sb.append("=====================================\n\n");

        sb.append("Generated: ").append(LocalDateTime.now().format(DTF)).append("\n");
        sb.append("Overall Status: ").append(computeRagStatus(store)).append("\n\n");

        // Proxy Information
        sb.append("--- Proxy Information ---\n");
        if (pd != null) {
            sb.append("TC Code: ").append(nvl(pd.getTcCode())).append("\n");
            sb.append("Proxy Name: ").append(nvl(pd.getProxyName())).append("\n");
            sb.append("Proxy Status: ").append(nvl(pd.getStatus())).append("\n");
            sb.append("HSQLDB Status: ").append(nvl(pd.getHsqldbStatus())).append("\n");
            sb.append("Uptime: ").append(pd.getUptimeString()).append("\n");
        } else {
            sb.append("Proxy data unavailable\n");
        }

        // Camera Summary
        sb.append("\n--- Camera Summary ---\n");
        sb.append("Total Cameras: ").append(store.getTotalCctvCount()).append("\n");
        sb.append("Active Cameras: ").append(store.getActiveCctvCount()).append("\n");
        sb.append("Inactive Cameras: ").append(store.getInactiveCctvCount()).append("\n");

        // System Resources
        if (sm != null) {
            sb.append("\n--- System Resources ---\n");
            sb.append("CPU Usage: ").append(String.format("%.1f%%", sm.getSystemCpuPercent())).append("\n");
            sb.append("Memory Usage: ").append(String.format("%.1f%%", sm.getMemoryUsedPercent())).append("\n");
            sb.append("Network Speed: ").append(String.format("%.2f MB/s", sm.getNetworkSpeedMbps())).append("\n");
        }

        // Alerts
        sb.append("\n--- Active Alerts ---\n");
        sb.append("Critical: ").append(critical).append("\n");
        sb.append("Warnings: ").append(warnings).append("\n");
        sb.append("Total Unresolved: ").append(unresolved.size()).append("\n");

        if (!unresolved.isEmpty()) {
            sb.append("\nAlert Details:\n");
            for (AlertData a : unresolved) {
                sb.append("  [").append(a.getSeverity().name()).append("] ")
                        .append(a.getMessage()).append("\n");
            }
        }

        sb.append("\n-----\n");
        sb.append("Please find the detailed report in the attached Excel file.\n");
        return sb.toString();
    }

    /**
     * Checks if a desktop mail client is available.
     */
    public static boolean isDesktopMailAvailable() {
        try {
            return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL);
        } catch (Exception e) {
            log.debug("Desktop mail check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Opens the default mail client with a pre-filled draft.
     * Note: mailto: URIs cannot attach files directly. The attachment must be added manually
     * or via platform-specific integration.
     */
    public static boolean openMailClient(String subject, String body) {
        try {
            if (!isDesktopMailAvailable()) return false;
            String encoded = "mailto:?subject=" + encodeMailComponent(subject)
                    + "&body=" + encodeMailComponent(body);
            Desktop.getDesktop().mail(new URI(encoded));
            return true;
        } catch (Exception e) {
            log.error("Failed to open mail client: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Copies email subject and body to the system clipboard.
     */
    public static void copyToClipboard(String subject, String body) {
        ClipboardContent content = new ClipboardContent();
        content.putString("Subject: " + subject + "\n\n" + body);
        Clipboard.getSystemClipboard().setContent(content);
    }

    /**
     * Shows a dialog with instructions for users without a desktop email client.
     */
    public static void showFallbackInstructions(String filePath, Stage owner) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Report Exported Successfully");
        dialog.setHeaderText("Report Saved & Email Content Copied");
        dialog.initOwner(owner);

        VBox content = new VBox(10);
        Label instructions = new Label(
                "The report has been saved successfully to:\n"
                        + filePath + "\n\n"
                        + "The email subject and body have been copied to your clipboard.\n\n"
                        + "To send the report:\n"
                        + "1. Open your webmail (e.g., Outlook, Gmail) in a browser.\n"
                        + "2. Create a new email and paste (Ctrl+V) the copied content.\n"
                        + "3. Attach the exported file from the location shown above.\n"
                        + "4. Send the email to your support team."
        );
        instructions.setWrapText(true);
        instructions.setMaxWidth(450);

        TextArea pathField = new TextArea(filePath);
        pathField.setEditable(false);
        pathField.setPrefRowCount(1);
        pathField.setMaxHeight(30);

        content.getChildren().addAll(instructions, pathField);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setMinWidth(500);
        dialog.showAndWait();
    }

    /**
     * Shows a confirmation dialog after successful export with the file path.
     */
    public static void showExportConfirmation(String filePath, Stage owner) {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("Export Successful");
        dialog.setHeaderText("Report exported successfully");
        dialog.setContentText("Saved to:\n" + filePath);
        dialog.initOwner(owner);
        dialog.showAndWait();
    }

    private static String computeRagStatus(DataStore store) {
        List<AlertData> unresolved = store.getUnresolvedAlerts();
        boolean hasCritical = unresolved.stream()
                .anyMatch(a -> a.getSeverity() == AlertData.Severity.CRITICAL);
        boolean hasWarning = unresolved.stream()
                .anyMatch(a -> a.getSeverity() == AlertData.Severity.WARNING);
        if (hasCritical) return "RED - Critical Issues";
        if (hasWarning) return "AMBER - Warnings Present";
        return "GREEN - All Systems Operational";
    }

    private static String encodeMailComponent(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    private static String nvl(String s) { return s != null ? s : "N/A"; }
}
