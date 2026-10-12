package com.cfks.goosedroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Permiso para dibujar sobre otras apps, sin el cual el ganso no puede salir. */
public final class OverlayPermission {
    private OverlayPermission() {
    }

    public static boolean isGranted(Context context) {
        return Settings.canDrawOverlays(context);
    }

    /** Abre la pantalla del sistema donde el usuario concede el permiso. */
    public static void openSettings(Activity activity, int requestCode) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
        activity.startActivityForResult(intent, requestCode);
    }
}
