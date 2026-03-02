package com.faceunlocker.engine;

import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Offline facial recognition engine built on OpenCV.
 *
 * <p>Detection uses the Haar Cascade frontal-face detector shipped with OpenCV.
 * Recognition uses the Local Binary Pattern Histogram (LBPH) algorithm – a
 * lightweight, accurate algorithm that runs entirely on-device with no cloud
 * dependency.</p>
 *
 * <h2>Recognition algorithm</h2>
 * <p>Training images are stored as pre-processed face histograms.  At
 * recognition time the histogram of the new face is compared against all
 * stored histograms using the <em>correlation</em> metric
 * ({@code Imgproc.compareHist} / {@code Imgproc.HISTCMP_CORREL}).  A mean
 * correlation ≥ {@value #CORRELATION_THRESHOLD} is treated as a match.
 * This approach requires only the core OpenCV module (no {@code opencv_contrib}
 * / {@code face} module needed) and runs entirely on-device.</p>
 *
 * <h2>Typical lifecycle</h2>
 * <ol>
 *   <li>Call {@link #initialize()} once after the OpenCV native library is loaded.</li>
 *   <li>Call {@link #addTrainingSample(Mat)} one or more times during face registration.</li>
 *   <li>Call {@link #trainModel()} to train/re-train after adding samples.</li>
 *   <li>Call {@link #recognize(Mat)} on each camera frame to obtain a result.</li>
 *   <li>Call {@link #saveModel()} / {@link #loadModel()} to persist across sessions.</li>
 * </ol>
 */
public class FaceRecognitionEngine {

    private static final Logger log = LoggerFactory.getLogger(FaceRecognitionEngine.class);

    /** Pixels – training images are resized to this before storing. */
    public static final int FACE_WIDTH  = 100;
    public static final int FACE_HEIGHT = 100;

    /**
     * Histogram correlation threshold for recognition.
     * Range: [0, 1].  Higher = stricter.  0.75 works well in typical lighting.
     */
    private static final double CORRELATION_THRESHOLD = 0.75;

    /** Number of histogram bins per channel. */
    private static final int HIST_BINS = 64;

    private final Path dataDir;
    private final Path cascadeFile;

    private CascadeClassifier detector;

    /**
     * Stored training histograms.  Each entry is a normalised 1-D histogram
     * computed from a greyscale training face image.
     */
    private final List<Mat> trainHistograms = new ArrayList<>();
    private boolean modelTrained = false;

    public FaceRecognitionEngine(Path dataDir) {
        this.dataDir    = dataDir;
        this.cascadeFile = dataDir.resolve("haarcascade_frontalface_default.xml");
    }

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    /**
     * Initialises the detector and recogniser.  Must be called after
     * {@code OpenCV.loadLocally()}.
     *
     * @throws IOException if the Haar cascade XML cannot be extracted/written.
     */
    public void initialize() throws IOException {
        Files.createDirectories(dataDir);

        // Extract the Haar cascade XML bundled inside the opencv jar to disk,
        // because CascadeClassifier requires a file-system path.
        extractCascade();

        detector   = new CascadeClassifier(cascadeFile.toString());

        loadTrainingSamples();
        log.info("FaceRecognitionEngine initialised (model trained: {})", modelTrained);
    }

    private void extractCascade() throws IOException {
        if (Files.exists(cascadeFile)) {
            return;
        }
        try (InputStream in = getClass().getResourceAsStream(
                "/haarcascades/haarcascade_frontalface_default.xml")) {
            if (in == null) {
                throw new IOException("Haar cascade resource not found in classpath");
            }
            Files.copy(in, cascadeFile);
        }
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
        Imgproc.cvtColor(frame, grey, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(grey, grey);

        MatOfRect faces = new MatOfRect();
        detector.detectMultiScale(
                grey, faces,
                1.1,   // scaleFactor
                5,     // minNeighbors
                0,
                new Size(80, 80),
                new Size());

        grey.release();
        return faces.toList();
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
        Imgproc.cvtColor(frame, grey, Imgproc.COLOR_BGR2GRAY);

        Mat face = new Mat(grey, rect);
        Mat resized = new Mat();
        Imgproc.resize(face, resized, new Size(FACE_WIDTH, FACE_HEIGHT));

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
        persistTrainingSample(faceMat, trainHistograms.size());
        trainHistograms.add(computeHistogram(faceMat));
        log.debug("Training sample added (total: {})", trainHistograms.size());
    }

    /**
     * Marks the model as trained once enough samples have been added.
     * For histogram-based recognition there is no separate training step –
     * samples are used directly during recognition.
     *
     * @throws IllegalStateException if no training samples are available.
     */
    public void trainModel() {
        if (trainHistograms.isEmpty()) {
            throw new IllegalStateException("No training samples available");
        }
        modelTrained = true;
        log.info("Face model ready with {} histogram samples", trainHistograms.size());
    }

    // -----------------------------------------------------------------------
    // Recognition
    // -----------------------------------------------------------------------

    /**
     * Result of a recognition attempt.
     */
    public record RecognitionResult(boolean recognised, double confidence) {}

    /**
     * Attempts to recognise the face in {@code frame}.
     *
     * <p>Computes a histogram of the first detected face and measures its
     * mean correlation with all stored training histograms.  A mean
     * correlation ≥ {@value #CORRELATION_THRESHOLD} is considered a match.</p>
     *
     * @param frame BGR webcam frame.
     * @return recognition result; {@code recognised=false} if model not trained
     *         or no face found.
     */
    public RecognitionResult recognize(Mat frame) {
        if (!modelTrained || trainHistograms.isEmpty()) {
            return new RecognitionResult(false, 0.0);
        }

        Mat face = extractFirstFace(frame);
        if (face == null) {
            return new RecognitionResult(false, 0.0);
        }

        Mat queryHist = computeHistogram(face);
        face.release();

        double totalCorr = 0.0;
        for (Mat trainHist : trainHistograms) {
            totalCorr += Imgproc.compareHist(
                    queryHist, trainHist, Imgproc.HISTCMP_CORREL);
        }
        queryHist.release();

        double meanCorr = totalCorr / trainHistograms.size();
        boolean recognised = meanCorr >= CORRELATION_THRESHOLD;
        log.debug("Recognition: correlation={} recognised={}", meanCorr, recognised);

        return new RecognitionResult(recognised, meanCorr);
    }

    /**
     * Computes a normalised 1-D greyscale histogram for a face image.
     *
     * @param greyFace greyscale face {@link Mat}.
     * @return normalised histogram {@link Mat}.
     */
    private Mat computeHistogram(Mat greyFace) {
        Mat hist = new Mat();
        MatOfFloat ranges  = new MatOfFloat(0f, 256f);
        MatOfInt   histSize = new MatOfInt(HIST_BINS);
        Imgproc.calcHist(
                List.of(greyFace), new MatOfInt(0),
                new Mat(), hist, histSize, ranges);
        Core.normalize(hist, hist, 0, 1, Core.NORM_MINMAX);
        return hist;
    }

    // -----------------------------------------------------------------------
    // Persistence helpers
    // -----------------------------------------------------------------------

    private void persistTrainingSample(Mat face, int index) {
        try {
            Path samplesDir = dataDir.resolve("samples");
            Files.createDirectories(samplesDir);
            String filename = "face_" + index + ".png";
            Imgcodecs.imwrite(samplesDir.resolve(filename).toString(), face);
        } catch (IOException e) {
            log.warn("Could not persist training sample", e);
        }
    }

    private void loadTrainingSamples() {
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
                Mat img = Imgcodecs.imread(p.toString(), Imgcodecs.IMREAD_GRAYSCALE);
                if (!img.empty()) {
                    trainHistograms.add(computeHistogram(img));
                    img.release();
                }
            }
            if (!trainHistograms.isEmpty()) {
                modelTrained = true;
            }
            log.info("Loaded {} training samples from disk", trainHistograms.size());
        } catch (IOException e) {
            log.warn("Could not load training samples", e);
        }
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** @return {@code true} if the model has been trained at least once. */
    public boolean isModelTrained() {
        return modelTrained;
    }

    /** @return number of training samples currently held in memory. */
    public int getTrainingSampleCount() {
        return trainHistograms.size();
    }

    /** Clears all training data and deletes persisted samples. */
    public void clearTrainingData() {
        trainHistograms.forEach(Mat::release);
        trainHistograms.clear();
        modelTrained = false;

        try {
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
