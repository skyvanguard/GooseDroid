package com.skyvanguard.goozy;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.skyvanguard.goozy.brain.BackendCatalog;
import com.skyvanguard.goozy.brain.BrainConfig;
import com.skyvanguard.goozy.brain.BrainController;
import com.skyvanguard.goozy.brain.BrainIntent;
import com.skyvanguard.goozy.brain.BrainMemory;
import com.skyvanguard.goozy.brain.BrainTrigger;
import com.skyvanguard.goozy.brain.LlmException;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

/**
 * Pantalla del cerebro: hablar con el ganso, elegir con qué piensa y ver lo
 * que recuerda y lo que escribe en su diario.
 */
public class BrainActivity extends AppCompatActivity implements BrainController.ReplyListener {
    private static final int MAX_DIARY_ENTRIES_SHOWN = 14;
    private static final int LIST_ITEM_PADDING_DP = 6;

    private BrainConfig config;
    private BackendCatalog.Entry selectedEntry;

    private RadioGroup backendGroup;
    private TextView backendSummary;
    private TextView remoteWarning;
    private View urlLayout;
    private View modelLayout;
    private View apiKeyLayout;
    private View apiKeyDeleteButton;
    private View intervalLayout;
    private TextInputEditText urlInput;
    private TextInputEditText modelInput;
    private TextInputEditText apiKeyInput;
    private TextInputEditText intervalInput;
    private TextView testResult;
    private TextInputEditText chatInput;
    private TextView chatReply;
    private TextView chatMeta;
    private LinearLayout memoryList;
    private LinearLayout diaryList;
    private View localModelsLayout;
    private com.google.android.material.materialswitch.MaterialSwitch useGpuSwitch;
    private LocalModelsSection localModels;

    /** Qué pantalla pidió el pensamiento en curso, para saber dónde mostrarlo. */
    private enum Pending { NONE, CHAT, TEST, DIARY }

