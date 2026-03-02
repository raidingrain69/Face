package com.faceunlocker.monitor;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Monitors the Windows foreground window every {@value #POLL_INTERVAL_MS} ms.
 * When the active window belongs to a locked executable the registered
 * {@link Consumer} is invoked with the executable name (lower-case).
 *
 * <p>Uses JNA to call:
 * <ul>
 *   <li>{@code User32.GetForegroundWindow()} – active HWND</li>
 *   <li>{@code User32.GetWindowThreadProcessId()} – PID from HWND</li>
 *   <li>{@code Kernel32.OpenProcess()} – process HANDLE from PID</li>
 *   <li>{@code Psapi.GetModuleFileNameExW()} – full image path</li>
 * </ul>
 * </p>
 *
 * <p><strong>Windows-only.</strong>  On any other OS the monitor silently
 * does nothing so that unit tests can still run on CI.</p>
 */
public class ProcessMonitor {

    private static final Logger log = LoggerFactory.getLogger(ProcessMonitor.class);
    private static final long POLL_INTERVAL_MS = 500;

    private final Set<String>        lockedApps  = Collections.synchronizedSet(new HashSet<>());
    private Consumer<String>         lockTrigger;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>       pollTask;
    private String                   lastLockedApp = null;

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    // -----------------------------------------------------------------------
    // JNA interface declarations
    // -----------------------------------------------------------------------

    interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        WinDef.HWND GetForegroundWindow();
        int GetWindowThreadProcessId(WinDef.HWND hWnd, int[] lpdwProcessId);
    }

    interface Psapi extends StdCallLibrary {
        Psapi INSTANCE = Native.load("psapi", Psapi.class, W32APIOptions.DEFAULT_OPTIONS);

        /** Returns the length of the string copied to {@code lpFilename}, or 0 on error. */
        int GetModuleFileNameExW(HANDLE hProcess, Pointer hModule,
                                 char[] lpFilename, int nSize);
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Sets the callback invoked when a locked application becomes foreground.
     * Called on the monitor background thread; update UI with
     * {@code Platform.runLater()}.
     */
    public void setLockTrigger(Consumer<String> lockTrigger) {
        this.lockTrigger = lockTrigger;
    }

    /**
     * Adds an executable name to the locked set (case-insensitive).
     *
     * @param exeName e.g. {@code "discord.exe"}
     */
    public void lockApp(String exeName) {
        lockedApps.add(exeName.toLowerCase(Locale.ROOT));
    }

    /**
     * Removes an executable from the locked set.
     *
     * @param exeName executable name (case-insensitive).
     */
    public void unlockApp(String exeName) {
        lockedApps.remove(exeName.toLowerCase(Locale.ROOT));
    }

    /** Replaces the entire locked set. */
    public void setLockedApps(Set<String> apps) {
        lockedApps.clear();
        apps.forEach(a -> lockedApps.add(a.toLowerCase(Locale.ROOT)));
    }

    /** @return an unmodifiable snapshot of the current locked apps set. */
    public Set<String> getLockedApps() {
        return Set.copyOf(lockedApps);
    }

    /**
     * Starts the background polling thread.
     */
    public void start() {
        if (!IS_WINDOWS) {
            log.warn("ProcessMonitor: not on Windows – monitoring disabled");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "process-monitor");
            t.setDaemon(true);
            return t;
        });
        pollTask = scheduler.scheduleAtFixedRate(
                this::poll, 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log.info("ProcessMonitor started (polling every {}ms)", POLL_INTERVAL_MS);
    }

    /**
     * Stops the polling thread.
     */
    public void stop() {
        if (pollTask != null) {
            pollTask.cancel(false);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        log.info("ProcessMonitor stopped");
    }

    // -----------------------------------------------------------------------
    // Internal polling
    // -----------------------------------------------------------------------

    private void poll() {
        try {
            String exeName = getForegroundExeName();
            if (exeName == null) {
                return;
            }

            String lower = exeName.toLowerCase(Locale.ROOT);
            if (lockedApps.contains(lower)) {
                if (!lower.equals(lastLockedApp)) {
                    lastLockedApp = lower;
                    log.info("Locked app detected in foreground: {}", exeName);
                    if (lockTrigger != null) {
                        lockTrigger.accept(lower);
                    }
                }
            } else {
                lastLockedApp = null;
            }
        } catch (Exception e) {
            log.debug("Poll error (ignored): {}", e.getMessage());
        }
    }

    /**
     * Returns the lower-case executable filename of the current foreground
     * window, or {@code null} if it cannot be determined.
     */
    String getForegroundExeName() {
        WinDef.HWND hwnd = User32.INSTANCE.GetForegroundWindow();
        if (hwnd == null) {
            return null;
        }

        int[] pid = new int[1];
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pid);
        if (pid[0] == 0) {
            return null;
        }

        HANDLE hProcess = Kernel32.INSTANCE.OpenProcess(
                WinNT.PROCESS_QUERY_INFORMATION | WinNT.PROCESS_VM_READ,
                false, pid[0]);
        if (hProcess == null) {
            return null;
        }

        try {
            char[] buf = new char[1024];
            int len = Psapi.INSTANCE.GetModuleFileNameExW(hProcess, null, buf, buf.length);
            if (len == 0) {
                return null;
            }
            String fullPath = new String(buf, 0, len);
            return Path.of(fullPath).getFileName().toString().toLowerCase(Locale.ROOT);
        } finally {
            Kernel32.INSTANCE.CloseHandle(hProcess);
        }
    }
}
