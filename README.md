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
3. **Recognition** — A normalised greyscale histogram is computed for the face region using `Imgproc.calcHist()`. The mean histogram correlation (via `Imgproc.compareHist()` / `HISTCMP_CORREL`) across all stored training histograms is computed; a mean correlation ≥ 0.75 is treated as a match. This approach uses only the core OpenCV module (no `opencv_contrib` / `face` module required) and runs entirely on-device.  
4. **Persistence** — Training images are saved as PNG files in `%APPDATA%\FaceUnlocker\samples\` and re-loaded at startup to rebuild the histogram set.

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
cd face-unlocker
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
