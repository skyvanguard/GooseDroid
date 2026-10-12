package com.skyvanguard.goozy.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.skyvanguard.goozy.GooseEvolution;
import com.skyvanguard.goozy.PetNeeds;
import com.skyvanguard.goozy.PetPersonality;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

/**
 * Advanced Local AI System for intelligent goose thoughts and personality.
 * 100% on-device - no network dependencies.
 *
 * Features:
 * - Context-aware thought generation
 * - Memory system for recent events
 * - Personality-driven responses
 * - Time and evolution-based behaviors
 * - Adaptive mood system
 * - Template-based text generation
 *
 * Los textos salen de GoosePhrases según el idioma elegido con setLanguage (español por defecto).
 */
public class GooseLLM {

    private static final String TAG = "GooseLLM";
    private static final String PREFS_NAME = "goose_llm_memory";

    // ============== CONFIGURATION ==============

    private static boolean isEnabled = true;
    private static Context appContext;
    private static final Random random = new Random();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Idioma de las frases por plantilla; volatile porque se puede cambiar desde la UI
    private static volatile GoosePhrases.Language language = GoosePhrases.DEFAULT_LANGUAGE;

    // ============== MEMORY SYSTEM ==============

    // Short-term memory (session)
    private static final Queue<MemoryEvent> shortTermMemory = new LinkedList<>();
    private static final int MAX_SHORT_TERM = 50;

    // Long-term memory stats (persisted)
    private static int totalPets = 0;
    private static int totalFeeds = 0;
    private static int totalPlays = 0;
    private static int consecutiveDaysActive = 0;
    private static long lastActiveDay = 0;
    private static String favoriteTimeOfDay = "unknown";
    private static int morningInteractions = 0;
    private static int afternoonInteractions = 0;
    private static int eveningInteractions = 0;
    private static int nightInteractions = 0;

    // Current state
    private static float currentMood = 0.5f; // -1 to 1
    private static float excitement = 0f; // 0 to 1
    private static float boredom = 0f; // 0 to 1
    private static long lastInteractionTime = 0;
    private static String lastThought = "";
    private static String lastAction = "";

    // ============== MEMORY EVENT ==============

    public static class MemoryEvent {
        public String type;
        public String detail;
        public long timestamp;
        public float emotionalImpact; // -1 to 1

        public MemoryEvent(String type, String detail, float impact) {
            this.type = type;
            this.detail = detail;
            this.timestamp = System.currentTimeMillis();
            this.emotionalImpact = impact;
        }
    }

    // ============== CALLBACKS ==============

    @FunctionalInterface
    public interface ThoughtCallback {
        void onThoughtGenerated(String thought);
        default void onError(String error) {
            Log.w(TAG, "LLM error: " + error);
        }
    }

