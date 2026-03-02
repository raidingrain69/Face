package com.faceunlocker.service;

import com.faceunlocker.service.AppLockerService;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.ActionListener;
import java.net.URL;

/**
 * Installs an AWT {@link SystemTray} icon so the application remains
 * accessible without a visible window.
 *
 * <p>Menu items:</p>
 * <ul>
 *   <li><b>Open Dashboard</b> – shows the main JavaFX window.</li>
 *   <li><b>Enable / Disable Locking</b> – toggles the locker service.</li>
 *   <li><b>Exit</b> – quits the application cleanly.</li>
 * </ul>
 */
public class SystemTrayService {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayService.class);

    private final Stage            primaryStage;
    private final AppLockerService lockerService;

    private TrayIcon trayIcon;

    public SystemTrayService(Stage primaryStage, AppLockerService lockerService) {
        this.primaryStage  = primaryStage;
        this.lockerService = lockerService;
    }

    // -----------------------------------------------------------------------
    // Install / uninstall
    // -----------------------------------------------------------------------

    /**
     * Adds the tray icon.  Safe to call on any thread; delegates to the AWT
     * event thread internally.
     */
    public void install() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray not supported on this platform");
            return;
        }

        EventQueue.invokeLater(() -> {
            try {
                Image icon = loadIcon();
                PopupMenu menu = buildMenu();
                trayIcon = new TrayIcon(icon, "AI Face Unlocker", menu);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> showDashboard());

                SystemTray.getSystemTray().add(trayIcon);
                log.info("System tray icon installed");
            } catch (AWTException e) {
                log.error("Could not install tray icon", e);
            }
        });
    }

    /**
     * Removes the tray icon.
     */
    public void uninstall() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
            trayIcon = null;
        }
    }

    // -----------------------------------------------------------------------
    // Menu construction
    // -----------------------------------------------------------------------

    private PopupMenu buildMenu() {
        PopupMenu menu = new PopupMenu();

        MenuItem openItem = new MenuItem("Open Dashboard");
        openItem.addActionListener(e -> showDashboard());
        menu.add(openItem);

        menu.addSeparator();

        MenuItem toggleItem = new MenuItem(
                lockerService.isEnabled() ? "Disable Locking" : "Enable Locking");
        toggleItem.addActionListener(e -> {
            boolean newState = !lockerService.isEnabled();
            lockerService.setEnabled(newState);
            toggleItem.setLabel(newState ? "Disable Locking" : "Enable Locking");
            updateTooltip(newState);
        });
        menu.add(toggleItem);

        menu.addSeparator();

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            uninstall();
            Platform.runLater(() -> {
                primaryStage.close();
                Platform.exit();
            });
        });
        menu.add(exitItem);

        return menu;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void showDashboard() {
        Platform.runLater(() -> {
            primaryStage.show();
            primaryStage.toFront();
        });
    }

    private void updateTooltip(boolean enabled) {
        if (trayIcon != null) {
            trayIcon.setToolTip("AI Face Unlocker – " + (enabled ? "Active" : "Paused"));
        }
    }

    private Image loadIcon() {
        URL url = getClass().getResource("/icons/app-icon.png");
        if (url != null) {
            return Toolkit.getDefaultToolkit().getImage(url);
        }
        // Fallback: 16×16 blue square
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(16, 16,
                                                 java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x2196F3));
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        return img;
    }
}
