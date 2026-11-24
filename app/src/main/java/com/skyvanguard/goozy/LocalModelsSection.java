package com.skyvanguard.goozy;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import com.skyvanguard.goozy.brain.BackendCatalog;
import com.skyvanguard.goozy.brain.BrainConfig;
import com.skyvanguard.goozy.brain.backend.LiteRtBackend;
import com.skyvanguard.goozy.brain.model.LocalModel;
import com.skyvanguard.goozy.brain.model.LocalModelCatalog;
import com.skyvanguard.goozy.brain.model.ModelDownloads;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parte de la pantalla del cerebro que lista los modelos descargables: elegir
 * uno, descargarlo, cancelar la descarga o borrarlo.
 */
final class LocalModelsSection implements ModelDownloads.Observer {
    private static final int PROGRESS_MAX = 1000;
    private static final int ROW_PADDING_DP = 8;

    /** Aviso a la pantalla cuando cambia el modelo elegido o termina una descarga. */
    interface Listener {
        void onLocalModelChanged();
    }

    /** Vistas de una fila. */
    private static final class Row {
        final LocalModel model;
        final RadioButton radio;
        final TextView status;
        final ProgressBar progress;
        final MaterialButton action;

        Row(LocalModel model, RadioButton radio, TextView status, ProgressBar progress,
            MaterialButton action) {
            this.model = model;
            this.radio = radio;
            this.status = status;
            this.progress = progress;
            this.action = action;
        }
    }

    private final Activity activity;
    private final BrainConfig config;
    private final Listener listener;
    private final List<Row> rows = new ArrayList<>();

    LocalModelsSection(Activity activity, BrainConfig config, LinearLayout container,
                       Listener listener) {
        this.activity = activity;
        this.config = config;
        this.listener = listener;
        for (LocalModel model : LocalModelCatalog.getModels()) {
            rows.add(createRow(container, model));
        }
        refresh();
    }

    void onStart() {
        ModelDownloads.setObserver(this);
        refresh();
    }

    void onStop() {
        ModelDownloads.setObserver(null);
    }

    private Row createRow(LinearLayout container, LocalModel model) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(ROW_PADDING_DP);
        layout.setPadding(0, padding, 0, padding);

        RadioButton radio = new RadioButton(activity);
        radio.setText(String.format(Locale.ROOT, "%s · %d MB", model.title,
                model.getSizeMegabytes()));
        radio.setOnClickListener(v -> select(model));
        layout.addView(radio);

        TextView summary = new TextView(activity);
        summary.setText(activity.getString(R.string.BrainModelSummary, model.summary,
                model.license));
        summary.setTextSize(13f);
        layout.addView(summary);

        TextView status = new TextView(activity);
        status.setTextSize(13f);
        layout.addView(status);

        ProgressBar progress = new ProgressBar(activity, null,
                android.R.attr.progressBarStyleHorizontal);
        progress.setMax(PROGRESS_MAX);
        layout.addView(progress);

        MaterialButton action = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        action.setOnClickListener(v -> onAction(model));
        layout.addView(action);

        container.addView(layout);
        return new Row(model, radio, status, progress, action);
    }

    private void select(LocalModel model) {
        config.setModel(LiteRtBackend.ID, model.id);
        refresh();
        listener.onLocalModelChanged();
    }

    private void onAction(LocalModel model) {
        LocalModel active = ModelDownloads.getActiveModel();
        if (active != null && active.id.equals(model.id)) {
            ModelDownloads.cancel();
            return;
        }
        if (ModelDownloads.isDownloaded(activity, model)) {
            confirmDelete(model);
            return;
        }
        if (active != null) {
            Utils.showToast(activity, activity.getText(R.string.BrainModelOneAtATime));
            return;
        }
        confirmDownload(model);
    }

    private void confirmDownload(LocalModel model) {
        new MaterialAlertDialogBuilder(activity)
                .setMessage(activity.getString(R.string.BrainModelDownloadQuestion,
                        model.title, model.getSizeMegabytes()))
                .setPositiveButton(R.string.BrainModelDownload, (dialog, which) -> {
                    ModelDownloads.start(activity, model);
                    select(model);
                })
                .setNegativeButton(R.string.Cancel, null)
                .show();
    }

    private void confirmDelete(LocalModel model) {
        new MaterialAlertDialogBuilder(activity)
                .setMessage(activity.getString(R.string.BrainModelDeleteQuestion, model.title))
                .setPositiveButton(R.string.BrainModelDelete, (dialog, which) -> {
                    ModelDownloads.delete(activity, model);
                    refresh();
                    listener.onLocalModelChanged();
                })
                .setNegativeButton(R.string.Cancel, null)
                .show();
    }

    void refresh() {
        LocalModel selected = BackendCatalog.getSelectedLocalModel(config);
        LocalModel active = ModelDownloads.getActiveModel();
        for (Row row : rows) {
            boolean isActive = active != null && active.id.equals(row.model.id);
            boolean isDownloaded = ModelDownloads.isDownloaded(activity, row.model);

            row.radio.setChecked(row.model.id.equals(selected.id));
            row.progress.setVisibility(isActive ? View.VISIBLE : View.GONE);
            if (isActive) {
                row.action.setText(R.string.BrainModelCancel);
                row.status.setText(R.string.BrainModelDownloading);
            } else if (isDownloaded) {
                row.action.setText(R.string.BrainModelDelete);
                row.status.setText(R.string.BrainModelReady);
            } else {
                row.action.setText(R.string.BrainModelDownload);
                row.status.setText(R.string.BrainModelNotDownloaded);
            }
        }
    }

    private Row rowFor(LocalModel model) {
        for (Row row : rows) {
            if (row.model.id.equals(model.id)) return row;
        }
        return null;
    }

    // ============== ModelDownloads.Observer ==============

    @Override
    public void onProgress(LocalModel model, long downloadedBytes, long totalBytes) {
        Row row = rowFor(model);
        if (row == null || totalBytes <= 0) return;
        row.progress.setVisibility(View.VISIBLE);
        row.progress.setProgress((int) (PROGRESS_MAX * downloadedBytes / totalBytes));
        row.status.setText(activity.getString(R.string.BrainModelProgress,
                downloadedBytes / (1024L * 1024L), totalBytes / (1024L * 1024L)));
    }

    @Override
    public void onFinished(LocalModel model) {
        refresh();
        listener.onLocalModelChanged();
    }

    @Override
    public void onCancelled(LocalModel model) {
        refresh();
    }

    @Override
    public void onFailed(LocalModel model, String reason) {
        refresh();
        Row row = rowFor(model);
        if (row != null) {
            row.status.setText(activity.getString(R.string.BrainModelFailed, reason));
        }
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
