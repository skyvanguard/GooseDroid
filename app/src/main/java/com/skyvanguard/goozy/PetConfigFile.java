package com.skyvanguard.goozy;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Archivo de configuración de la mascota (formato properties, clave=valor).
 *
 * Solo lo usa {@link PetRepository}; el resto del código lee los valores ya
 * cargados a través de él.
 */
public final class PetConfigFile {
    private static final String TAG = "PetConfigFile";
    private static final long MAX_BYTES = 1024 * 1024;
    private static final Object WRITE_LOCK = new Object();

    private Properties values = new Properties();

    /** Carga el archivo; reemplaza lo que hubiera en memoria. */
    public void load(File file) throws IOException {
        if (!file.isFile()) throw new IOException("No existe el archivo de configuración: " + file);
        if (file.length() > MAX_BYTES) {
            throw new IOException("Configuración demasiado grande: " + file.length() + " bytes");
        }
        Properties loaded = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            loaded.load(in);
        }
        values = loaded;
    }

    /** Valor guardado, o null si no existe. */
    public String get(String key) {
        return key == null ? null : values.getProperty(key);
    }

    public String get(String key, String fallback) {
        String value = get(key);
        return value != null ? value : fallback;
    }

    /**
     * Escribe {@code updates} sobre el archivo, conservando las claves que no
     * vienen en el mapa. La escritura es atómica: un corte deja el archivo viejo.
     */
    public static void saveMerged(File file, Properties updates) throws IOException {
        synchronized (WRITE_LOCK) {
            Properties merged = new Properties();
            if (file.isFile()) {
                try (InputStream in = new FileInputStream(file)) {
                    merged.load(in);
                } catch (IOException | IllegalArgumentException e) {
                    Log.w(TAG, "Configuración ilegible, se reescribe: " + file, e);
                }
            }
            for (String key : updates.stringPropertyNames()) {
                merged.setProperty(key, updates.getProperty(key));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            merged.store(out, null);
            AtomicFiles.write(file, out.toByteArray());
        }
    }
}
