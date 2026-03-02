package com.faceunlocker.engine;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manages the webcam lifecycle and delivers frames on a background thread.
 *
 * <p>Frames are read at {@value #TARGET_FPS} fps.  Callers subscribe with
 * {@link #startCapture(Consumer)} and stop with {@link #stopCapture()}.
 * A JavaFX {@link Image} version of each frame is also published via an
 * optional {@link #setImageCallback(Consumer)} for live preview rendering.</p>
 */
public class CameraCapture {

    private static final Logger log = LoggerFactory.getLogger(CameraCapture.class);

    private static final int TARGET_FPS  = 15;
    private static final int FRAME_DELAY = 1000 / TARGET_FPS; // ms

    private final int cameraIndex;
    private VideoCapture capture;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> captureTask;

    private Consumer<Mat>   frameCallback;
    private Consumer<Image> imageCallback;

    public CameraCapture(int cameraIndex) {
        this.cameraIndex = cameraIndex;
    }

    /** Convenience constructor that uses the default (index 0) camera. */
    public CameraCapture() {
        this(0);
    }

    // -----------------------------------------------------------------------
    // Camera control
    // -----------------------------------------------------------------------

    /**
     * Opens the webcam and begins delivering BGR {@link Mat} frames to
     * {@code frameCallback} at {@value #TARGET_FPS} fps.
     *
     * @param frameCallback consumer that receives each BGR frame.
     * @throws IllegalStateException if the camera cannot be opened.
     */
    public synchronized void startCapture(Consumer<Mat> frameCallback) {
        if (captureTask != null && !captureTask.isDone()) {
            log.warn("Capture already running");
            return;
        }

        this.frameCallback = frameCallback;

        capture = new VideoCapture(cameraIndex);
        capture.set(Videoio.CAP_PROP_FRAME_WIDTH,  640);
        capture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480);

        if (!capture.isOpened()) {
            throw new IllegalStateException(
                    "Cannot open camera at index " + cameraIndex);
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "camera-capture");
            t.setDaemon(true);
            return t;
        });

        captureTask = scheduler.scheduleAtFixedRate(
                this::readFrame, 0, FRAME_DELAY, TimeUnit.MILLISECONDS);

        log.info("Camera {} capture started", cameraIndex);
    }

    /**
     * Stops frame capture and releases the webcam.
     */
    public synchronized void stopCapture() {
        if (captureTask != null) {
            captureTask.cancel(false);
            captureTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (capture != null) {
            capture.release();
            capture = null;
        }
        log.info("Camera {} capture stopped", cameraIndex);
    }

    /** @return {@code true} if the camera is currently capturing. */
    public boolean isCapturing() {
        return captureTask != null && !captureTask.isDone();
    }

    // -----------------------------------------------------------------------
    // Callbacks
    // -----------------------------------------------------------------------

    /**
     * Optionally set a callback that receives a converted JavaFX {@link Image}
     * suitable for display in an {@code ImageView}.  Called on the JavaFX
     * application thread.
     */
     public void setImageCallback(Consumer<Image> imageCallback) {
        this.imageCallback = imageCallback;
    }

    // -----------------------------------------------------------------------
    // Internal
    // -----------------------------------------------------------------------

    private void readFrame() {
        if (capture == null || !capture.isOpened()) {
            return;
        }
        Mat frame = new Mat();
        if (capture.read(frame) && !frame.empty()) {
            if (frameCallback != null) {
                frameCallback.accept(frame.clone());
            }
            if (imageCallback != null) {
                Image fx = matToFxImage(frame);
                Platform.runLater(() -> imageCallback.accept(fx));
            }
        }
        frame.release();
    }

    /**
     * Converts an OpenCV BGR {@link Mat} to a JavaFX {@link Image}.
     */
    public static Image matToFxImage(Mat bgr) {
        int width  = bgr.cols();
        int height = bgr.rows();
        int channels = bgr.channels();

        byte[] pixels = new byte[width * height * channels];
        bgr.get(0, 0, pixels);

        BufferedImage buf = new BufferedImage(width, height,
                channels == 3 ? BufferedImage.TYPE_3BYTE_BGR
                              : BufferedImage.TYPE_BYTE_GRAY);
        buf.getRaster().setDataElements(0, 0, width, height, pixels);

        return SwingFXUtils.toFXImage(buf, null);
    }
}
