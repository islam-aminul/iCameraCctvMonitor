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
 * Loads MAC validation endpoint configuration from
 * {@code <app-dir>/application.properties}.
 *
 * <p>The same {@code application.properties} file at the executable location
 * serves as the single external configuration file for all configurable
 * parameters.  If the file does not exist, built-in defaults are used and a
 * fully commented template is written to disk so operators can discover and
 * customise settings without repackaging the application.
 *
 * <p>Recognised properties (MAC validation section):
 * <ul>
 *   <li>{@code mac.validation.host} – hostname used both for outbound-interface
 *       detection and for building the HTTPS API URL
 *       (default: {@value #DEFAULT_HOST}).</li>
 *   <li>{@code mac.validation.servlet.path} – path component appended to
 *       {@code https://<host>} to form the full endpoint URL
 *       (default: {@value #DEFAULT_SERVLET_PATH}).</li>
 * </ul>
 */
public final class MacValidationConfig {

    private static final Logger log = LoggerFactory.getLogger(MacValidationConfig.class);

    public static final String DEFAULT_HOST         = "g01.tcsion.com";
    public static final String DEFAULT_SERVLET_PATH =
            "/iCAMERAStreamingFW/GetProxyMacValidationDataServlet";

    /** External config file – same name used by all configurable parameters. */
    private static final String FILE_NAME   = "application.properties";
    private static final Path   CONFIG_FILE = AppDirs.getAppDir().resolve(FILE_NAME);

    private final String host;
    private final String servletPath;

    private MacValidationConfig(String host, String servletPath) {
        this.host        = host;
        this.servletPath = servletPath;
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
                log.info("MAC validation config loaded from {}", CONFIG_FILE);
            } catch (IOException e) {
                log.warn("Failed to read {}: {} – using built-in defaults",
                        CONFIG_FILE, e.getMessage());
            }
        } else {
            writeTemplate();
        }

        String host = trim(props.getProperty("mac.validation.host", DEFAULT_HOST));
        String path = trim(props.getProperty("mac.validation.servlet.path", DEFAULT_SERVLET_PATH));

        if (host.isEmpty()) {
            log.warn("mac.validation.host is empty – falling back to {}", DEFAULT_HOST);
            host = DEFAULT_HOST;
        }
        if (path.isEmpty()) {
            log.warn("mac.validation.servlet.path is empty – falling back to {}",
                    DEFAULT_SERVLET_PATH);
            path = DEFAULT_SERVLET_PATH;
        }

        return new MacValidationConfig(host, path);
    }

    // ---- Template writer ---------------------------------------------------

    /**
     * Writes a fully commented {@code application.properties} template to
     * {@code <app-dir>} covering all known configurable parameters.
     */
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
                "# --- MAC Validation ---" + nl +
                "# Hostname used for:" + nl +
                "#   1. Detecting the local outbound network interface" + nl +
                "#      (a no-op UDP probe is made – no data is transmitted)" + nl +
                "#   2. Building the HTTPS API URL:" + nl +
                "#      https://<mac.validation.host><mac.validation.servlet.path>" + nl +
                "mac.validation.host=" + DEFAULT_HOST + nl +
                "#" + nl +
                "# Servlet path appended to https://<mac.validation.host>." + nl +
                "mac.validation.servlet.path=" + DEFAULT_SERVLET_PATH + nl;

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
     * Full HTTPS API URL, e.g.
     * {@code https://g01.tcsion.com/iCAMERAStreamingFW/GetProxyMacValidationDataServlet}.
     */
    public String getApiUrl() {
        return "https://" + host + servletPath;
    }

    /** Hostname component only (used for interface detection). */
    public String getHost() { return host; }

    public String getServletPath() { return servletPath; }

    // ---- Helpers -----------------------------------------------------------

    private static String trim(String s) {
        return (s == null) ? "" : s.trim();
    }
}
