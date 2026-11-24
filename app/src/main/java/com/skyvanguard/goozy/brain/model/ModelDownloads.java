package com.skyvanguard.goozy.brain.model;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Descargas de modelos en curso. Vive a nivel de proceso para que la descarga
 * siga aunque se cierre la pantalla que la empezó.
 *
 * Una descarga a la vez. Los avisos llegan por el hilo principal.
 */
public final class ModelDownloads {
    private static final String TAG = "ModelDownloads";
    private static final String MODELS_DIRECTORY = "models";

    /** Lo que ve la pantalla. Todos los métodos se llaman en el hilo principal. */
    public interface Observer {
        void onProgress(LocalModel model, long downloadedBytes, long totalBytes);

        void onFinished(LocalModel model);

        void onCancelled(LocalModel model);

        void onFailed(LocalModel model, String reason);
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "goose-model-download");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static LocalModel activeModel;
    private static ModelDownloader activeDownloader;
    private static Observer observer;

    private ModelDownloads() {
    }

    /**
     * Carpeta de modelos. Se prefiere el almacenamiento externo propio de la
     * app: no pide permisos, se borra al desinstalar y admite archivos grandes.
     */
    public static File getDirectory(Context context) {
        File external = context.getApplicationContext().getExternalFilesDir(MODELS_DIRECTORY);
        if (external != null) return external;
        return new File(context.getApplicationContext().getFilesDir(), MODELS_DIRECTORY);
    }

    public static File getFile(Context context, LocalModel model) {
        return new File(getDirectory(context), model.fileName);
    }

    public static boolean isDownloaded(Context context, LocalModel model) {
        return ModelDownloader.isDownloaded(getDirectory(context), model);
    }

    public static synchronized void setObserver(Observer value) {
        observer = value;
    }

    /**
     * @return el modelo que se está descargando, o null si no hay descarga
     */
    public static synchronized LocalModel getActiveModel() {
        return activeModel;
    }

    /**
     * @return false si ya había una descarga en curso
     */
    public static synchronized boolean start(Context context, LocalModel model) {
        if (activeModel != null) return false;

        final File directory = getDirectory(context);
        final ModelDownloader downloader = new ModelDownloader();
        activeModel = model;
        activeDownloader = downloader;

        executor.execute(() -> {
            try {
                ModelDownloader.Result result = downloader.download(directory, model,
                        (downloaded, total) -> notifyObserver(
                                target -> target.onProgress(model, downloaded, total)));
                clearActive();
                if (result == ModelDownloader.Result.COMPLETED) {
                    notifyObserver(target -> target.onFinished(model));
                } else {
                    notifyObserver(target -> target.onCancelled(model));
                }
            } catch (IOException e) {
                Log.w(TAG, "Falló la descarga de " + model.id, e);
                clearActive();
                String reason = e.getMessage() != null ? e.getMessage() : e.toString();
                notifyObserver(target -> target.onFailed(model, reason));
            }
        });
        return true;
    }

    public static synchronized void cancel() {
        if (activeDownloader != null) {
            activeDownloader.cancel();
        }
    }

    public static boolean delete(Context context, LocalModel model) {
        synchronized (ModelDownloads.class) {
            if (activeModel != null && activeModel.id.equals(model.id)) return false;
        }
        return ModelDownloader.delete(getDirectory(context), model);
    }

    private static synchronized void clearActive() {
        activeModel = null;
        activeDownloader = null;
    }

    private interface Notification {
        void deliver(Observer target);
    }

    private static void notifyObserver(Notification notification) {
        mainHandler.post(() -> {
            Observer target;
            synchronized (ModelDownloads.class) {
                target = observer;
            }
            if (target != null) {
                notification.deliver(target);
            }
        });
    }
}
