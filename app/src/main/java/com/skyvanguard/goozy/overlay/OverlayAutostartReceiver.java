package com.skyvanguard.goozy.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * Vuelve a poner al ganso en pantalla después de reiniciar el teléfono o de
 * actualizar la app, si el usuario lo había dejado encendido.
 */
public class OverlayAutostartReceiver extends BroadcastReceiver {
    private static final String TAG = "OverlayAutostart";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        boolean isBoot = Intent.ACTION_BOOT_COMPLETED.equals(action);
        boolean isUpdate = Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!isBoot && !isUpdate) return;

        if (!GooseOverlayService.isEnabledByUser(context)) return;
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Sin permiso de overlay: el ganso no se enciende solo");
            return;
        }
        try {
            GooseOverlayService.start(context);
        } catch (RuntimeException e) {
            // Algunos fabricantes bloquean servicios al arrancar; queda para cuando abra la app
            Log.w(TAG, "No se pudo encender el ganso automáticamente", e);
        }
    }
}
