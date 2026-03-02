package com.faceunlocker;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_core;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Application entry point.
 * Loads the OpenCV native library via JavaCPP, then launches the JavaFX application.
 * Separating main() from the JavaFX Application class is required by
 * the JavaFX launcher when packaging as a fat JAR.
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // Load OpenCV native libraries via JavaCPP Loader
        try {
            Loader.load(opencv_core.class);
            log.info("OpenCV {} loaded successfully", opencv_core.CV_VERSION);
        } catch (Exception e) {
            log.error("Failed to load OpenCV native library", e);
            System.exit(1);
        }

        // Delegate to JavaFX launcher
        FaceUnlockerApp.launch(FaceUnlockerApp.class, args);
    }
}
