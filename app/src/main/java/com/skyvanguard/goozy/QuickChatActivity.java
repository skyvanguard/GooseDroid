package com.skyvanguard.goozy;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.skyvanguard.goozy.brain.BrainController;
import com.skyvanguard.goozy.brain.BrainIntent;
import com.skyvanguard.goozy.brain.BrainTrigger;
import com.skyvanguard.goozy.brain.LlmException;
import com.google.android.material.textfield.TextInputEditText;

/**
 * Ventanita flotante para escribirle al ganso sin salir de la app que se está
 * usando. Se abre con una pulsación larga sobre el ganso o desde la
 * notificación.
 */
public class QuickChatActivity extends AppCompatActivity implements BrainController.ReplyListener {

    private TextInputEditText input;
    private TextView reply;
    private boolean isWaiting = false;

    /** Intent para abrir el chat desde fuera de una Activity (servicio, notificación). */
    public static Intent createIntent(Context context) {
        return new Intent(context, QuickChatActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_chat);
        setFinishOnTouchOutside(true);

        TextView title = findViewById(R.id.QuickChatTitle);
        title.setText(getString(R.string.QuickChatTitleWithName, PetAppearance.get().petName));

        input = findViewById(R.id.QuickChatInput);
        reply = findViewById(R.id.QuickChatReply);
        findViewById(R.id.QuickChatSend).setOnClickListener(v -> send());
        findViewById(R.id.QuickChatClose).setOnClickListener(v -> finish());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            send();
            return true;
        });
        input.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        BrainController.setReplyListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        BrainController.setReplyListener(null);
    }

    private void send() {
        Editable text = input.getText();
        String message = text != null ? text.toString().trim() : "";
        if (message.isEmpty() || isWaiting) return;

        if (!BrainController.think(this, BrainTrigger.Kind.CHAT, message)) {
            reply.setText(R.string.BrainBusy);
            return;
        }
        isWaiting = true;
        input.setText("");
        reply.setText(R.string.BrainThinking);
    }

    @Override
    public void onReply(BrainIntent intent, String backendId) {
        isWaiting = false;
        reply.setText(intent.hasSpeech() ? intent.say : getString(R.string.BrainSilent));
    }

    @Override
    public void onError(LlmException error, String backendId) {
        // Enseguida responde el respaldo; no hace falta mostrar el error acá
    }
}
