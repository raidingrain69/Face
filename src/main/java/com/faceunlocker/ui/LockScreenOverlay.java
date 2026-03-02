package com.faceunlocker.ui;

import com.faceunlocker.config.ConfigManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.*;

/**
 * Full-screen overlay that appears whenever a locked application becomes the
 * foreground window.
 *
 * <p>Layout (top-to-bottom):</p>
 * <ul>
 *   <li>Dark translucent background covering the whole screen.</li>
 *   <li>Lock icon + app name.</li>
 *   <li>Live camera preview with a face-detection highlight.</li>
 *   <li>Status message (scanning / found / denied).</li>
 *   <li>Fallback "Use PIN" button.</li>
 * </ul>
 */
public class LockScreenOverlay {

    private final Stage             stage;
    private final ConfigManager     configManager;
    private final Runnable          onGranted;
    private final Runnable          onDenied;

    private Label      appNameLabel;
    private Label      statusLabel;
    private ImageView  cameraView;
    private ProgressIndicator spinner;

    public LockScreenOverlay(Stage owner,
                             ConfigManager configManager,
                             Runnable onGranted,
                             Runnable onDenied) {
        this.configManager = configManager;
        this.onGranted     = onGranted;
        this.onDenied      = onDenied;
        this.stage = buildStage(owner);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** Updates the application name shown on the overlay. */
    public void setAppName(String exeName) {
        Platform.runLater(() -> appNameLabel.setText(exeName));
    }

    /** Updates the status message (e.g. "Scanning…", "Face recognised ✓"). */
    public void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    /** Feeds a new camera frame into the preview {@link ImageView}. */
    public void updateCameraFrame(Image image) {
        Platform.runLater(() -> cameraView.setImage(image));
    }

    public void show()  { Platform.runLater(stage::show); }
    public void hide()  { Platform.runLater(stage::hide); }
    public boolean isShowing() { return stage.isShowing(); }

    // -----------------------------------------------------------------------
    // Build
    // -----------------------------------------------------------------------

    private Stage buildStage(Stage owner) {
        Stage s = new Stage();
        s.initOwner(owner);
        s.initStyle(StageStyle.UNDECORATED);
        s.initModality(Modality.APPLICATION_MODAL);
        s.setAlwaysOnTop(true);

        // Cover the entire primary screen
        Rectangle2D bounds = Screen.getPrimary().getBounds();
        s.setX(bounds.getMinX());
        s.setY(bounds.getMinY());
        s.setWidth(bounds.getWidth());
        s.setHeight(bounds.getHeight());

        StackPane root = new StackPane(buildContent());
        root.getStyleClass().add("lock-root");

        Scene scene = new Scene(root);
        scene.getStylesheets().add(
                getClass().getResource("/styles/main.css").toExternalForm());

        s.setScene(scene);
        return s;
    }

    private VBox buildContent() {
        VBox content = new VBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        content.setMaxWidth(500);

        // Lock icon + title
        Text lockIcon = new Text("🔒");
        lockIcon.getStyleClass().add("lock-icon");

        Text title = new Text("Access Restricted");
        title.getStyleClass().add("lock-title");

        appNameLabel = new Label("application");
        appNameLabel.getStyleClass().add("lock-app-name");

        // Camera preview
        cameraView = new ImageView();
        cameraView.setFitWidth(320);
        cameraView.setFitHeight(240);
        cameraView.setPreserveRatio(true);
        cameraView.getStyleClass().add("camera-view");

        // Spinner + status
        spinner = new ProgressIndicator();
        spinner.setPrefSize(36, 36);
        spinner.getStyleClass().add("lock-spinner");

        statusLabel = new Label("Scanning for your face…");
        statusLabel.getStyleClass().add("lock-status");

        HBox statusRow = new HBox(10, spinner, statusLabel);
        statusRow.setAlignment(Pos.CENTER);

        // PIN fallback button
        Button pinBtn = new Button("Use PIN Instead");
        pinBtn.getStyleClass().add("btn-secondary");
        pinBtn.setVisible(configManager.hasPinConfigured());
        pinBtn.setOnAction(e -> showPinDialog());

        content.getChildren().addAll(
                lockIcon, title, appNameLabel, cameraView, statusRow, pinBtn);
        return content;
    }

    // -----------------------------------------------------------------------
    // PIN fallback
    // -----------------------------------------------------------------------

    private void showPinDialog() {
        PinFallbackDialog dialog = new PinFallbackDialog(stage, configManager);
        dialog.showAndWait().ifPresent(granted -> {
            if (granted) {
                setStatus("PIN accepted ✓");
                configManager.logAccess(appNameLabel.getText(), "PIN");
                if (onGranted != null) {
                    onGranted.run();
                }
            } else {
                setStatus("Wrong PIN – try again");
                if (onDenied != null) {
                    onDenied.run();
                }
            }
        });
    }
}
