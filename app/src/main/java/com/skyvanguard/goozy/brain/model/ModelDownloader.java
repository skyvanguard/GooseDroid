package com.skyvanguard.goozy.brain.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Descarga un modelo a un archivo ".part" y lo renombra al terminar, de modo
 * que un archivo con el nombre final siempre está completo. Si la descarga se
 * corta, la siguiente la retoma desde donde quedó.
 */
public class ModelDownloader {
    static final String PART_SUFFIX = ".part";
    private static final int BUFFER_BYTES = 64 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int HTTP_OK = 200;
    private static final int HTTP_PARTIAL = 206;
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;
    private static final long PROGRESS_INTERVAL_BYTES = 512 * 1024;

    /** Avisos de la descarga. Se invocan desde el hilo que descarga. */
    public interface Listener {
        void onProgress(long downloadedBytes, long totalBytes);
    }

    /** Resultado de una descarga. */
    public enum Result { COMPLETED, CANCELLED }

    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    public void cancel() {
        isCancelled.set(true);
    }

    public static boolean isDownloaded(File directory, LocalModel model) {
        File file = new File(directory, model.fileName);
        return file.isFile() && file.length() == model.sizeBytes;
    }

    /** Bytes ya descargados de una descarga sin terminar. */
    public static long getPartialBytes(File directory, LocalModel model) {
        File part = new File(directory, model.fileName + PART_SUFFIX);
        return part.isFile() ? part.length() : 0L;
    }

    public static boolean delete(File directory, LocalModel model) {
        File file = new File(directory, model.fileName);
        File part = new File(directory, model.fileName + PART_SUFFIX);
        boolean isFileGone = !file.exists() || file.delete();
        boolean isPartGone = !part.exists() || part.delete();
        return isFileGone && isPartGone;
    }

    /**
     * Descarga el modelo. Bloquea hasta terminar: llamar desde un hilo de fondo.
     *
     * @throws IOException si la red falla, no hay espacio o el tamaño final no
     *                     coincide con el esperado
     */
    public Result download(File directory, LocalModel model, Listener listener)
            throws IOException {
        if (!directory.exists() && !directory.mkdirs() && !directory.exists()) {
            throw new IOException("No se pudo crear " + directory);
        }
        File target = new File(directory, model.fileName);
        if (isDownloaded(directory, model)) {
            listener.onProgress(model.sizeBytes, model.sizeBytes);
            return Result.COMPLETED;
        }

        File part = new File(directory, model.fileName + PART_SUFFIX);
        long existing = part.isFile() ? part.length() : 0L;
        if (existing > model.sizeBytes) {
            // Resto de otra versión del archivo: no sirve para retomar
            if (!part.delete()) throw new IOException("No se pudo borrar " + part);
            existing = 0L;
        }
        if (existing < model.sizeBytes) {
            long missing = model.sizeBytes - existing;
            if (directory.getUsableSpace() < missing) {
                throw new IOException("No hay espacio suficiente: faltan "
                        + (missing / (1024L * 1024L)) + " MB");
            }
            Result result = fetch(model, part, existing, listener);
            if (result == Result.CANCELLED) return result;
        }

        if (part.length() != model.sizeBytes) {
            long actual = part.length();
            if (!part.delete()) {
                throw new IOException("Descarga corrupta y no se pudo borrar " + part);
            }
            throw new IOException("El archivo descargado mide " + actual
                    + " bytes y se esperaban " + model.sizeBytes);
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("No se pudo reemplazar " + target);
        }
        if (!part.renameTo(target)) {
            throw new IOException("No se pudo mover la descarga a " + target);
        }
        listener.onProgress(model.sizeBytes, model.sizeBytes);
        return Result.COMPLETED;
    }

    private Result fetch(LocalModel model, File part, long offset, Listener listener)
            throws IOException {
        HttpURLConnection connection =
                (HttpURLConnection) new URL(model.downloadUrl).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        if (offset > 0) {
            connection.setRequestProperty("Range", "bytes=" + offset + "-");
        }

        try {
            int status = connection.getResponseCode();
            long position = offset;
            if (status == HTTP_OK) {
                // El servidor ignoró el Range: se empieza de cero
                position = 0L;
            } else if (status == HTTP_RANGE_NOT_SATISFIABLE) {
                return Result.COMPLETED;
            } else if (status != HTTP_PARTIAL) {
                throw new IOException("El servidor respondió HTTP " + status);
            }

            try (InputStream in = connection.getInputStream();
                 RandomAccessFile out = new RandomAccessFile(part, "rw")) {
                out.setLength(position);
                out.seek(position);

                byte[] buffer = new byte[BUFFER_BYTES];
                long lastReported = position;
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (isCancelled.get()) return Result.CANCELLED;
                    out.write(buffer, 0, read);
                    position += read;
                    if (position - lastReported >= PROGRESS_INTERVAL_BYTES) {
                        lastReported = position;
                        listener.onProgress(position, model.sizeBytes);
                    }
                }
                out.getFD().sync();
            }
            return Result.COMPLETED;
        } finally {
            connection.disconnect();
        }
    }
}
