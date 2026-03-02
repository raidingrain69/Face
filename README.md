# AI Face Unlocker

A local, offline AI-powered application locker for Windows.  
Restrict access to any `.exe` until your face is authenticated via webcam — no cloud, no subscription, 100% private.

---

## Features

| Feature | Detail |
|---------|--------|
| 🔒 App locking | Monitor any executable; overlay a lock screen the moment it gains focus |
| 📷 Offline face recognition | OpenCV LBPH algorithm — runs entirely on-device |
| 📌 Fallback PIN | SHA-256 hashed PIN when the camera isn't available |
| 🖥 Modern dashboard | JavaFX dark-theme UI with three tabs (Locked Apps · Face Registration · Access Log) |
| 🔔 System tray | Runs silently in the background; right-click tray icon for quick controls |
| 🚀 Auto-start | Optional Windows startup registry entry |
| 📦 Standalone installer | NSIS + Launch4j produce a single `.exe` installer |

---

## Recent Changes

### UI Overhaul
The dashboard and all dialogs have been redesigned with a new high-contrast Catppuccin-inspired dark theme:

- **Buttons** are now clearly distinguishable — primary actions use vibrant purple (`#7c3aed`), destructive actions use bright red (`#ef4444`), and secondary actions use neutral gray.
- **Progress bars** and **status labels** use distinct accent colours so feedback is immediately readable.
- Tab headers, form fields, and table rows all have improved contrast against the dark background.

### Face Registration Fix
The face-capture flow in the **Register Face** dialog has been hardened:

- **Thread-safety**: `lastFrame` (the most recent webcam frame held for snapshotting) is now accessed under a lock, eliminating a race condition where the background capture thread could release the Mat while the UI thread was cloning it — causing silent failures when the Capture button was pressed.
- **Engine error feedback**: If the face-recognition engine fails to initialise (e.g. Haar cascade XML not found), the dialog now shows the error in the progress label and disables the Capture button instead of silently proceeding and crashing on first capture.
- **Null-safe detector**: `FaceRecognitionEngine.detectFaces()` now returns an empty list immediately when the detector is `null` or not loaded, preventing a NullPointerException that would otherwise swallow capture attempts.

---

## Directory Structure

```
FaceUnlocker/
├── pom.xml                          # Maven build (Java 17, JavaFX 21, OpenCV 4.9)
├── launch4j-config.xml              # Wraps fat JAR → native Windows .exe
├── installer/
│   └── installer.nsi                # NSIS installer script
└── src/
    ├── main/
    │   ├── java/com/faceunlocker/
    │   │   ├── Main.java                        # Entry point; loads OpenCV
    │   │   ├── FaceUnlockerApp.java              # JavaFX Application root
    │   │   ├── engine/
    │   │   │   ├── FaceRecognitionEngine.java    # Haar + LBPH offline recognition
    │   │   │   └── CameraCapture.java            # Webcam capture (OpenCV VideoCapture)
    │   │   ├── monitor/
    │   │   │   └── ProcessMonitor.java           # Win32 foreground-window polling (JNA)
    │   │   ├── ui/
    │   │   │   ├── MainDashboard.java            # 3-tab JavaFX dashboard
    │   │   │   ├── LockScreenOverlay.java        # Full-screen lock overlay
    │   │   │   ├── FaceRegistrationDialog.java   # Face enrollment wizard
    │   │   │   └── PinFallbackDialog.java        # PIN entry dialog
    │   │   ├── config/
    │   │   │   ├── AppConfig.java                # POJO config data model
    │   │   │   └── ConfigManager.java            # JSON persistence + PIN hashing
    │   │   └── service/
    │   │       ├── AppLockerService.java         # Orchestrates monitor + recognition
    │   │       └── SystemTrayService.java        # AWT system-tray integration
    │   └── resources/
    │       ├── haarcascades/                     # Haar cascade XML (extracted at runtime)
    │       ├── styles/main.css                   # Dark-theme JavaFX stylesheet
    │       ├── icons/app-icon.png                # Application icon
    │       └── logback.xml                       # Logging configuration
    └── test/
        └── java/com/faceunlocker/
            └── FaceUnlockerTest.java             # Unit tests (no OpenCV / no GUI)
```

---

## Architecture

### Offline Facial Recognition (`engine/`)

