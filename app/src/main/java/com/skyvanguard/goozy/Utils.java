package com.skyvanguard.goozy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Point;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Utility class with helper methods for common operations.
 */
public class Utils {

    /**
     * Show a short toast message.
     */
    public static void showToast(Context context, CharSequence message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Show an alert dialog with title and message.
     */
    public static void showDialog(Context context, String title, String message) {
        new AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    /**
     * Get screen width in pixels.
     */
    public static int getScreenWidth(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.x;
    }

    /**
     * Get screen height in pixels.
     */
    public static int getScreenHeight(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        return size.y;
    }

    /**
     * Copy text to clipboard.
     */
    public static void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("text", text);
        clipboard.setPrimaryClip(clip);
    }

    /**
     * Get app private directory path.
     */
    public static String getPrivateDir(Context context) {
        return context.getFilesDir().getAbsolutePath();
    }

    /**
     * Check if file exists.
     */
    public static boolean fileExists(String path) {
        return new File(path).exists();
    }

    /**
     * Delete file.
     */
    public static boolean deleteFile(String path) {
        return new File(path).delete();
    }

    /**
     * Copy asset file to destination path.
     */
    public static void copyAssetFile(Context context, String assetName, String destPath) {
        try {
            InputStream in = context.getAssets().open(assetName);
            FileOutputStream out = new FileOutputStream(destPath);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Parse color string to int. Supports formats: #RGB, #ARGB, #RRGGBB, #AARRGGBB
     */
    public static int parseColor(String colorString) {
        try {
            if (colorString == null || colorString.isEmpty()) {
                return Color.WHITE;
            }
            return Color.parseColor(colorString);
        } catch (Exception e) {
            return Color.WHITE;
        }
    }
}
