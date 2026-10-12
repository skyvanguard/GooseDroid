package com.skyvanguard.goozy.brain;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.skyvanguard.goozy.brain.backend.TemplateBackend;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Configuración del cerebro. Lo común va en preferencias normales; las claves
 * de API van en preferencias cifradas con una llave del Keystore del teléfono.
 */
public final class BrainConfig {
    private static final String TAG = "BrainConfig";
    private static final String PREFS_NAME = "goose_brain";
    private static final String SECRET_PREFS_NAME = "goose_brain_secrets";

    private static final String KEY_BACKEND = "backend";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_INTERVAL_SECONDS = "interval_seconds";
    private static final String KEY_VOICE_ENABLED = "voice_enabled";
    private static final String KEY_GPU_ENABLED = "gpu_enabled";
    private static final String KEY_PREFIX_URL = "url_";
    private static final String KEY_PREFIX_MODEL = "model_";
    private static final String KEY_PREFIX_API_KEY = "api_key_";

    public static final String DEFAULT_LANGUAGE = "español";
    public static final int DEFAULT_INTERVAL_SECONDS = 60;
    public static final int MIN_INTERVAL_SECONDS = 15;
    public static final int MAX_INTERVAL_SECONDS = 3600;

    private final SharedPreferences prefs;
    private final Context appContext;
    private SharedPreferences secretPrefs;

    public BrainConfig(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getBackendId() {
        return prefs.getString(KEY_BACKEND, TemplateBackend.ID);
    }

    public void setBackendId(String backendId) {
        prefs.edit().putString(KEY_BACKEND, backendId).apply();
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public int getIntervalSeconds() {
        int value = prefs.getInt(KEY_INTERVAL_SECONDS, DEFAULT_INTERVAL_SECONDS);
        return Math.max(MIN_INTERVAL_SECONDS, Math.min(value, MAX_INTERVAL_SECONDS));
    }

    public void setIntervalSeconds(int seconds) {
        prefs.edit().putInt(KEY_INTERVAL_SECONDS, seconds).apply();
    }

    public boolean isGpuEnabled() {
        return prefs.getBoolean(KEY_GPU_ENABLED, false);
    }

    public void setGpuEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GPU_ENABLED, enabled).apply();
    }

    public boolean isVoiceEnabled() {
        return prefs.getBoolean(KEY_VOICE_ENABLED, false);
    }

    public void setVoiceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply();
    }

    public String getUrl(String backendId, String defaultValue) {
        return prefs.getString(KEY_PREFIX_URL + backendId, defaultValue);
    }

    public void setUrl(String backendId, String url) {
        prefs.edit().putString(KEY_PREFIX_URL + backendId, url.trim()).apply();
    }

    public String getModel(String backendId, String defaultValue) {
        return prefs.getString(KEY_PREFIX_MODEL + backendId, defaultValue);
    }

    public void setModel(String backendId, String model) {
        prefs.edit().putString(KEY_PREFIX_MODEL + backendId, model.trim()).apply();
    }

    /**
     * @return la clave guardada, o cadena vacía si no hay o no se pudo leer
     */
    public String getApiKey(String backendId) {
        SharedPreferences secrets = getSecretPrefs();
        return secrets != null ? secrets.getString(KEY_PREFIX_API_KEY + backendId, "") : "";
    }

    /**
     * @return false si el almacenamiento cifrado no está disponible; la clave
     *         no se guarda en ningún otro lado
     */
    public boolean setApiKey(String backendId, String apiKey) {
        SharedPreferences secrets = getSecretPrefs();
        if (secrets == null) return false;
        SharedPreferences.Editor editor = secrets.edit();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            editor.remove(KEY_PREFIX_API_KEY + backendId);
        } else {
            editor.putString(KEY_PREFIX_API_KEY + backendId, apiKey.trim());
        }
        editor.apply();
        return true;
    }

    public boolean hasApiKey(String backendId) {
        return !getApiKey(backendId).isEmpty();
    }

    private synchronized SharedPreferences getSecretPrefs() {
        if (secretPrefs != null) return secretPrefs;
        try {
            MasterKey masterKey = new MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            secretPrefs = EncryptedSharedPreferences.create(
                    appContext,
                    SECRET_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            // Sin almacenamiento cifrado no se guardan claves: nunca en texto plano
            Log.e(TAG, "Almacenamiento cifrado no disponible", e);
            return null;
        }
        return secretPrefs;
    }
}