1. **Detection** — `CascadeClassifier` with the Haar frontal-face cascade detects faces in each BGR frame.  
2. **Pre-processing** — Detected region is converted to greyscale and resized to 100 × 100 px.  
3. **Recognition** — The LBPH (`LBPHFaceRecognizer`) model is trained on the pre-processed face images. At recognition time the model predicts a label and a confidence (distance) score; a score below **80** is treated as a successful match. *(Note: earlier documentation incorrectly described a histogram-correlation approach — the implementation has always used LBPH.)*  
4. **Persistence** — Training images are saved as PNG files in `%APPDATA%\FaceUnlocker\samples\` and the trained model is saved as `face_model.yml`. Both are re-loaded at startup so registration survives restarts.

### Windows Process Monitor (`monitor/`)

- Polls `User32.GetForegroundWindow()` every 500 ms via JNA.  
- Resolves the HWND → PID → full image path using `GetWindowThreadProcessId` + `Psapi.GetModuleFileNameExW`.  
- Fires a `Consumer<String>` callback when a locked executable name is detected.

### App Locker Service (`service/`)

- Starts `ProcessMonitor` and `FaceRecognitionEngine` on app launch.  
- On trigger: shows `LockScreenOverlay` (full-screen, always-on-top) and starts the webcam.  
- On successful recognition: hides the overlay, logs "GRANTED".  
- PIN fallback if camera fails.

### GUI (`ui/`)

| Class | Purpose |
|-------|---------|
| `MainDashboard` | 3-tab BorderPane — Locked Apps, Face Registration, Access Log |
| `LockScreenOverlay` | Full-screen UNDECORATED+ALWAYS_ON_TOP Stage; live camera preview |
| `FaceRegistrationDialog` | Step-by-step enrollment (capture 20 samples → train LBPH) |
| `PinFallbackDialog` | Password field → SHA-256 verify |

---

## Prerequisites

| Tool | Version | Download |
|------|---------|----------|
| JDK | 17+ | [Adoptium](https://adoptium.net) |
| Maven | 3.9+ | [maven.apache.org](https://maven.apache.org) |
| Launch4j | 3.50+ | [launch4j.sf.net](https://launch4j.sourceforge.net) _(packaging only)_ |
| NSIS | 3.x | [nsis.sf.io](https://nsis.sourceforge.io) _(packaging only)_ |

---

## Build Instructions

### 1. Clone & compile

```bash
git clone https://github.com/raidingrain69/Face.git
cd Face
mvn clean package -DskipTests
```

The fat JAR is produced at:

```
target/face-unlocker-1.0.0-jar-with-dependencies.jar
```

### 2. Run (development)

```bash
mvn javafx:run
```

Or directly:

```bash
java -jar target/face-unlocker-1.0.0-jar-with-dependencies.jar
```

### 3. Run tests

```bash
mvn test
```

### 4. Create Windows .exe launcher (Launch4j)

```bash
# Windows
launch4j.exe launch4j-config.xml

# macOS / Linux (via command-line Launch4j)
java -jar /opt/launch4j/launch4j.jar launch4j-config.xml
```

Output: `target/FaceUnlocker.exe`

### 5. Bundle a JRE (optional but recommended for a truly standalone installer)

```bash
# jlink (Java 17+) – creates a minimal 60 MB JRE
jlink \
  --module-path "$JAVA_HOME/jmods" \
  --add-modules java.base,java.desktop,java.logging,java.sql,javafx.controls,javafx.fxml,javafx.swing \
  --output jre \
  --strip-debug \
  --compress 2 \
  --no-header-files \
  --no-man-pages
```

### 6. Build the installer (NSIS)

```bash
cd installer
makensis installer.nsi
```

Output: `installer/FaceUnlocker-Setup-1.0.0.exe`

---

## Face Registration – Step by Step

1. Open the dashboard and navigate to the **📷 Face Registration** tab.
2. Click **📷 Register Face** — a dialog opens with a live webcam preview.
3. Look directly at the camera and click **📸 Capture**. Repeat from slightly different angles.
4. After **20 captures** the **Train Model** button becomes active — click it to train and save the LBPH model.
5. The stats label on the tab updates to show the sample count and "Model trained: Yes ✓".

If the camera is unavailable (no webcam detected) or the recognition engine fails to initialise, the **Capture** button is automatically disabled and the reason is shown in the dialog.

---

## Configuration

All settings are persisted as JSON at:

```
%APPDATA%\FaceUnlocker\config.json
```

Key fields:

| Field | Default | Description |
|-------|---------|-------------|
| `lockedApps` | `[]` | List of locked executable names |
| `pinHash` | `""` | SHA-256 of the fallback PIN |
| `lockTimeoutSeconds` | `30` | Auto-dismiss lock screen after N seconds |
| `startWithWindows` | `false` | Add to `HKCU\...\Run` |
| `cameraIndex` | `0` | Webcam device index |

---

## Privacy & Security

- **No network calls** — zero internet access required or made.  
- **Face data stays local** — stored only in `%APPDATA%\FaceUnlocker\samples\`.  
- **PIN is hashed** — SHA-256 digest; the raw PIN is never written to disk.  
- **No plaintext secrets** in source code or config files.

---

## License

MIT — see `LICENSE`.
