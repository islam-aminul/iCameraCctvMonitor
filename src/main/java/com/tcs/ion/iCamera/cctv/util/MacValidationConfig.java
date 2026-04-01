package com.tcs.ion.iCamera.cctv.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

/**
 * Loads cloud data-centre configuration from {@code <app-dir>/application.properties}.
 *
 * <p>The {@code cloud.dc.host} property is the single hostname used as the base
 * for all remote REST API calls (MAC validation, cloud alerts, etc.).  The
 * servlet paths for individual APIs are hardcoded in their respective services.
 *
 * <p>If the file does not exist, built-in defaults are used and a fully commented
 * template is written to disk so operators can customise settings without
 * repackaging the application.
 */
public final class MacValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(MacValidationConfig.class);

    /** Default cloud DC hostname. */
    public static final String DEFAULT_CLOUD_DC_HOST = "g01.tcsion.com";

    /** Hardcoded servlet path for the MAC validation API. */
    public static final String MAC_VALIDATION_SERVLET_PATH =
            "/iCAMERAStreamingFW/GetProxyMacValidationDataServlet";

    private static final String FILE_NAME   = "application.properties";
    private static final Path   CONFIG_FILE = AppDirs.getAppDir().resolve(FILE_NAME);

    /** Default auto-validation interval (900s = 15 minutes). */
    public static final int DEFAULT_MAC_VALIDATION_INTERVAL = 900;

    private final String cloudDcHost;
    private final int    macValidationIntervalSeconds;

    private MacValidationConfig(String cloudDcHost, int macValidationIntervalSeconds) {
        this.cloudDcHost = cloudDcHost;
        this.macValidationIntervalSeconds = macValidationIntervalSeconds;
    }

    // ---- Factory -----------------------------------------------------------

    /**
     * Loads configuration from {@code <app-dir>/application.properties},
     * falling back to built-in defaults when the file is absent or unreadable.
     * A template file is written to disk on first use.
     */
    public static MacValidationConfig load() {
        Properties props = new Properties();

        if (Files.exists(CONFIG_FILE)) {
            try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
                props.load(in);
                log.info("Config loaded from {}", CONFIG_FILE);
            } catch (IOException e) {
                log.warn("Failed to read {}: {} – using built-in defaults",
                        CONFIG_FILE, e.getMessage());
            }
        } else {
            writeTemplate();
        }

        String host = trim(props.getProperty("cloud.dc.host", DEFAULT_CLOUD_DC_HOST));
        if (host.isEmpty()) {
            log.warn("cloud.dc.host is empty – falling back to {}", DEFAULT_CLOUD_DC_HOST);
            host = DEFAULT_CLOUD_DC_HOST;
        }

        int validationInterval = DEFAULT_MAC_VALIDATION_INTERVAL;
        String intervalStr = trim(props.getProperty("mac.validation.interval.seconds", ""));
        if (!intervalStr.isEmpty()) {
            try {
                validationInterval = Integer.parseInt(intervalStr);
                if (validationInterval < 0) validationInterval = 0;
            } catch (NumberFormatException e) {
                log.warn("Invalid mac.validation.interval.seconds '{}' – disabled", intervalStr);
            }
        }

        return new MacValidationConfig(host, validationInterval);
    }

    // ---- Template writer ---------------------------------------------------

    private static void writeTemplate() {
        String nl = System.lineSeparator();
        String template =
                "# ============================================================" + nl +
                "# iCamera CCTV Monitor – Application Properties" + nl +
                "# Place this file next to the application JAR / EXE to" + nl +
                "# override built-in defaults without repackaging." + nl +
                "# ============================================================" + nl +
                nl +
                "# --- JMX Connection ---" + nl +
                "jmx.host=localhost" + nl +
                "jmx.port=1099" + nl +
                "jmx.port.retries=5" + nl +
                nl +
                "# --- Polling ---" + nl +
                "poll.interval.seconds=30" + nl +
                nl +
                "# --- ffprobe ---" + nl +
                "ffprobe.path=.\\ffprobe.exe" + nl +
                nl +
                "# --- Embedded Jetty REST Server ---" + nl +
                "jetty.port=8080" + nl +
                nl +
                "# --- Export ---" + nl +
                "export.path=exports" + nl +
                "export.format=XLSX" + nl +
                nl +
                "# --- Theme ---" + nl +
                "theme=DARK" + nl +
                "accent.color=#2196F3" + nl +
                "font.family=Segoe UI" + nl +
                "font.size=13" + nl +
                nl +
                "# --- Cloud Data Centre ---" + nl +
                "# Base hostname for all remote REST API calls" + nl +
                "# (MAC validation, cloud alerts, etc.)." + nl +
                "cloud.dc.host=" + DEFAULT_CLOUD_DC_HOST + nl +
                nl +
                "# --- MAC Validation ---" + nl +
                "# Auto-validate MAC against cloud at this interval (seconds)." + nl +
                "# Set to 0 to disable auto-validation (manual only)." + nl +
                "mac.validation.interval.seconds=900" + nl;

        try {
            Files.write(CONFIG_FILE,
                    template.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("application.properties template written to {} – edit to customise",
                    CONFIG_FILE);
        } catch (IOException e) {
            log.warn("Could not write application.properties template to {}: {}",
                    CONFIG_FILE, e.getMessage());
        }
    }

    // ---- Accessors ---------------------------------------------------------

    /**
     * Full HTTPS MAC validation API URL, e.g.
     * {@code https://g01.tcsion.com/iCAMERAStreamingFW/GetProxyMacValidationDataServlet}.
     */
    public String getMacValidationApiUrl() {
        return "https://" + cloudDcHost + MAC_VALIDATION_SERVLET_PATH;
    }

    /** Cloud DC hostname (used for interface detection and as the API base). */
    public String getCloudDcHost() { return cloudDcHost; }

    /**
     * Auto-validation interval in seconds.  A value of {@code 0} means
     * auto-validation is disabled and validation is manual-only.
     */
    public int getMacValidationIntervalSeconds() { return macValidationIntervalSeconds; }

    // ---- Helpers -----------------------------------------------------------

    private static String trim(String s) {
        return (s == null) ? "" : s.trim();
    }
}
