package com.tcs.ion.iCamera.cctv;

import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Application entry point. Handles {@code --version}, {@code -version},
 * and {@code -v} before the Launcher class (and its static logback
 * initialiser) is ever loaded, then delegates to Launcher for normal startup.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            String arg = args[0];
            if ("--version".equals(arg) || "-version".equals(arg) || "-v".equals(arg)) {
                printVersion();
                System.exit(0);
            }
        }
        Launcher.main(args);
    }

    private static void printVersion() {
        String title = "iCamera CCTV Monitor";
        String version = "unknown";
        String vendor = "unknown";
        String buildDate = "unknown";

        try {
            String className = Main.class.getSimpleName() + ".class";
            String classPath = Main.class.getResource(className).toString();
            String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1)
                    + "/META-INF/MANIFEST.MF";
            Manifest manifest = new Manifest(new URL(manifestPath).openStream());
            Attributes attrs = manifest.getMainAttributes();
            if (attrs.getValue("Implementation-Title") != null)
                title = attrs.getValue("Implementation-Title");
            if (attrs.getValue("Implementation-Version") != null)
                version = attrs.getValue("Implementation-Version");
            if (attrs.getValue("Implementation-Vendor") != null)
                vendor = attrs.getValue("Implementation-Vendor");
            if (attrs.getValue("Build-Date") != null)
                buildDate = attrs.getValue("Build-Date");
        } catch (Exception ignored) {
        }

        System.out.println(title + " " + version);
        System.out.println("Build date: " + buildDate);
        System.out.println("Vendor:     " + vendor);
    }
}
