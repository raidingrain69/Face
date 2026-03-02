package com.faceunlocker.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Singleton responsible for loading, saving, and providing access to
 * {@link AppConfig}.  Configuration is stored as pretty-printed JSON in
 * {@code %APPDATA%\FaceUnlocker\config.json} (or the user home on non-Windows).
 */
public class ConfigManager {

    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    private static final DateTimeFormatter LOG_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static ConfigManager instance;

    private final Path configFile;
    private final Path dataDir;
    private AppConfig config;
    private final Gson gson;

    private ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.dataDir   = resolveDataDir();
        this.configFile = dataDir.resolve("config.json");
        load();
    }

    /** Returns the application-wide singleton. */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    // -----------------------------------------------------------------------
    // Load / save
    // -----------------------------------------------------------------------

    private void load() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            log.error("Cannot create data directory {}", dataDir, e);
        }

        if (Files.exists(configFile)) {
            try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                config = gson.fromJson(r, AppConfig.class);
                log.info("Config loaded from {}", configFile);
                return;
            } catch (IOException e) {
                log.warn("Failed to read config – using defaults", e);
            }
        }
        config = new AppConfig();
        log.info("Using default config");
    }

    /** Persists the current config to disk. */
    public void save() {
        try (Writer w = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
            gson.toJson(config, w);
            log.debug("Config saved to {}", configFile);
        } catch (IOException e) {
            log.error("Failed to save config", e);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** @return the directory used for all application data files. */
    public Path getDataDir() {
        return dataDir;
    }

    /** @return the loaded {@link AppConfig}. Never {@code null}. */
    public AppConfig getConfig() {
        return config;
    }

    /**
     * Hashes {@code pin} with SHA-256 and stores the hex digest.
     * Use {@link #verifyPin(String)} to check a candidate PIN.
     *
     * @param pin raw PIN string.
     */
    public void setPin(String pin) {
        config.setPinHash(sha256(pin));
        save();
    }

    /**
     * Verifies a candidate PIN against the stored hash.
     *
     * @param pin candidate PIN.
     * @return {@code true} if the PIN matches.
     */
    public boolean verifyPin(String pin) {
        if (config.getPinHash() == null || config.getPinHash().isEmpty()) {
            return false;
        }
        return config.getPinHash().equals(sha256(pin));
    }

    /** @return {@code true} if a fallback PIN has been configured. */
    public boolean hasPinConfigured() {
        String h = config.getPinHash();
        return h != null && !h.isEmpty();
    }

    /**
     * Appends an entry to the in-memory access log and persists.
     *
     * @param exeName  executable that was locked.
     * @param outcome  "GRANTED", "DENIED", or "PIN".
     */
    public void logAccess(String exeName, String outcome) {
        String ts = LocalDateTime.now().format(LOG_FMT);
        config.getAccessLog().add(
                new AppConfig.AccessLogEntry(ts, exeName, outcome));
        // Keep only the last 500 entries to avoid unbounded growth
        var log2 = config.getAccessLog();
        if (log2.size() > 500) {
            config.setAccessLog(log2.subList(log2.size() - 500, log2.size()));
        }
        save();
    }

    // -----------------------------------------------------------------------
    // Private utilities
    // -----------------------------------------------------------------------

    private static Path resolveDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isEmpty()) {
            return Path.of(appData, "FaceUnlocker");
        }
        return Path.of(System.getProperty("user.home"), ".faceunlocker");
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
