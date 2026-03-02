package com.faceunlocker;

import org.opencv.core.Core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 * Loads the OpenCV native library, then launches the JavaFX application.
 * Separating main() from the JavaFX Application class is required by
 * the JavaFX launcher when packaging as a fat JAR.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Load OpenCV native library bundled by org.openpnp:opencv
        try {
            nu.pattern.OpenCV.loadLocally();
            log.info("OpenCV {} loaded successfully", Core.VERSION);
        } catch (Exception e) {
            log.error("Failed to load OpenCV native library", e);
            System.exit(1);
        }

        // Delegate to JavaFX launcher
        FaceUnlockerApp.launch(FaceUnlockerApp.class, args);
    }
}
