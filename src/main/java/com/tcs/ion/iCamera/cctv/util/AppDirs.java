package com.tcs.ion.iCamera.cctv.util;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves runtime directories relative to the application's own JAR/EXE location.
 *
 * Layout:
 *   <app-dir>/
 *     logs/   – rolling log files
 *     data/   – settings.json, exports, and future data files
 *
 * When running from a JAR file, <app-dir> is the directory that contains the JAR.
 * When running from a classes directory (IDE / Maven exec), <app-dir> falls back
 * to the JVM working directory (user.dir).
 */
public final class AppDirs {

    private static final Path APP_DIR = resolveAppDir();

    private AppDirs() {}

    private static Path resolveAppDir() {
        try {
            URI location = AppDirs.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI();
            Path p = Paths.get(location);
            // JAR file → its parent directory is the application directory
            if (p.toFile().isFile()) {
                return p.getParent();
            }
            // Classes directory (dev/IDE) → use working directory
            return Paths.get(System.getProperty("user.dir"));
        } catch (Exception e) {
            return Paths.get(System.getProperty("user.dir"));
        }
    }

    /** Root directory of the application (where the JAR/EXE lives). */
    public static Path getAppDir() { return APP_DIR; }

    /** {@code <app-dir>/data} – settings.json and future data files. */
    public static Path getDataDir() { return APP_DIR.resolve("data"); }

    /** {@code <app-dir>/exports} – Excel, CSV, and JSON exports. */
    public static Path getExportsDir() { return APP_DIR.resolve("exports"); }

    /** {@code <app-dir>/logs} – rolling log files. */
    public static Path getLogsDir() { return APP_DIR.resolve("logs"); }
}