    private Pending pending = Pending.NONE;
    /** Por qué falló el cerebro principal en el pedido en curso, o null. */
    private String pendingError = null;
    private long requestStartMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_brain);

        MaterialToolbar toolbar = findViewById(R.id.BrainToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        config = new BrainConfig(this);
        bindViews();
        localModels = new LocalModelsSection(this, config, findViewById(R.id.BrainLocalModels),
                () -> {
                    if (selectedEntry != null && selectedEntry.needsLocalModel) {
                        testResult.setText("");
                    }
                });
        buildBackendOptions();
        selectEntry(BackendCatalog.find(config.getBackendId()));
        refreshMemory();
        refreshDiary();
    }

    @Override
    protected void onStart() {
        super.onStart();
        BrainController.setReplyListener(this);
        localModels.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        BrainController.setReplyListener(null);
        localModels.onStop();
        pending = Pending.NONE;
    }

    private void bindViews() {
        backendGroup = findViewById(R.id.BrainBackendGroup);
        backendSummary = findViewById(R.id.BrainBackendSummary);
        remoteWarning = findViewById(R.id.BrainRemoteWarning);
        urlLayout = findViewById(R.id.BrainUrlLayout);
        modelLayout = findViewById(R.id.BrainModelLayout);
        apiKeyLayout = findViewById(R.id.BrainApiKeyLayout);
        apiKeyDeleteButton = findViewById(R.id.BrainApiKeyDelete);
        apiKeyDeleteButton.setOnClickListener(v -> confirmDeleteApiKey());
        intervalLayout = findViewById(R.id.BrainIntervalLayout);
        urlInput = findViewById(R.id.BrainUrl);
        modelInput = findViewById(R.id.BrainModel);
        apiKeyInput = findViewById(R.id.BrainApiKey);
        intervalInput = findViewById(R.id.BrainInterval);
        testResult = findViewById(R.id.BrainTestResult);
        chatInput = findViewById(R.id.BrainChatInput);
        chatReply = findViewById(R.id.BrainChatReply);
        chatMeta = findViewById(R.id.BrainChatMeta);
        memoryList = findViewById(R.id.BrainMemoryList);
        diaryList = findViewById(R.id.BrainDiaryList);
        localModelsLayout = findViewById(R.id.BrainLocalModelsLayout);
        useGpuSwitch = findViewById(R.id.BrainUseGpu);
        useGpuSwitch.setChecked(config.isGpuEnabled());
        useGpuSwitch.setOnCheckedChangeListener((button, isChecked) ->
                config.setGpuEnabled(isChecked));

        com.google.android.material.materialswitch.MaterialSwitch voiceSwitch =
                findViewById(R.id.BrainVoice);
        voiceSwitch.setChecked(config.isVoiceEnabled());
        voiceSwitch.setOnCheckedChangeListener((button, isChecked) ->
                config.setVoiceEnabled(isChecked));

        MaterialButton saveAndTest = findViewById(R.id.BrainSaveAndTest);
        saveAndTest.setOnClickListener(v -> saveAndTest());

        MaterialButton send = findViewById(R.id.BrainChatSend);
        send.setOnClickListener(v -> sendChat());
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            sendChat();
            return true;
        });

        MaterialButton clearMemory = findViewById(R.id.BrainMemoryClear);
        clearMemory.setOnClickListener(v -> confirmClearMemory());

        MaterialButton writeDiary = findViewById(R.id.BrainDiaryWrite);
        writeDiary.setOnClickListener(v -> writeDiaryNow());
    }

    // ============== TIPO DE CEREBRO ==============

    private void buildBackendOptions() {
        List<BackendCatalog.Entry> entries = BackendCatalog.getEntries();
        for (int i = 0; i < entries.size(); i++) {
            BackendCatalog.Entry entry = entries.get(i);
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(entry.title);
            button.setTag(entry);
            backendGroup.addView(button);
        }
        backendGroup.setOnCheckedChangeListener((group, checkedId) -> {
            View checked = group.findViewById(checkedId);
            if (checked != null && checked.getTag() instanceof BackendCatalog.Entry) {
                selectEntry((BackendCatalog.Entry) checked.getTag());
            }
        });
    }

    private void selectEntry(BackendCatalog.Entry entry) {
        selectedEntry = entry;
        for (int i = 0; i < backendGroup.getChildCount(); i++) {
            RadioButton button = (RadioButton) backendGroup.getChildAt(i);
            if (button.getTag() == entry && !button.isChecked()) {
                button.setChecked(true);
            }
        }

        backendSummary.setText(entry.summary);
        remoteWarning.setVisibility(entry.isRemote ? View.VISIBLE : View.GONE);
        urlLayout.setVisibility(entry.needsUrl ? View.VISIBLE : View.GONE);
        modelLayout.setVisibility(entry.needsModel ? View.VISIBLE : View.GONE);
        apiKeyLayout.setVisibility(entry.needsApiKey ? View.VISIBLE : View.GONE);
        localModelsLayout.setVisibility(entry.needsLocalModel ? View.VISIBLE : View.GONE);
        boolean isModelBacked = entry.isRemote || entry.needsLocalModel;
        intervalLayout.setVisibility(isModelBacked ? View.VISIBLE : View.GONE);

        urlInput.setText(config.getUrl(entry.id, entry.defaultUrl));
        modelInput.setText(config.getModel(entry.id, entry.defaultModel));
        // La clave guardada nunca se vuelve a mostrar: el campo vacío la conserva
        apiKeyInput.setText("");
        boolean hasSavedKey = config.hasApiKey(entry.id);
        apiKeyInput.setHint(hasSavedKey ? getString(R.string.BrainApiKeySaved) : null);
        apiKeyDeleteButton.setVisibility(entry.needsApiKey && hasSavedKey
                ? View.VISIBLE : View.GONE);
        intervalInput.setText(String.valueOf(config.getIntervalSeconds()));
        testResult.setText("");
    }

    private void saveAndTest() {
        BackendCatalog.Entry entry = selectedEntry;
        config.setBackendId(entry.id);
        if (entry.needsUrl) {
            config.setUrl(entry.id, textOf(urlInput));
        }
        if (entry.needsModel) {
            config.setModel(entry.id, textOf(modelInput));
        }
        String apiKey = textOf(apiKeyInput);
        if (entry.needsApiKey && !apiKey.isEmpty() && !config.setApiKey(entry.id, apiKey)) {
            testResult.setText(R.string.BrainApiKeyNotSaved);
            return;
        }
        config.setIntervalSeconds(parseInterval(textOf(intervalInput)));

        if (entry.needsLocalModel && !com.skyvanguard.goozy.brain.model.ModelDownloads.isDownloaded(
                this, BackendCatalog.getSelectedLocalModel(config))) {
            testResult.setText(R.string.BrainModelMissing);
            BrainController.reloadConfig(this);
            return;
        }

        BrainController.reloadConfig(this);
        selectEntry(entry);

        testResult.setText(R.string.BrainThinking);
        startRequest(Pending.TEST, BrainTrigger.Kind.TEST, "");
    }

    private void confirmDeleteApiKey() {
        BackendCatalog.Entry entry = selectedEntry;
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.BrainApiKeyDeleteQuestion, entry.title))
                .setPositiveButton(R.string.BrainApiKeyDelete, (dialog, which) -> {
                    config.setApiKey(entry.id, "");
                    BrainController.reloadConfig(this);
                    selectEntry(entry);
                })
                .setNegativeButton(R.string.Cancel, null)
                .show();
    }

    private static int parseInterval(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return BrainConfig.DEFAULT_INTERVAL_SECONDS;
        }
    }

    // ============== HABLAR ==============

    private void sendChat() {
        String message = textOf(chatInput);
        if (message.isEmpty()) return;
        chatReply.setText(R.string.BrainThinking);
        chatMeta.setText("");
        if (startRequest(Pending.CHAT, BrainTrigger.Kind.CHAT, message)) {
            chatInput.setText("");
        } else {
            chatReply.setText(R.string.BrainBusy);
        }
    }

    private void writeDiaryNow() {
        if (!startRequest(Pending.DIARY, BrainTrigger.Kind.DIARY, "")) {
            Utils.showToast(this, getText(R.string.BrainBusy));
        } else {
            Utils.showToast(this, getText(R.string.BrainThinking));
        }
    }

    private boolean startRequest(Pending kind, BrainTrigger.Kind trigger, String detail) {
        boolean isStarted = BrainController.think(this, trigger, detail);
        if (isStarted) {
            pending = kind;
            pendingError = null;
            requestStartMs = SystemClock.elapsedRealtime();
        }
        return isStarted;
    }

    @Override
    public void onReply(BrainIntent intent, String backendId) {
        long elapsedMs = SystemClock.elapsedRealtime() - requestStartMs;
        String meta = String.format(Locale.ROOT, "%s · %.1f s",
                BackendCatalog.find(backendId).title, elapsedMs / 1000f);
        String text = intent.hasSpeech() ? intent.say : getString(R.string.BrainSilent);

        // Si respondió el respaldo, el motivo de la falla sigue a la vista
        String errorPrefix = pendingError != null ? pendingError + "\n" : "";
        switch (pending) {
            case CHAT:
                chatReply.setText(text);
                chatMeta.setText(errorPrefix + describe(intent, meta));
                break;
            case TEST:
                testResult.setText(errorPrefix + getString(R.string.BrainTestOk, text, meta));
                break;
            case DIARY:
                refreshDiary();
                break;
            case NONE:
            default:
                break;
        }
        pending = Pending.NONE;
        pendingError = null;
        if (intent.hasMemory()) {
            refreshMemory();
        }
    }

    @Override
    public void onError(LlmException error, String backendId) {
        String message = getString(R.string.BrainTestError,
                BackendCatalog.find(backendId).title, error.getMessage());
        pendingError = message;
        if (pending == Pending.TEST) {
            testResult.setText(message);
        } else if (pending == Pending.CHAT) {
            chatMeta.setText(message);
        }
        // No se limpia "pending": enseguida llega la respuesta del respaldo
    }

    private String describe(BrainIntent intent, String meta) {
        StringBuilder sb = new StringBuilder(meta);
        sb.append(" · ").append(intent.mood.name().toLowerCase(Locale.ROOT));
        if (intent.action != com.skyvanguard.goozy.brain.BrainAction.NONE) {
            sb.append(" · ").append(intent.action.getDescription());
        }
        return sb.toString();
    }

    // ============== MEMORIA Y DIARIO ==============

    private void refreshMemory() {
        BrainMemory memory = BrainController.getMemory(this);
        List<String> facts = memory.getFacts();
        memoryList.removeAllViews();
        if (facts.isEmpty()) {
            memoryList.addView(createItem(getString(R.string.BrainMemoryEmpty), null));
            return;
        }
        for (int i = facts.size() - 1; i >= 0; i--) {
            final String fact = facts.get(i);
            memoryList.addView(createItem("• " + fact, v -> confirmForget(fact)));
        }
    }

    private void refreshDiary() {
        List<BrainMemory.DiaryEntry> diary = BrainController.getMemory(this).getDiary();
        diaryList.removeAllViews();
        if (diary.isEmpty()) {
            diaryList.addView(createItem(getString(R.string.BrainDiaryEmpty), null));
            return;
        }
        int shown = 0;
        for (int i = diary.size() - 1; i >= 0 && shown < MAX_DIARY_ENTRIES_SHOWN; i--, shown++) {
            BrainMemory.DiaryEntry entry = diary.get(i);
            diaryList.addView(createItem(entry.date + "\n" + entry.text, null));
        }
    }

    private TextView createItem(String text, View.OnClickListener onClick) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(14f);
        int padding = Math.round(LIST_ITEM_PADDING_DP * getResources().getDisplayMetrics().density);
        view.setPadding(0, padding, 0, padding);
        if (onClick != null) {
            view.setOnClickListener(onClick);
        }
        return view;
    }

    private void confirmForget(String fact) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.BrainForgetQuestion, fact))
                .setPositiveButton(R.string.BrainForget, (dialog, which) -> {
                    BrainController.getMemory(this).forget(fact);
                    refreshMemory();
                })
                .setNegativeButton(R.string.Cancel, null)
                .show();
    }

    private void confirmClearMemory() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.BrainMemoryClearQuestion)
                .setPositiveButton(R.string.BrainMemoryClear, (dialog, which) -> {
                    BrainController.getMemory(this).clear();
                    refreshMemory();
                    refreshDiary();
                })
                .setNegativeButton(R.string.Cancel, null)
                .show();
    }

    private static String textOf(TextInputEditText input) {
        Editable text = input.getText();
        return text != null ? text.toString().trim() : "";
    }
}
