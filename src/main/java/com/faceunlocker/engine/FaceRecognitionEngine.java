package com.faceunlocker.engine;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.opencv.opencv_objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_objdetect.*;

/**
 * Offline facial recognition engine built on OpenCV via JavaCV.
 *
 * <p>Detection uses the Haar Cascade frontal-face detector.
 * Recognition uses the Local Binary Pattern Histogram (LBPH) algorithm
 * ({@link LBPHFaceRecognizer}) from opencv_contrib – a lightweight,
 * accurate algorithm that runs entirely on-device with no cloud
 * dependency and compares actual facial texture patterns, not just
 * lighting information.</p>
 *
 * <h2>Typical lifecycle</h2>
 * <ol>
 *   <li>Call {@link #initialize()} once after the OpenCV native library is loaded.</li>
 *   <li>Call {@link #addTrainingSample(Mat)} one or more times during face registration.</li>
 *   <li>Call {@link #trainModel()} to train/re-train after adding samples.</li>
 *   <li>Call {@link #recognize(Mat)} on each camera frame to obtain a result.</li>
 * </ol>
 */
public class FaceRecognitionEngine {

    private static final Logger log = LoggerFactory.getLogger(FaceRecognitionEngine.class);

    /** Pixels – training images are resized to this before storing. */
    public static final int FACE_WIDTH  = 100;
    public static final int FACE_HEIGHT = 100;

    /**
     * LBPH confidence threshold. LBPH returns a distance where 0 = perfect
     * match and higher values = worse match. Scores below this value are
     * considered a successful recognition. Typical values: 50–100.
     */
    private static final double LBPH_CONFIDENCE_THRESHOLD = 80.0;

    /** Label assigned to the single registered user in the LBPH model. */
    private static final int REGISTERED_USER_LABEL = 0;

    private final Path dataDir;
    private final Path cascadeFile;
    private final Path modelFile;

    private CascadeClassifier detector;
    private LBPHFaceRecognizer recognizer;

    /** Raw greyscale training face images held in memory for potential retraining. */
    private final List<Mat> trainingSamples = new ArrayList<>();
    private boolean modelTrained = false;

    public FaceRecognitionEngine(Path dataDir) {
        this.dataDir     = dataDir;
        this.cascadeFile = dataDir.resolve("haarcascade_frontalface_default.xml");
        this.modelFile   = dataDir.resolve("face_model.yml");
    }

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    /**
     * Initialises the detector and the LBPH recogniser. Must be called after
     * the OpenCV native library has been loaded (JavaCV handles this via
     * JavaCPP's {@link Loader}).
     *
     * @throws IOException if the Haar cascade XML cannot be extracted/written.
     */
    public void initialize() throws IOException {
        Files.createDirectories(dataDir);
        extractCascade();
        detector   = new CascadeClassifier(cascadeFile.toString());
        recognizer = LBPHFaceRecognizer.create();
        loadModel();
        log.info("FaceRecognitionEngine initialised (model trained: {})", modelTrained);
    }

    private void extractCascade() throws IOException {
        if (Files.exists(cascadeFile)) {
            return;
        }
        // Strategy 1: classpath resource (bundled in project resources)
        try (InputStream in = getClass().getResourceAsStream(
                "/haarcascades/haarcascade_frontalface_default.xml")) {
            if (in != null) {
                Files.copy(in, cascadeFile);
                return;
            }
        }
        // Strategy 2: locate from JavaCV's extracted native bundle
        try {
            String nativeLib = Loader.load(org.bytedeco.opencv.global.opencv_objdetect.class);
            Path cascadeSrc = Paths.get(nativeLib).getParent()
                    .resolve("share/opencv4/haarcascades/haarcascade_frontalface_default.xml");
            if (Files.exists(cascadeSrc)) {
                Files.copy(cascadeSrc, cascadeFile);
                return;
            }
        } catch (Exception e) {
            log.debug("Native bundle cascade extraction failed: {}", e.getMessage());
        }
        throw new IOException("Haar cascade XML not found in classpath or native bundle");
    }

    // -----------------------------------------------------------------------
    // Face detection
    // -----------------------------------------------------------------------

    /**
     * Detects faces in a BGR frame and returns a list of bounding rectangles.
     *
     * @param frame BGR {@link Mat} from the webcam.
     * @return list of detected face regions (may be empty).
     */
    public List<Rect> detectFaces(Mat frame) {
        Mat grey = new Mat();
        cvtColor(frame, grey, COLOR_BGR2GRAY);
        equalizeHist(grey, grey);

        RectVector facesVec = new RectVector();
        detector.detectMultiScale(grey, facesVec, 1.1, 5, 0,
                new Size(80, 80), new Size());
        grey.release();

        List<Rect> faces = new ArrayList<>();
        for (long i = 0; i < facesVec.size(); i++) {
            faces.add(facesVec.get(i));
        }
        return faces;
    }

    /**
     * Extracts and pre-processes the first detected face from {@code frame}.
     *
     * @return resized greyscale face {@link Mat}, or {@code null} if no face found.
     */
    public Mat extractFirstFace(Mat frame) {
        List<Rect> faces = detectFaces(frame);
        if (faces.isEmpty()) {
            return null;
        }
        return preprocessFace(frame, faces.get(0));
    }

    private Mat preprocessFace(Mat frame, Rect rect) {
        Mat grey = new Mat();
        cvtColor(frame, grey, COLOR_BGR2GRAY);
        Mat face = new Mat(grey, rect);
        Mat resized = new Mat();
        resize(face, resized, new Size(FACE_WIDTH, FACE_HEIGHT));
        grey.release();
        face.release();
        return resized;
    }

