package com.faceunlocker.service;

import com.faceunlocker.config.AppConfig;
import com.faceunlocker.config.ConfigManager;
import com.faceunlocker.engine.CameraCapture;
import com.faceunlocker.engine.FaceRecognitionEngine;
import com.faceunlocker.monitor.ProcessMonitor;
import com.faceunlocker.ui.LockScreenOverlay;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates the core app-locking loop:
 *
 * <ol>
 *   <li>Starts the {@link ProcessMonitor} to watch for locked apps.</li>
 *   <li>When a locked app is detected, shows the {@link LockScreenOverlay}.</li>
 *   <li>Starts the webcam via {@link CameraCapture} and feeds frames to
 *       {@link FaceRecognitionEngine}.</li>
 *   <li>On successful recognition (or valid PIN) hides the overlay.</li>
 *   <li>Logs every access attempt via {@link ConfigManager}.</li>
 * </ol>
 */
public class AppLockerService {

    private static final Logger log = LoggerFactory.getLogger(AppLockerService.class);

    private final ConfigManager         configManager;
    private final Stage                 ownerStage;
    private final ProcessMonitor        processMonitor;
    private final FaceRecognitionEngine faceEngine;
    private final CameraCapture         camera;

    private LockScreenOverlay lockOverlay;
    private final AtomicBoolean locked  = new AtomicBoolean(false);
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    private String currentLockedApp = null;

    public AppLockerService(ConfigManager configManager, Stage ownerStage) {
        this.configManager  = configManager;
        this.ownerStage     = ownerStage;
        this.processMonitor = new ProcessMonitor();
        this.faceEngine     = new FaceRecognitionEngine(configManager.getDataDir());
        this.camera         = new CameraCapture(configManager.getConfig().getCameraIndex());
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /** Initialises the engine and starts process monitoring. */
    public void start() {
        try {
            faceEngine.initialize();
        } catch (IOException e) {
            log.error("FaceRecognitionEngine failed to initialise", e);
        }

        // Sync locked-apps set from config
        syncLockedApps();

        processMonitor.setLockTrigger(this::onLockedAppDetected);
        processMonitor.start();

        log.info("AppLockerService started (enabled={})", enabled.get());
    }

    /** Stops all background threads and releases camera. */
    public void stop() {
        processMonitor.stop();
        camera.stopCapture();
        log.info("AppLockerService stopped");
    }

    // -----------------------------------------------------------------------
    // Public controls
    // -----------------------------------------------------------------------

    /** Enables or disables locking without stopping the monitor. */
    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
        log.info("AppLockerService enabled={}", enabled);
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Adds an app to the locked list, persists to config.
     *
     * @param exeName executable filename, e.g. {@code "discord.exe"}.
     */
    public void addLockedApp(String exeName) {
        String lower = exeName.toLowerCase(Locale.ROOT);
        configManager.getConfig().getLockedApps().add(lower);
        configManager.save();
        processMonitor.lockApp(lower);
    }

    /**
     * Removes an app from the locked list, persists to config.
     */
    public void removeLockedApp(String exeName) {
        String lower = exeName.toLowerCase(Locale.ROOT);
        configManager.getConfig().getLockedApps().remove(lower);
        configManager.save();
        processMonitor.unlockApp(lower);
    }

    /** Re-reads the config and updates the monitor's locked set. */
    public void syncLockedApps() {
        Set<String> apps = new HashSet<>(configManager.getConfig().getLockedApps());
        processMonitor.setLockedApps(apps);
    }

    // -----------------------------------------------------------------------
    // Face registration helpers (used by UI)
    // -----------------------------------------------------------------------

    public FaceRecognitionEngine getFaceEngine() {
        return faceEngine;
    }

    public CameraCapture getCamera() {
        return camera;
    }

    // -----------------------------------------------------------------------
    // Lock / unlock flow
    // -----------------------------------------------------------------------

    private void onLockedAppDetected(String exeName) {
        if (!enabled.get() || locked.getAndSet(true)) {
            return; // already locked or disabled
        }
        currentLockedApp = exeName;
        log.info("Locking: {}", exeName);

        Platform.runLater(() -> showLockScreen(exeName));
    }

    private void showLockScreen(String exeName) {
        if (lockOverlay == null || !lockOverlay.isShowing()) {
            lockOverlay = new LockScreenOverlay(
                    ownerStage,
                    configManager,
                    this::onAuthGranted,
                    this::onAuthDenied);
        }
        lockOverlay.setAppName(exeName);
        lockOverlay.show();

        // Start camera and pipe frames to recognition
        try {
            camera.startCapture(frame -> {
                FaceRecognitionEngine.RecognitionResult result =
                        faceEngine.recognize(frame);
                frame.release();
                if (result.recognised()) {
                    Platform.runLater(this::onAuthGranted);
                }
            });
        } catch (Exception e) {
            log.warn("Camera unavailable – PIN fallback only: {}", e.getMessage());
        }
    }

    private void onAuthGranted() {
        if (!locked.compareAndSet(true, false)) {
            return;
        }
        log.info("Authentication GRANTED for {}", currentLockedApp);
        configManager.logAccess(currentLockedApp, "GRANTED");
        camera.stopCapture();
        if (lockOverlay != null) {
            lockOverlay.hide();
        }
        currentLockedApp = null;
    }

    private void onAuthDenied() {
        log.info("Authentication DENIED for {}", currentLockedApp);
        configManager.logAccess(currentLockedApp, "DENIED");
    }
}
