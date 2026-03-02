package com.faceunlocker.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Data model for persisted application configuration.
 * Serialised to / deserialised from JSON by {@link ConfigManager}.
 */
public class AppConfig {

    /** Display name shown in window title and tray tooltip. */
    private String appName = "AI Face Unlocker";

    /** Executable names (lower-case) that are currently locked. */
    private List<String> lockedApps = new ArrayList<>();

    /**
     * SHA-256 hash of the fallback PIN.  Empty string means no PIN is set.
     * Using a hash avoids storing the PIN in plaintext.
     */
    private String pinHash = "";

    /**
     * Maximum time (seconds) the lock screen may remain open before forcing
     * PIN entry (0 = never force).
     */
    private int lockTimeoutSeconds = 30;

    /** Whether to launch the app automatically at Windows startup. */
    private boolean startWithWindows = false;

    /** Camera device index (0 = default webcam). */
    private int cameraIndex = 0;

    /** Access-log entries (ISO-8601 timestamp + app name). */
    private List<AccessLogEntry> accessLog = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Inner types
    // -----------------------------------------------------------------------

    /** A single entry in the access log. */
    public static class AccessLogEntry {
        private String timestamp;
        private String exeName;
        private String outcome; // "GRANTED" | "DENIED" | "PIN"

        public AccessLogEntry() {}

        public AccessLogEntry(String timestamp, String exeName, String outcome) {
            this.timestamp = timestamp;
            this.exeName   = exeName;
            this.outcome   = outcome;
        }

        public String getTimestamp() { return timestamp; }
        public String getExeName()   { return exeName;   }
        public String getOutcome()   { return outcome;   }

        public void setTimestamp(String ts) { this.timestamp = ts; }
        public void setExeName(String n)    { this.exeName = n;    }
        public void setOutcome(String o)    { this.outcome = o;    }

        @Override
        public String toString() {
            return timestamp + "  " + exeName + "  [" + outcome + "]";
        }
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------

    public String getAppName()          { return appName; }
    public void setAppName(String v)    { this.appName = v; }

    public List<String> getLockedApps()         { return lockedApps; }
    public void setLockedApps(List<String> v)   { this.lockedApps = v; }

    public String getPinHash()          { return pinHash; }
    public void setPinHash(String v)    { this.pinHash = v; }

    public int getLockTimeoutSeconds()       { return lockTimeoutSeconds; }
    public void setLockTimeoutSeconds(int v) { this.lockTimeoutSeconds = v; }

    public boolean isStartWithWindows()          { return startWithWindows; }
    public void setStartWithWindows(boolean v)   { this.startWithWindows = v; }

    public int getCameraIndex()       { return cameraIndex; }
    public void setCameraIndex(int v) { this.cameraIndex = v; }

    public List<AccessLogEntry> getAccessLog()          { return accessLog; }
    public void setAccessLog(List<AccessLogEntry> v)    { this.accessLog = v; }
}
