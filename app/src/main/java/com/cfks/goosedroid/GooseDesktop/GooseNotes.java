package com.cfks.goosedroid.GooseDesktop;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import com.cfks.goosedroid.core.Vec2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;

/**
 * Las notas que el ganso trae arrastrando desde el borde de la pantalla.
 *
 * El texto sale de las notas incluidas en la app (según el idioma) o, si hay
 * un modelo de IA activo, de una nota que el ganso escribe para el humano.
 * Todo corre en el hilo principal.
 */
public final class GooseNotes {
    private static final String TAG = "GooseNotes";
    private static final String NOTES_DIRECTORY = "Text/NotepadMessages/";
    private static final String BYTE_ORDER_MARK = "﻿";
    private static final int MAX_NOTE_CHARS = 400;

    /** Notas incluidas por idioma. */
    private static final String[] SPANISH_NOTES = {
        "soy ganso.txt", "la paz nunca.txt", "lista de tareas.txt",
        "rutina de la mañana.txt", "sabiduria.txt", "dificil escribir.txt", "gooseASCII1.txt",
    };
    private static final String[] ENGLISH_NOTES = {
        "am goose.txt", "good work.txt", "hard to type.txt", "i cause problems.txt",
        "morning routine.txt", "peace was never.txt", "todo list.txt", "wisdom.txt",
        "gooseASCII1.txt",
    };

    // Medidas en unidades de mundo
    private static final float CARD_WIDTH = 120f;
    private static final float CARD_PADDING = 8f;
    private static final float TEXT_SIZE = 8.5f;
    private static final float CORNER_RADIUS = 3f;
    private static final float FOLD_SIZE = 10f;
    private static final float SHADOW_OFFSET = 2f;
    private static final int PAPER_COLOR = Color.rgb(255, 244, 168);
    private static final int FOLD_COLOR = Color.rgb(232, 214, 120);
    private static final int INK_COLOR = Color.rgb(60, 50, 30);
    private static final int SHADOW_COLOR = Color.argb(60, 0, 0, 0);

    private static final NoteCarrier carrier = new NoteCarrier();
    private static final Random random = new Random();

    private static final Paint paperPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint foldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint textPaint = createTextPaint();
    private static final RectF cardRect = new RectF();
    private static final android.graphics.Path foldPath = new android.graphics.Path();

    private static StaticLayout layout;
    private static String layoutText;

    private GooseNotes() {
    }

    private static TextPaint createTextPaint() {
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(TEXT_SIZE);
        paint.setTypeface(Typeface.MONOSPACE);
        return paint;
    }

    static void reset() {
        carrier.reset();
        layout = null;
        layoutText = null;
    }

    /** El ganso sale a buscar una nota. */
    static void prepare(Context context, boolean isSpanish) {
        carrier.prepare(loadRandomNote(context, isSpanish));
        // Si hay un modelo activo, le pide una nota propia; si llega a tiempo la reemplaza
        com.cfks.goosedroid.brain.BrainController.requestNote();
    }

    /** Texto escrito por el modelo para la nota en camino. */
    public static void offerText(String text) {
        carrier.offerText(text);
    }

    /**
     * Empieza a arrastrarla desde el borde.
     *
     * @param dirX dirección hacia el borde de donde viene (-1 izquierda, 1 derecha)
     * @param dirY dirección hacia el borde (-1 arriba)
     */
    static void pickUp(float dirX, float dirY, Vec2 goosePosition) {
        carrier.pickUp(dirX, dirY, goosePosition);
    }

    /** La deja en pantalla, entera dentro del mundo. */
    static void place(float worldWidth, float worldHeight) {
        StaticLayout current = ensureLayout();
        float halfHeight = current != null ? cardHeight(current) / 2f : 0f;
        carrier.place(worldWidth, worldHeight, CARD_WIDTH / 2f, halfHeight);
    }

    static void update(float deltaTime, Vec2 goosePosition) {
        carrier.update(deltaTime, goosePosition);
    }

    static void render(Canvas canvas) {
        float alpha = carrier.getAlpha();
        if (alpha <= 0f) return;
        StaticLayout current = ensureLayout();
        if (current == null) return;

        float height = cardHeight(current);
        float left = carrier.getCenterX() - CARD_WIDTH / 2f;
        float top = carrier.getCenterY() - height / 2f;
        int alphaByte = Math.round(alpha * 255);

        shadowPaint.setColor(SHADOW_COLOR);
        shadowPaint.setAlpha(Math.round(Color.alpha(SHADOW_COLOR) * alpha));
        cardRect.set(left + SHADOW_OFFSET, top + SHADOW_OFFSET,
                left + CARD_WIDTH + SHADOW_OFFSET, top + height + SHADOW_OFFSET);
        canvas.drawRoundRect(cardRect, CORNER_RADIUS, CORNER_RADIUS, shadowPaint);

        paperPaint.setColor(PAPER_COLOR);
        paperPaint.setAlpha(alphaByte);
        cardRect.set(left, top, left + CARD_WIDTH, top + height);
        canvas.drawRoundRect(cardRect, CORNER_RADIUS, CORNER_RADIUS, paperPaint);

        // Esquina doblada
        foldPaint.setColor(FOLD_COLOR);
        foldPaint.setAlpha(alphaByte);
        foldPath.rewind();
        foldPath.moveTo(left + CARD_WIDTH - FOLD_SIZE, top + height);
        foldPath.lineTo(left + CARD_WIDTH, top + height - FOLD_SIZE);
        foldPath.lineTo(left + CARD_WIDTH - FOLD_SIZE, top + height - FOLD_SIZE);
        foldPath.close();
        canvas.drawPath(foldPath, foldPaint);

        textPaint.setColor(INK_COLOR);
        textPaint.setAlpha(alphaByte);
        int saveCount = canvas.save();
        canvas.translate(left + CARD_PADDING, top + CARD_PADDING);
        current.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    private static float cardHeight(StaticLayout current) {
        return current.getHeight() + 2f * CARD_PADDING;
    }

    private static StaticLayout ensureLayout() {
        String text = carrier.getText();
        if (text.isEmpty()) return null;
        if (layout != null && text.equals(layoutText)) return layout;

        int width = Math.round(CARD_WIDTH - 2f * CARD_PADDING);
        layout = StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build();
        layoutText = text;
        return layout;
    }

    private static String loadRandomNote(Context context, boolean isSpanish) {
        String[] notes = isSpanish ? SPANISH_NOTES : ENGLISH_NOTES;
        String fileName = notes[random.nextInt(notes.length)];
        if (context == null) return "HONK";
        try (InputStream in = context.getAssets().open(NOTES_DIRECTORY + fileName)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) >= 0 && out.size() < MAX_NOTE_CHARS * 4) {
                out.write(buffer, 0, read);
            }
            String text = new String(out.toByteArray(), StandardCharsets.UTF_8)
                    .replace(BYTE_ORDER_MARK, "").trim();
            return text.length() > MAX_NOTE_CHARS ? text.substring(0, MAX_NOTE_CHARS) : text;
        } catch (IOException e) {
            Log.w(TAG, "No se pudo leer la nota " + fileName, e);
            return "HONK";
        }
    }
}
