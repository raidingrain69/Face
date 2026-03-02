package com.faceunlocker;

import com.faceunlocker.config.ConfigManager;
import com.faceunlocker.service.AppLockerService;
import com.faceunlocker.service.SystemTrayService;
import com.faceunlocker.ui.MainDashboard;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Root JavaFX Application.
 * Wires together all services and shows the main dashboard.
 */
public class FaceUnlockerApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(FaceUnlockerApp.class);

    private AppLockerService lockerService;
    private SystemTrayService trayService;

    @Override
    public void start(Stage primaryStage) {
        // Do not exit when last window is closed – we live in the tray
        Platform.setImplicitExit(false);

        ConfigManager configManager = ConfigManager.getInstance();

        // Build and wire the main dashboard
        MainDashboard dashboard = new MainDashboard(configManager);

        // App-locker background service
        lockerService = new AppLockerService(configManager, primaryStage);
        lockerService.start();

        // System tray
        trayService = new SystemTrayService(primaryStage, lockerService);
        trayService.install();

        // Configure primary stage
        Scene scene = new Scene(dashboard.getRoot(), 900, 650);
        scene.getStylesheets().add(
                Objects.requireNonNull(
                        getClass().getResource("/styles/main.css")).toExternalForm());

        primaryStage.setTitle("AI Face Unlocker");
        primaryStage.setScene(scene);
        try {
            primaryStage.getIcons().add(
                    new Image(Objects.requireNonNull(
                            getClass().getResourceAsStream("/icons/app-icon.png"))));
        } catch (Exception ignored) {
            // Icon is optional
        }
        primaryStage.setOnCloseRequest(e -> {
            e.consume();               // Don't destroy – minimise to tray
            primaryStage.hide();
        });
        primaryStage.show();
        log.info("AI Face Unlocker started");
    }

    @Override
    public void stop() {
        if (lockerService != null) {
            lockerService.stop();
        }
        if (trayService != null) {
            trayService.uninstall();
        }
        log.info("AI Face Unlocker stopped");
    }
}
