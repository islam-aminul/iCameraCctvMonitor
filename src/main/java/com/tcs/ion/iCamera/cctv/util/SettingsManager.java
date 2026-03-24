package com.tcs.ion.iCamera.cctv.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tcs.ion.iCamera.cctv.model.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * Loads and persists AppSettings from/to JSON on disk.
 * Location: <app-dir>/data/settings.json
 */
public class SettingsManager {

    private static final Logger log = LoggerFactory.getLogger(SettingsManager.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path SETTINGS_DIR  = AppDirs.getDataDir();
    private static final Path SETTINGS_FILE = SETTINGS_DIR.resolve("settings.json");

    public static AppSettings load() {
        if (!Files.exists(SETTINGS_FILE)) {
            log.info("No settings file found – using defaults");
            return loadFromProperties();
        }
        try {
            String json = new String(Files.readAllBytes(SETTINGS_FILE), StandardCharsets.UTF_8);
            AppSettings settings = GSON.fromJson(json, AppSettings.class);
            if (settings == null) settings = new AppSettings();
            log.info("Settings loaded from {}", SETTINGS_FILE);
            return settings;
        } catch (Exception e) {
            log.warn("Failed to read settings file: {} – using defaults", e.getMessage());
            return new AppSettings();
        }
    }

    public static void save(AppSettings settings) {
        try {
            Files.createDirectories(SETTINGS_DIR);
            String json = GSON.toJson(settings);
            Files.write(SETTINGS_FILE, json.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Settings saved to {}", SETTINGS_FILE);
        } catch (IOException e) {
            log.error("Failed to save settings: {}", e.getMessage());
        }
    }

    /**
     * Reads application.properties as a fallback / override for JMX settings.
     */
    private static AppSettings loadFromProperties() {
        AppSettings settings = new AppSettings();
        try (InputStream is = SettingsManager.class.getResourceAsStream("/application.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                settings.setJmxHost(props.getProperty("jmx.host", settings.getJmxHost()));
                settings.setJmxBasePort(Integer.parseInt(props.getProperty("jmx.port", String.valueOf(settings.getJmxBasePort()))));
                settings.setPollIntervalSeconds(Integer.parseInt(props.getProperty("poll.interval.seconds", String.valueOf(settings.getPollIntervalSeconds()))));
                settings.setFfprobePath(props.getProperty("ffprobe.path", settings.getFfprobePath()));
                settings.setJettyPort(Integer.parseInt(props.getProperty("jetty.port", String.valueOf(settings.getJettyPort()))));
            }
        } catch (Exception e) {
            log.warn("Could not read application.properties: {}", e.getMessage());
        }
        return settings;
    }
}
