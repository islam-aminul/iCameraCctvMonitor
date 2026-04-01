package com.tcs.ion.iCamera.cctv;

import com.tcs.ion.iCamera.cctv.model.AppSettings;
import com.tcs.ion.iCamera.cctv.service.*;
import com.tcs.ion.iCamera.cctv.util.SettingsManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * JavaFX Application for iCamera CCTV Monitor.
 *
 * Responsibilities:
 *  1. Load settings from disk / application.properties
 *  2. Start embedded Jetty REST server
 *  3. Launch JavaFX UI
 *  4. Start background polling scheduler
 *  5. On unexpected close – reopen window within 5 seconds
 */
public class Launcher extends Application {

    private static final Logger log = LoggerFactory.getLogger(Launcher.class);

    private JettyServer       jettyServer;
    private SchedulerService  scheduler;
    private JmxService        jmxService;
    private OshiService       oshiService;
    private FfprobeService    ffprobeService;
    private AlertService      alertService;
    private WindowsServiceReader wsReader;
    private HttpService       httpService;

    private Stage primaryStage;
    private boolean shutdownRequested = false;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;

        // ---- Load settings ----
        AppSettings settings = SettingsManager.load();
        DataStore.getInstance().setSettings(settings);

        // ---- Start services ----
        startServices(settings);

        // ---- Build JavaFX window ----
        showMainWindow(stage);

        log.info("iCamera CCTV Monitor started");
    }

    private void showMainWindow(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
        Scene scene = new Scene(loader.load(), 1440, 800);

        // Load theme based on saved setting
        AppSettings settings = DataStore.getInstance().getSettings();
        String cssFile = "LIGHT".equalsIgnoreCase(settings.getTheme()) ? "light-theme.css" : "dark-theme.css";
        java.net.URL cssUrl = getClass().getResource("/css/" + cssFile);
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
        }

        stage.setTitle("iCamera CCTV Monitor");
        stage.setScene(scene);
        stage.setMinWidth(1200);
        stage.setMinHeight(700);

        // Set application icon
        try (InputStream iconStream = getClass().getResourceAsStream("/icon.ico")) {
            if (iconStream != null) stage.getIcons().add(new Image(iconStream));
        } catch (Exception e) { log.warn("Could not load icon: {}", e.getMessage()); }

        // Intercept window close – hide instead of exit (prevent accidental close)
        stage.setOnCloseRequest((WindowEvent event) -> {
            if (!shutdownRequested) {
                event.consume();    // Block the close
                stage.setIconified(true); // Minimise instead
                log.info("Close intercepted – window minimised");
            }
        });

        // Auto-reopen if iconified unexpectedly
        stage.iconifiedProperty().addListener((obs, wasMin, isMin) -> {
            // Allow intentional minimise but detect if window becomes not showing
        });

        stage.show();

        // Watchdog – reopen window if it somehow closes
        startWindowWatchdog(stage);
    }

    /**
     * Checks every second if the primary stage is still showing.
     * If it unexpectedly hides (not minimised, not shutdown), restores within 5 seconds.
     */
    private void startWindowWatchdog(Stage stage) {
        Timer watchdog = new Timer("window-watchdog", true);
        watchdog.scheduleAtFixedRate(new TimerTask() {
            int closedTicks = 0;
            @Override public void run() {
                if (shutdownRequested) { cancel(); return; }
                Platform.runLater(() -> {
                    if (stage != null && !stage.isShowing() && !stage.isIconified()) {
                        closedTicks++;
                        if (closedTicks >= 5) {
                            log.warn("Window lost – restoring...");
                            try {
                                showMainWindow(new Stage());
                            } catch (Exception e) {
                                log.error("Failed to reopen window", e);
                            }
                            closedTicks = 0;
                        }
                    } else {
                        closedTicks = 0;
                    }
                });
            }
        }, 1000, 1000);
    }

    private void startServices(AppSettings settings) {
        // Services
        jmxService      = new JmxService();
        oshiService     = new OshiService();
        ffprobeService  = new FfprobeService();
        alertService    = new AlertService();
        wsReader        = new WindowsServiceReader();
        httpService     = new HttpService();

        // Jetty
        jettyServer = new JettyServer();
        try {
            jettyServer.start();
        } catch (Exception e) {
            log.error("Jetty failed to start: {}", e.getMessage());
        }

        // Scheduler
        scheduler = new SchedulerService(jmxService, oshiService, ffprobeService, alertService, wsReader);
        scheduler.start();
    }

    @Override
    public void stop() throws Exception {
        shutdownRequested = true;
        log.info("Shutting down...");
        if (scheduler != null) scheduler.stop();
        if (jmxService != null) jmxService.disconnect();
        if (ffprobeService != null) ffprobeService.shutdown();
        if (httpService != null) httpService.close();
        if (jettyServer != null) try { jettyServer.stop(); } catch (Exception e) { /* ignore */ }
        SettingsManager.save(DataStore.getInstance().getSettings());
        super.stop();
    }

    /**
     * Called by Main after version check passes.
     * Launch4j/EXE → Main.main() → Launcher.main().
     */
    public static void main(String[] args) {
        // Ensure JavaFX classes are loadable on the module path
        System.setProperty("javafx.preloader", "");
        launch(args);
    }
}
