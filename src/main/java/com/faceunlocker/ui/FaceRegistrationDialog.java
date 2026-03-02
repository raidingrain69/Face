package com.faceunlocker.ui;

import com.faceunlocker.config.ConfigManager;
import com.faceunlocker.engine.CameraCapture;
import com.faceunlocker.engine.FaceRecognitionEngine;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modal dialog that guides the user through face enrollment.
 *
 * <ol>
 *   <li>Opens the webcam and shows a live preview.</li>
 *   <li>User clicks "Capture" to take a snapshot.</li>
 *   <li>After {@value #REQUIRED_SAMPLES} captures the engine is trained.</li>
 *   <li>The model is saved to disk.</li>
 * </ol>
 */
public class FaceRegistrationDialog extends Dialog<Void> {

    private static final Logger log =
            LoggerFactory.getLogger(FaceRegistrationDialog.class);

    private static final int REQUIRED_SAMPLES = 20;

    private final ConfigManager         configManager;
    private final FaceRecognitionEngine engine;
    private final CameraCapture         camera;

    private ImageView  preview;
    private Label      progressLabel;
    private Button     captureBtn;
    private ProgressBar progressBar;

    private final AtomicInteger capturedCount = new AtomicInteger(0);
    private volatile Mat        lastFrame     = null;

    public FaceRegistrationDialog(Stage owner, ConfigManager configManager) {
        this.configManager = configManager;
        this.engine  = new FaceRecognitionEngine(configManager.getDataDir());
        this.camera  = new CameraCapture(configManager.getConfig().getCameraIndex());

        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Register Your Face");
        setResizable(false);

        buildContent();
        setupButtons();
        startCamera();
    }

    // -----------------------------------------------------------------------
    // Build
    // -----------------------------------------------------------------------

    private void buildContent() {
        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);
        content.setPrefWidth(400);

        Text instruction = new Text(
                "Look directly at the camera, then click Capture.\n" +
                "Repeat " + REQUIRED_SAMPLES + " times from slightly different angles.");
        instruction.setWrappingWidth(360);
        instruction.getStyleClass().add("hint-label");

        preview = new ImageView();
        preview.setFitWidth(320);
        preview.setFitHeight(240);
        preview.setPreserveRatio(true);
        preview.getStyleClass().add("camera-view");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(320);

        progressLabel = new Label("0 / " + REQUIRED_SAMPLES + " captured");
        progressLabel.getStyleClass().add("stats-label");

        captureBtn = new Button("📸  Capture");
        captureBtn.getStyleClass().add("btn-primary");
        captureBtn.setPrefWidth(160);
        captureBtn.setOnAction(e -> captureFrame());

        content.getChildren().addAll(instruction, preview, progressBar,
                                      progressLabel, captureBtn);
        getDialogPane().setContent(content);
    }

    private void setupButtons() {
        getDialogPane().getButtonTypes().addAll(ButtonType.FINISH, ButtonType.CANCEL);

        Button finishBtn = (Button) getDialogPane().lookupButton(ButtonType.FINISH);
        finishBtn.setText("Train Model");
        finishBtn.setDisable(true);
        finishBtn.getStyleClass().add("btn-primary");

        finishBtn.setOnAction(e -> {
            trainAndSave();
        });

        // Enable Finish only when enough samples collected
        captureBtn.setOnAction(ev -> {
            captureFrame();
            finishBtn.setDisable(capturedCount.get() < REQUIRED_SAMPLES);
        });

        setOnHiding(e -> stopCamera());
    }

    // -----------------------------------------------------------------------
    // Camera
    // -----------------------------------------------------------------------

    private void startCamera() {
        try {
            engine.initialize();
        } catch (IOException e) {
            log.error("Engine init failed", e);
        }

        camera.setImageCallback(img -> Platform.runLater(() -> preview.setImage(img)));
        try {
            camera.startCapture(frame -> {
                // Keep the latest frame available for capture
                if (lastFrame != null) {
                    lastFrame.release();
                }
                lastFrame = frame.clone();
            });
        } catch (Exception e) {
            log.warn("Camera unavailable: {}", e.getMessage());
            Platform.runLater(() ->
                captureBtn.setText("Camera unavailable"));
        }
    }

    private void stopCamera() {
        camera.stopCapture();
        if (lastFrame != null) {
            lastFrame.release();
            lastFrame = null;
        }
    }

    // -----------------------------------------------------------------------
    // Capture / train
    // -----------------------------------------------------------------------

    private void captureFrame() {
        Mat frame = lastFrame;
        if (frame == null) {
            return;
        }
        Mat face = engine.extractFirstFace(frame.clone());
        if (face == null) {
            Platform.runLater(() -> progressLabel.setText(
                    "No face detected – try again"));
            return;
        }
        engine.addTrainingSample(face);
        int count = capturedCount.incrementAndGet();
        Platform.runLater(() -> {
            progressLabel.setText(count + " / " + REQUIRED_SAMPLES + " captured");
            progressBar.setProgress((double) count / REQUIRED_SAMPLES);
        });
    }

    private void trainAndSave() {
        try {
            engine.trainModel();
            Platform.runLater(() ->
                new Alert(Alert.AlertType.INFORMATION,
                          "Face model trained successfully! (" +
                          engine.getTrainingSampleCount() + " samples)",
                          ButtonType.OK).showAndWait());
        } catch (Exception e) {
            log.error("Training failed", e);
            Platform.runLater(() ->
                new Alert(Alert.AlertType.ERROR,
                          "Training failed: " + e.getMessage(),
                          ButtonType.OK).showAndWait());
        } finally {
            stopCamera();
        }
    }
}
