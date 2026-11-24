package com.skyvanguard.goozy.brain;

import com.skyvanguard.goozy.AtomicFiles;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Memoria a largo plazo del ganso: datos sueltos que decidió recordar y las
 * entradas de su diario. Se guarda como JSON en un archivo propio.
 *
 * Todos los métodos son seguros entre hilos: el cerebro escribe desde el hilo
 * del backend y la pantalla lee desde el principal.
 */
public class BrainMemory {
    static final int MAX_FACTS = 40;
    static final int MAX_DIARY_ENTRIES = 60;
    private static final float SAME_FACT_SIMILARITY = 0.6f;
    private static final int MAX_FILLER_WORD_LENGTH = 2;
    private static final int MAX_FILE_BYTES = 512 * 1024;

    /** Una entrada del diario. Inmutable. */
    public static final class DiaryEntry {
        /** Fecha local en formato AAAA-MM-DD. */
        public final String date;
        public final String text;

        public DiaryEntry(String date, String text) {
            this.date = date;
            this.text = text;
        }
    }

    private final File file;
    private final List<String> facts = new ArrayList<>();
    private final List<DiaryEntry> diary = new ArrayList<>();
    private boolean isLoaded = false;

    public BrainMemory(File file) {
        this.file = file;
    }

    public synchronized List<String> getFacts() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(facts));
    }

    public synchronized List<DiaryEntry> getDiary() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(diary));
    }

    public synchronized boolean hasDiaryEntryFor(String date) {
        ensureLoaded();
        for (DiaryEntry entry : diary) {
            if (entry.date.equals(date)) return true;
        }
        return false;
    }

    /**
     * Guarda un dato. Los repetidos no se duplican: pasan a ser el más reciente.
     * Cuando se llena, se olvida el más viejo.
     *
     * @return true si la memoria cambió
     */
    public synchronized boolean remember(String fact) {
        if (fact == null) return false;
        String clean = fact.trim();
        if (clean.isEmpty()) return false;
        ensureLoaded();

        // La versión nueva reemplaza a las parecidas: los modelos repiten lo que
        // ya saben con otras palabras
        for (int i = facts.size() - 1; i >= 0; i--) {
            if (isSameFact(facts.get(i), clean)) {
                facts.remove(i);
            }
        }
        facts.add(clean);
        while (facts.size() > MAX_FACTS) {
            facts.remove(0);
        }
        return save();
    }

    public synchronized boolean forget(String fact) {
        ensureLoaded();
        boolean isRemoved = facts.remove(fact);
        return isRemoved && save();
    }

    /** Una entrada por día: la del mismo día reemplaza a la anterior. */
    public synchronized boolean writeDiary(String date, String text) {
        if (date == null || text == null || text.trim().isEmpty()) return false;
        ensureLoaded();

        for (int i = diary.size() - 1; i >= 0; i--) {
            if (diary.get(i).date.equals(date)) {
                diary.remove(i);
            }
        }
        diary.add(new DiaryEntry(date, text.trim()));
        while (diary.size() > MAX_DIARY_ENTRIES) {
            diary.remove(0);
        }
        return save();
    }

    public synchronized boolean clear() {
        facts.clear();
        diary.clear();
        isLoaded = true;
        return save();
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /**
     * Dos datos son el mismo si comparten casi todas sus palabras con contenido
     * (se ignoran las de una o dos letras, como artículos y pronombres, pero no
     * los números).
     */
    static boolean isSameFact(String a, String b) {
        Set<String> wordsA = contentWords(a);
        Set<String> wordsB = contentWords(b);
        if (wordsA.isEmpty() || wordsB.isEmpty()) {
            return normalize(a).equals(normalize(b));
        }
        Set<String> common = new HashSet<>(wordsA);
        common.retainAll(wordsB);
        Set<String> all = new HashSet<>(wordsA);
        all.addAll(wordsB);
        return (float) common.size() / all.size() >= SAME_FACT_SIMILARITY;
    }

    private static Set<String> contentWords(String text) {
        Set<String> words = new HashSet<>();
        for (String word : normalize(text).split(" ")) {
            // Los números siempre cuentan: "tiene 3 gatos" no es "tiene 30 gatos"
            if (word.length() > MAX_FILLER_WORD_LENGTH || containsDigit(word)) words.add(word);
        }
        return words;
    }

    private static boolean containsDigit(String word) {
        for (int i = 0; i < word.length(); i++) {
            if (Character.isDigit(word.charAt(i))) return true;
        }
        return false;
    }

    private void ensureLoaded() {
        if (isLoaded) return;
        isLoaded = true;
        if (!file.exists() || file.length() == 0 || file.length() > MAX_FILE_BYTES) return;

        try {
            JSONObject root = new JSONObject(readFile());
            JSONArray savedFacts = root.optJSONArray("facts");
            for (int i = 0; savedFacts != null && i < savedFacts.length(); i++) {
                String fact = savedFacts.optString(i, "").trim();
                if (!fact.isEmpty()) facts.add(fact);
            }
            JSONArray savedDiary = root.optJSONArray("diary");
            for (int i = 0; savedDiary != null && i < savedDiary.length(); i++) {
                JSONObject entry = savedDiary.optJSONObject(i);
                if (entry == null) continue;
                String date = entry.optString("date", "");
                String text = entry.optString("text", "");
                if (!date.isEmpty() && !text.isEmpty()) diary.add(new DiaryEntry(date, text));
            }
        } catch (IOException | JSONException e) {
            // Archivo ilegible: se arranca con la memoria vacía y el próximo
            // guardado lo reemplaza.
            facts.clear();
            diary.clear();
        }
    }

    private String readFile() throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) file.length()];
            int offset = 0;
            while (offset < buffer.length) {
                int read = in.read(buffer, offset, buffer.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        }
    }

    private boolean save() {
        try {
            JSONObject root = new JSONObject();
            root.put("facts", new JSONArray(facts));
            JSONArray savedDiary = new JSONArray();
            for (DiaryEntry entry : diary) {
                JSONObject object = new JSONObject();
                object.put("date", entry.date);
                object.put("text", entry.text);
                savedDiary.put(object);
            }
            root.put("diary", savedDiary);

            AtomicFiles.write(file, root.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | JSONException e) {
            return false;
        }
    }
}