    // ============== INITIALIZATION ==============

    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
        loadMemory();
        updateActiveStreak();
    }

    private static void loadMemory() {
        if (appContext == null) return;

        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        totalPets = prefs.getInt("total_pets", 0);
        totalFeeds = prefs.getInt("total_feeds", 0);
        totalPlays = prefs.getInt("total_plays", 0);
        consecutiveDaysActive = prefs.getInt("consecutive_days", 0);
        lastActiveDay = prefs.getLong("last_active_day", 0);
        favoriteTimeOfDay = prefs.getString("favorite_time", "unknown");
        morningInteractions = prefs.getInt("morning_interactions", 0);
        afternoonInteractions = prefs.getInt("afternoon_interactions", 0);
        eveningInteractions = prefs.getInt("evening_interactions", 0);
        nightInteractions = prefs.getInt("night_interactions", 0);
    }

    private static void saveMemory() {
        if (appContext == null) return;

        SharedPreferences.Editor editor = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        editor.putInt("total_pets", totalPets);
        editor.putInt("total_feeds", totalFeeds);
        editor.putInt("total_plays", totalPlays);
        editor.putInt("consecutive_days", consecutiveDaysActive);
        editor.putLong("last_active_day", lastActiveDay);
        editor.putString("favorite_time", favoriteTimeOfDay);
        editor.putInt("morning_interactions", morningInteractions);
        editor.putInt("afternoon_interactions", afternoonInteractions);
        editor.putInt("evening_interactions", eveningInteractions);
        editor.putInt("night_interactions", nightInteractions);
        editor.apply();
    }

    private static void updateActiveStreak() {
        // Día local, no UTC: la racha cambia a medianoche del usuario
        long nowMs = System.currentTimeMillis();
        long today = (nowMs + java.util.TimeZone.getDefault().getOffset(nowMs))
                / (24 * 60 * 60 * 1000);
        if (today == lastActiveDay + 1) {
            consecutiveDaysActive++;
        } else if (today != lastActiveDay) {
            consecutiveDaysActive = 1;
        }
        lastActiveDay = today;
        saveMemory();
    }

    // ============== CONFIGURATION ==============

    public static void setEnabled(boolean enabled) {
        isEnabled = enabled;
    }

    public static boolean isEnabled() {
        return isEnabled;
    }

    /**
     * Elige el idioma de las frases a partir de un nombre libre ("español", "Spanish",
     * "es", "english"...). Lo que empieza con "es" o "spa" es español; el resto, inglés.
     */
    public static void setLanguage(String languageName) {
        language = GoosePhrases.parseLanguage(languageName);
    }

    public static boolean isSpanish() {
        return language == GoosePhrases.Language.SPANISH;
    }

    // ============== MEMORY MANAGEMENT ==============

    public static void recordEvent(String type, String detail, float emotionalImpact) {
        MemoryEvent event = new MemoryEvent(type, detail, emotionalImpact);
        shortTermMemory.offer(event);

        while (shortTermMemory.size() > MAX_SHORT_TERM) {
            shortTermMemory.poll();
        }

        // Update mood based on emotional impact
        currentMood = Math.max(-1, Math.min(1, currentMood + emotionalImpact * 0.2f));

        // Update excitement
        excitement = Math.min(1, excitement + Math.abs(emotionalImpact) * 0.3f);

        // Reset boredom on interaction
        boredom = 0;
        lastInteractionTime = System.currentTimeMillis();

        // Track time of day
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 6 && hour < 12) morningInteractions++;
        else if (hour >= 12 && hour < 18) afternoonInteractions++;
        else if (hour >= 18 && hour < 22) eveningInteractions++;
        else nightInteractions++;

        // Update favorite time
        updateFavoriteTime();

        // Track specific actions
        if (type.equals("pet")) totalPets++;
        else if (type.equals("feed")) totalFeeds++;
        else if (type.equals("play")) totalPlays++;

        lastAction = type;
        saveMemory();
    }

    private static void updateFavoriteTime() {
        int max = Math.max(Math.max(morningInteractions, afternoonInteractions),
                Math.max(eveningInteractions, nightInteractions));

        if (max == morningInteractions) favoriteTimeOfDay = "morning";
        else if (max == afternoonInteractions) favoriteTimeOfDay = "afternoon";
        else if (max == eveningInteractions) favoriteTimeOfDay = "evening";
        else favoriteTimeOfDay = "night";
    }

    // Update boredom over time (call periodically)
    public static void updateBoredom(float deltaSeconds) {
        long timeSinceInteraction = System.currentTimeMillis() - lastInteractionTime;
        float minutesSinceInteraction = timeSinceInteraction / 60000f;

        // Boredom increases after 2 minutes of no interaction
        if (minutesSinceInteraction > 2) {
            boredom = Math.min(1, boredom + deltaSeconds * 0.01f);
        }

        // Excitement decays
        excitement = Math.max(0, excitement - deltaSeconds * 0.05f);

        // Mood slowly returns to neutral
        if (currentMood > 0) currentMood = Math.max(0, currentMood - deltaSeconds * 0.01f);
        else if (currentMood < 0) currentMood = Math.min(0, currentMood + deltaSeconds * 0.01f);
    }

    // ============== THOUGHT GENERATION ==============

    public static void generateThought(Context context, ThoughtCallback callback) {
        if (!isEnabled) return;

        String thought = generateLocalThought();
        lastThought = thought;

        if (callback != null) {
            callback.onThoughtGenerated(thought);
        }
    }

    public static void generateResponse(String userAction, ThoughtCallback callback) {
        if (!isEnabled) return;

        String response = generateLocalResponse(userAction);
        lastThought = response;

        if (callback != null) {
            callback.onThoughtGenerated(response);
        }
    }

    // ============== LOCAL AI ENGINE ==============

    private static String generateLocalThought() {
        // Build context scores for different thought categories
        Map<String, Float> categoryScores = new HashMap<>();

        // === CRITICAL NEEDS (highest priority) ===
        if (PetNeeds.get().hunger > 85) {
            categoryScores.put("starving", 1.0f);
        } else if (PetNeeds.get().hunger > 70) {
            categoryScores.put("hungry", 0.8f);
        } else if (PetNeeds.get().hunger > 50) {
            categoryScores.put("peckish", 0.4f);
        }

        if (PetNeeds.get().energy < 15) {
            categoryScores.put("exhausted", 1.0f);
        } else if (PetNeeds.get().energy < 30) {
            categoryScores.put("tired", 0.7f);
        } else if (PetNeeds.get().energy < 50) {
            categoryScores.put("sleepy", 0.3f);
        }

        if (PetNeeds.get().happiness < 20) {
            categoryScores.put("sad", 0.9f);
        } else if (PetNeeds.get().happiness < 40) {
            categoryScores.put("lonely", 0.5f);
        }

        // === TIME-BASED ===
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
        int month = Calendar.getInstance().get(Calendar.MONTH);

        // Morning thoughts
        if (hour >= 5 && hour < 8) {
            categoryScores.put("dawn", 0.4f + random.nextFloat() * 0.3f);
        } else if (hour >= 8 && hour < 11) {
            categoryScores.put("morning", 0.3f + random.nextFloat() * 0.2f);
        }

        // Lunch time
        if (hour >= 11 && hour < 14) {
            categoryScores.put("lunchtime", 0.5f + random.nextFloat() * 0.2f);
        }

        // Afternoon
        if (hour >= 14 && hour < 17) {
            categoryScores.put("afternoon", 0.2f + random.nextFloat() * 0.2f);
        }

        // Evening
        if (hour >= 17 && hour < 21) {
            categoryScores.put("evening", 0.3f + random.nextFloat() * 0.2f);
        }

        // Night
        if (hour >= 21 || hour < 5) {
            categoryScores.put("night", 0.5f + random.nextFloat() * 0.3f);
        }

        // Weekend
        if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
            categoryScores.put("weekend", 0.3f);
        }

        // Special days
        if (dayOfMonth == 1) categoryScores.put("newmonth", 0.5f);
        if (month == Calendar.DECEMBER && dayOfMonth >= 20) categoryScores.put("holiday", 0.6f);
        if (month == Calendar.OCTOBER && dayOfMonth == 31) categoryScores.put("halloween", 0.8f);

        // === EVOLUTION STAGE ===
        GooseEvolution.Stage stage = GooseEvolution.getCurrentStage();
        categoryScores.put("evolution_" + stage.name().toLowerCase(), 0.3f + random.nextFloat() * 0.2f);

        // === PERSONALITY ===
        if (PetPersonality.get().playfulness > 70) {
            categoryScores.put("playful", PetPersonality.get().playfulness / 100f * 0.6f);
        }
        if (PetPersonality.get().mischief > 70) {
            categoryScores.put("mischievous", PetPersonality.get().mischief / 100f * 0.5f);
        }
        if (PetPersonality.get().affection > 70) {
            categoryScores.put("affectionate", PetPersonality.get().affection / 100f * 0.5f);
        }
        if (PetPersonality.get().bravery > 70) {
            categoryScores.put("brave", PetPersonality.get().bravery / 100f * 0.4f);
        }

        // === MOOD ===
        if (currentMood > 0.5f) {
            categoryScores.put("veryhappy", currentMood * 0.7f);
        } else if (currentMood > 0.2f) {
            categoryScores.put("happy", currentMood * 0.5f);
        } else if (currentMood < -0.5f) {
            categoryScores.put("verysad", Math.abs(currentMood) * 0.7f);
        } else if (currentMood < -0.2f) {
            categoryScores.put("sad", Math.abs(currentMood) * 0.5f);
        }

        // === BOREDOM ===
        if (boredom > 0.7f) {
            categoryScores.put("verybored", boredom * 0.8f);
        } else if (boredom > 0.4f) {
            categoryScores.put("bored", boredom * 0.5f);
        }

        // === EXCITEMENT ===
        if (excitement > 0.7f) {
            categoryScores.put("excited", excitement * 0.6f);
        }

        // === MEMORIES ===
        if (totalPets > 100) {
            categoryScores.put("loved", 0.3f);
        }
        if (consecutiveDaysActive > 7) {
            categoryScores.put("loyal", 0.4f);
        }
        if (consecutiveDaysActive > 30) {
            categoryScores.put("bestfriends", 0.5f);
        }

        // Check recent events
        MemoryEvent recentEvent = getRecentEvent(10000); // Last 10 seconds
        if (recentEvent != null) {
            categoryScores.put("reaction_" + recentEvent.type, 0.6f);
        }

        // === RANDOM AMBIENT THOUGHTS ===
        if (categoryScores.isEmpty() || random.nextFloat() < 0.3f) {
            categoryScores.put("ambient", 0.3f + random.nextFloat() * 0.3f);
        }

        // Select category based on weighted random
        String selectedCategory = selectWeightedCategory(categoryScores);

        // Generate thought from category
        return getThoughtForCategory(selectedCategory);
    }

    private static MemoryEvent getRecentEvent(long maxAgeMs) {
        long now = System.currentTimeMillis();
        MemoryEvent recent = null;

        for (MemoryEvent event : shortTermMemory) {
            if (now - event.timestamp <= maxAgeMs) {
                recent = event;
            }
        }

        return recent;
    }

    private static String selectWeightedCategory(Map<String, Float> scores) {
        if (scores.isEmpty()) return "ambient";

        float totalWeight = 0;
        for (float weight : scores.values()) {
            totalWeight += weight;
        }

        float randomValue = random.nextFloat() * totalWeight;
        float cumulative = 0;

        for (Map.Entry<String, Float> entry : scores.entrySet()) {
            cumulative += entry.getValue();
            if (randomValue <= cumulative) {
                return entry.getKey();
            }
        }

        return scores.keySet().iterator().next();
    }

    // ============== THOUGHT TEMPLATES ==============

    private static String getThoughtForCategory(String category) {
        String[] options = GoosePhrases.get(category, language);
        // Categoría sin frases propias (p. ej. "reaction_sleep"): pensamiento suelto, como antes
        if (options == null) {
            return getAmbientThought();
        }
        return pickRandom(options);
    }

    private static String getAmbientThought() {
        return pickPhrase(GoosePhrases.AMBIENT);
    }

    // ============== RESPONSE GENERATION ==============

    private static String generateLocalResponse(String action) {
        String actionLower = action.toLowerCase();

        // Pet/touch responses
        if (actionLower.contains("pet") || actionLower.contains("acarici") || actionLower.contains("touch")) {
            recordEvent("pet", action, 0.3f);

            if (totalPets > 100 && random.nextFloat() < 0.2f) {
                return pickPhrase(GoosePhrases.RESPONSE_PET_BEST_FRIEND);
            }

            if (PetNeeds.get().happiness > 80) {
                return pickPhrase(GoosePhrases.RESPONSE_PET_HIGH);
            } else if (PetNeeds.get().happiness > 50) {
                return pickPhrase(GoosePhrases.RESPONSE_PET_MID);
            } else {
                return pickPhrase(GoosePhrases.RESPONSE_PET_LOW);
            }
        }

        // Feed responses
        if (actionLower.contains("feed") || actionLower.contains("aliment") || actionLower.contains("food")) {
            recordEvent("feed", action, 0.4f);

            if (PetNeeds.get().hunger > 70) {
                return pickPhrase(GoosePhrases.RESPONSE_FEED_STARVING);
            } else if (PetNeeds.get().hunger > 40) {
                return pickPhrase(GoosePhrases.RESPONSE_FEED_HUNGRY);
            } else {
                return pickPhrase(GoosePhrases.RESPONSE_FEED_FULL);
            }
        }

        // Play responses
        if (actionLower.contains("play") || actionLower.contains("jugar") || actionLower.contains("game")) {
            recordEvent("play", action, 0.5f);

            if (PetNeeds.get().energy > 70) {
                return pickPhrase(GoosePhrases.RESPONSE_PLAY_HIGH);
            } else if (PetNeeds.get().energy > 40) {
                return pickPhrase(GoosePhrases.RESPONSE_PLAY_MID);
            } else {
                return pickPhrase(GoosePhrases.RESPONSE_PLAY_TIRED);
            }
        }

        // Drag responses
        if (actionLower.contains("drag") || actionLower.contains("arrastr") || actionLower.contains("move")) {
            recordEvent("drag", action, -0.1f);
            return pickPhrase(GoosePhrases.RESPONSE_DRAG);
        }

        // Talk/greet responses
        if (actionLower.contains("hello") || actionLower.contains("hola") || actionLower.contains("hi")) {
            recordEvent("greet", action, 0.2f);
            return pickPhrase(GoosePhrases.RESPONSE_GREET);
        }

        // Goodbye responses
        if (actionLower.contains("bye") || actionLower.contains("adios") || actionLower.contains("leave")) {
            recordEvent("farewell", action, -0.2f);
            return pickPhrase(GoosePhrases.RESPONSE_FAREWELL);
        }

        // Default response
        recordEvent("interact", action, 0.1f);
        return pickPhrase(GoosePhrases.RESPONSE_DEFAULT);
    }

    // ============== UTILITIES ==============

    private static String pickRandom(String... options) {
        return options[random.nextInt(options.length)];
    }

    /** Frase al azar de una clave fija de GoosePhrases en el idioma activo. */
    private static String pickPhrase(String key) {
        return pickRandom(requirePhrases(key));
    }

    /** Frases de una clave fija; su ausencia es un error de programación (lo cubre GoosePhrasesTest). */
    private static String[] requirePhrases(String key) {
        String[] options = GoosePhrases.get(key, language);
        if (options == null || options.length == 0) {
            throw new IllegalStateException("GoosePhrases sin frases para '" + key + "' en " + language);
        }
        return options;
    }

    // ============== GETTERS FOR UI ==============

    public static float getCurrentMood() {
        return currentMood;
    }

    public static float getExcitement() {
        return excitement;
    }

    public static float getBoredom() {
        return boredom;
    }

    public static int getTotalPets() {
        return totalPets;
    }

    public static int getTotalFeeds() {
        return totalFeeds;
    }

    public static int getTotalPlays() {
        return totalPlays;
    }

    public static int getConsecutiveDays() {
        return consecutiveDaysActive;
    }

    public static String getFavoriteTime() {
        return favoriteTimeOfDay;
    }

    public static String getLastThought() {
        return lastThought;
    }

    // ============== SPECIAL THOUGHTS ==============

    /**
     * Generate a special thought for milestones
     */
    public static String getMilestoneThought() {
        String thought = claimMilestone("pets", totalPets, 1000, GoosePhrases.MILESTONE_PETS_1000);
        if (thought == null) thought = claimMilestone("pets", totalPets, 500, GoosePhrases.MILESTONE_PETS_500);
        if (thought == null) thought = claimMilestone("pets", totalPets, 100, GoosePhrases.MILESTONE_PETS_100);
        if (thought == null) thought = claimMilestone("feeds", totalFeeds, 100, GoosePhrases.MILESTONE_FEEDS_100);
        if (thought == null) thought = claimMilestone("plays", totalPlays, 100, GoosePhrases.MILESTONE_PLAYS_100);
        if (thought == null) thought = claimMilestone("days", consecutiveDaysActive, 365, GoosePhrases.MILESTONE_DAYS_365);
        if (thought == null) thought = claimMilestone("days", consecutiveDaysActive, 100, GoosePhrases.MILESTONE_DAYS_100);
        if (thought == null) thought = claimMilestone("days", consecutiveDaysActive, 30, GoosePhrases.MILESTONE_DAYS_30);
        if (thought == null) thought = claimMilestone("days", consecutiveDaysActive, 7, GoosePhrases.MILESTONE_DAYS_7);
        return thought;
    }

    /**
     * Devuelve el mensaje del hito (clave de GoosePhrases, una sola frase) la primera vez
     * que se alcanza el umbral y lo marca como mostrado, para que un hito no se repita
     * mientras el contador no cambia.
     */
    private static String claimMilestone(String counter, int value, int threshold, String phraseKey) {
        if (value < threshold || appContext == null) return null;
        String key = MILESTONE_KEY_PREFIX + counter + "_" + threshold;
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(key, false)) return null;
        prefs.edit().putBoolean(key, true).apply();
        return requirePhrases(phraseKey)[0];
    }

    private static final String MILESTONE_KEY_PREFIX = "milestone_shown_";

    /**
     * Check if favorite time matches current time
     */
    public static boolean isFavoriteTime() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        switch (favoriteTimeOfDay) {
            case "morning": return hour >= 6 && hour < 12;
            case "afternoon": return hour >= 12 && hour < 18;
            case "evening": return hour >= 18 && hour < 22;
            case "night": return hour >= 22 || hour < 6;
            default: return false;
        }
    }

    public static String getFavoriteTimeThought() {
        if (isFavoriteTime()) {
            return pickPhrase(GoosePhrases.FAVORITE_TIME);
        }
        return null;
    }
}
