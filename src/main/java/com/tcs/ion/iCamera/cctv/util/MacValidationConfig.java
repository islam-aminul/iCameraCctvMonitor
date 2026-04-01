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
 * {@code <app-dir>/mac-validation.properties}.
 *
 * <p>If the file does not exist at startup, built-in defaults are used and a
 * template file is written to disk so operators can discover and customise the
 * configuration without repackaging the application.
 *
 * <p>Recognised properties:
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

    private static final String FILE_NAME   = "mac-validation.properties";
    private static final Path   CONFIG_FILE = AppDirs.getAppDir().resolve(FILE_NAME);

    private final String host;
    private final String servletPath;

    private MacValidationConfig(String host, String servletPath) {
        this.host        = host;
        this.servletPath = servletPath;
    }

    // ---- Factory -----------------------------------------------------------

    /**
     * Loads configuration from {@code <app-dir>/mac-validation.properties},
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

    private static void writeTemplate() {
        String nl = System.lineSeparator();
        String template =
                "# =================================================================" + nl +
                "# iCamera CCTV Monitor – MAC Validation Configuration" + nl +
                "# Edit this file to customise the remote MAC validation endpoint." + nl +
                "# =================================================================" + nl +
                "#" + nl +
                "# mac.validation.host" + nl +
                "#   Hostname used for:" + nl +
                "#     1. Detecting the local outbound network interface" + nl +
                "#        (a UDP probe is made to this host – no data is actually sent)" + nl +
                "#     2. Building the HTTPS API URL:" + nl +
                "#        https://<host><mac.validation.servlet.path>" + nl +
                "mac.validation.host=" + DEFAULT_HOST + nl +
                nl +
                "# mac.validation.servlet.path" + nl +
                "#   Servlet path appended to https://<mac.validation.host>." + nl +
                "mac.validation.servlet.path=" + DEFAULT_SERVLET_PATH + nl;

        try {
            Files.write(CONFIG_FILE,
                    template.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("MAC validation config template written to {} – edit to customise",
                    CONFIG_FILE);
        } catch (IOException e) {
            log.warn("Could not write MAC validation config template to {}: {}",
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
