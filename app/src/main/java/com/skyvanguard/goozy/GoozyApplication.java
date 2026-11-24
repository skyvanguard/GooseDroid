package com.skyvanguard.goozy;

import android.app.Application;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.skyvanguard.goozy.engine.Sound;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Application class for Goozy.
 * Handles global crash reporting, memory management, and resource cleanup.
 */
public class GoozyApplication extends Application {

    private static final String TAG = "GoozyApp";
    private static GoozyApplication instance;
    private Thread.UncaughtExceptionHandler defaultHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Set up global crash handler
        setupCrashHandler();

        // Application ya recibe onTrimMemory/onLowMemory por sí misma. Registrarse
        // además como callback hace que super.onTrimMemory() se llame a sí mismo
        // hasta desbordar la pila.

        Log.i(TAG, "Goozy initialized");
    }

    /**
     * Get the application instance.
     */
    public static GoozyApplication getInstance() {
        return instance;
    }

    /**
     * Set up global uncaught exception handler to log crashes.
     */
    private void setupCrashHandler() {
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                // Log the crash
                Log.e(TAG, "FATAL CRASH in thread " + thread.getName(), throwable);

                // Save crash log to file
                saveCrashLog(thread, throwable);

                // Clean up resources before crashing
                cleanupResources();

            } catch (Exception e) {
                Log.e(TAG, "Error in crash handler", e);
            } finally {
                // Call the default handler to show the crash dialog
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });
    }

    /**
     * Save crash log to a file for later analysis.
     */
    private void saveCrashLog(Thread thread, Throwable throwable) {
        try {
            File crashDir = new File(getFilesDir(), "crash_logs");
            if (!crashDir.exists()) {
                crashDir.mkdirs();
            }

            // Limit number of crash logs (keep last 5)
            File[] existingLogs = crashDir.listFiles();
            if (existingLogs != null && existingLogs.length >= 5) {
                // Delete oldest files
                java.util.Arrays.sort(existingLogs, (a, b) ->
                    Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < existingLogs.length - 4; i++) {
                    existingLogs[i].delete();
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US);
            String timestamp = sdf.format(new Date());
            File crashFile = new File(crashDir, "crash_" + timestamp + ".txt");

            try (PrintWriter writer = new PrintWriter(new FileWriter(crashFile))) {
                writer.println("Goozy crash report");
                writer.println("=======================");
                writer.println("Time: " + timestamp);
                writer.println("Thread: " + thread.getName());
                writer.println("Android Version: " + Build.VERSION.RELEASE);
                writer.println("SDK: " + Build.VERSION.SDK_INT);
                writer.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
                writer.println();
                writer.println("Stack Trace:");
                writer.println("------------");
                throwable.printStackTrace(writer);

                // Print cause chain
                Throwable cause = throwable.getCause();
                while (cause != null) {
                    writer.println();
                    writer.println("Caused by:");
                    cause.printStackTrace(writer);
                    cause = cause.getCause();
                }
            }

            Log.i(TAG, "Crash log saved to: " + crashFile.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Failed to save crash log", e);
        }
    }

    /**
     * Clean up resources when app is terminating.
     */
    private void cleanupResources() {
        try {
            // Clean up notification manager
            PetNotificationManager.cleanup();

            Log.i(TAG, "Resources cleaned up");
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up resources", e);
        }
    }

    /**
     * Called when the system is running low on memory.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        Log.w(TAG, "onTrimMemory called with level: " + level);

        switch (level) {
            case TRIM_MEMORY_RUNNING_MODERATE:
                // App is in foreground, system is running low
                Log.i(TAG, "Memory moderate - consider reducing usage");
                break;

            case TRIM_MEMORY_RUNNING_LOW:
                // App is in foreground, system is very low
                Log.w(TAG, "Memory low - releasing non-critical resources");
                releaseNonCriticalResources();
                break;

            case TRIM_MEMORY_RUNNING_CRITICAL:
                // App is in foreground, system is critically low
                Log.w(TAG, "Memory critical - releasing all possible resources");
                releaseAllResources();
                break;

            case TRIM_MEMORY_UI_HIDDEN:
                // App UI is hidden (went to background)
                Log.i(TAG, "UI hidden - app went to background");
                break;

            case TRIM_MEMORY_BACKGROUND:
            case TRIM_MEMORY_MODERATE:
            case TRIM_MEMORY_COMPLETE:
                // App is in background and system needs memory
                Log.w(TAG, "Background memory pressure - releasing resources");
                releaseAllResources();
                break;
        }
    }

    /**
     * Release non-critical resources to free memory.
     */
    private void releaseNonCriticalResources() {
        if (com.skyvanguard.goozy.engine.TheGoose.isRunning()) {
            return;
        }
        try {
            Log.i(TAG, "Non-critical resources released");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing non-critical resources", e);
        }
    }

    /**
     * Release all possible resources to free memory.
     */
    private void releaseAllResources() {
        if (com.skyvanguard.goozy.engine.TheGoose.isRunning()) {
            // El ganso sigue en pantalla: liberar el audio lo dejaría mudo.
            return;
        }
        try {
            // Release sound resources
            Sound.release();

            // Force garbage collection
            System.gc();

            Log.i(TAG, "All resources released");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing all resources", e);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "onLowMemory called - releasing resources");
        releaseAllResources();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        cleanupResources();
    }
}