    // -----------------------------------------------------------------------
    // Training
    // -----------------------------------------------------------------------

    /**
     * Adds a single training sample (a pre-processed face {@link Mat}).
     * Call {@link #trainModel()} after adding all desired samples.
     *
     * @param faceMat greyscale, resized face image.
     */
    public void addTrainingSample(Mat faceMat) {
        Mat copy = faceMat.clone();
        persistTrainingSample(copy, trainingSamples.size());
        trainingSamples.add(copy);
        log.debug("Training sample added (total: {})", trainingSamples.size());
    }

    /**
     * Trains the LBPH model on all collected samples and saves it to disk.
     *
     * @throws IllegalStateException if no training samples are available.
     */
    public void trainModel() {
        if (trainingSamples.isEmpty()) {
            throw new IllegalStateException("No training samples available");
        }
        MatVector images = new MatVector(trainingSamples.size());
        Mat labels = new Mat(trainingSamples.size(), 1, CV_32SC1);
        for (int i = 0; i < trainingSamples.size(); i++) {
            images.put(i, trainingSamples.get(i));
            // All samples belong to the single registered user (REGISTERED_USER_LABEL)
            labels.ptr(i, 0).putInt(REGISTERED_USER_LABEL);
        }
        recognizer.train(images, labels);
        recognizer.save(modelFile.toString());
        modelTrained = true;
        log.info("LBPH model trained and saved with {} samples", trainingSamples.size());
    }

    // -----------------------------------------------------------------------
    // Recognition
    // -----------------------------------------------------------------------

    /**
     * Result of a recognition attempt.
     *
     * @param recognised {@code true} if the face matches the registered user.
     * @param confidence LBPH distance score (lower = better match; 0 = perfect).
     */
    public record RecognitionResult(boolean recognised, double confidence) {}

    /**
     * Attempts to recognise the face in {@code frame} using the LBPH model.
     *
     * @param frame BGR webcam frame.
     * @return recognition result; {@code recognised=false} if model not trained
     *         or no face found.
     */
    public RecognitionResult recognize(Mat frame) {
        if (!modelTrained) {
            return new RecognitionResult(false, 0.0);
        }
        Mat face = extractFirstFace(frame);
        if (face == null) {
            return new RecognitionResult(false, 0.0);
        }
        try (IntPointer label      = new IntPointer(1);
             DoublePointer confidence = new DoublePointer(1)) {
            recognizer.predict(face, label, confidence);
            double score = confidence.get();
            boolean recognised = score < LBPH_CONFIDENCE_THRESHOLD;
            log.debug("LBPH recognition: score={} recognised={}", score, recognised);
            return new RecognitionResult(recognised, score);
        } finally {
            face.release();
        }
    }

    // -----------------------------------------------------------------------
    // Persistence helpers
    // -----------------------------------------------------------------------

    private void persistTrainingSample(Mat face, int index) {
        try {
            Path samplesDir = dataDir.resolve("samples");
            Files.createDirectories(samplesDir);
            imwrite(samplesDir.resolve("face_" + index + ".png").toString(), face);
        } catch (Exception e) {
            log.warn("Could not persist training sample", e);
        }
    }

    private void loadModel() {
        if (Files.exists(modelFile)) {
            try {
                recognizer.read(modelFile.toString());
                modelTrained = true;
                log.info("LBPH model loaded from {}", modelFile);
            } catch (Exception e) {
                log.warn("Could not load LBPH model, will retrain from raw samples", e);
            }
        }
        // Always reload raw samples so getTrainingSampleCount() is accurate
        loadRawSamples();
        if (!modelTrained && !trainingSamples.isEmpty()) {
            try {
                trainModel();
            } catch (Exception e) {
                log.warn("Could not rebuild LBPH model from raw samples", e);
            }
        }
    }

    private void loadRawSamples() {
        Path samplesDir = dataDir.resolve("samples");
        if (!Files.isDirectory(samplesDir)) {
            return;
        }
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(samplesDir, "face_*.png")) {
            List<Path> files = new ArrayList<>();
            stream.forEach(files::add);
            files.sort(Comparator.comparing(Path::toString));
            for (Path p : files) {
                Mat img = imread(p.toString(), IMREAD_GRAYSCALE);
                if (!img.empty()) {
                    trainingSamples.add(img);
                }
            }
            log.info("Loaded {} raw training samples from disk", trainingSamples.size());
        } catch (IOException e) {
            log.warn("Could not load training samples", e);
        }
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** @return {@code true} if the LBPH model has been trained at least once. */
    public boolean isModelTrained() {
        return modelTrained;
    }

    /** @return number of training samples currently held in memory. */
    public int getTrainingSampleCount() {
        return trainingSamples.size();
    }

    /** Clears all training data and deletes persisted samples and model. */
    public void clearTrainingData() {
        trainingSamples.forEach(Mat::release);
        trainingSamples.clear();
        modelTrained = false;
        recognizer = LBPHFaceRecognizer.create();
        try {
            Files.deleteIfExists(modelFile);
            Path samplesDir = dataDir.resolve("samples");
            if (Files.isDirectory(samplesDir)) {
                try (DirectoryStream<Path> ds =
                             Files.newDirectoryStream(samplesDir, "face_*.png")) {
                    for (Path p : ds) {
                        Files.deleteIfExists(p);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Error clearing training data", e);
        }
        log.info("Training data cleared");
    }
}
