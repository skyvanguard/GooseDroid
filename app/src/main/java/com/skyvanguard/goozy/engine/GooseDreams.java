package com.skyvanguard.goozy.engine;

import android.graphics.*;
import com.skyvanguard.goozy.PetNeeds;
import com.skyvanguard.goozy.PetPersonality;
import com.skyvanguard.goozy.GooseEvolution;
import com.skyvanguard.goozy.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sistema de sueños del ganso.
 * Muestra burbujas de pensamiento con contenido basado en las experiencias del día.
 */
public class GooseDreams {

    private static final String TAG = "GooseDreams";

    // ============== TIPOS DE SUEÑOS ==============

    public enum DreamType {
        FOOD("Dreaming of food...", new String[]{"BREAD", "CORN", "FISH", "YUM", "NOM NOM"}),
        PLAY("Dreaming of playing...", new String[]{"BALL", "RUN", "FUN", "WHEEE", "ZOOM"}),
        LOVE("Dreaming of pets...", new String[]{"<3", "LOVE", "PETS", "WARM", "HAPPY"}),
        ADVENTURE("Dreaming of adventure...", new String[]{"EXPLORE", "RUN", "FLY?", "WORLD", "GO!"}),
        MISCHIEF("Dreaming of chaos...", new String[]{"HONK", "STEAL", "CHAOS", ">:)", "EVIL"}),
        NIGHTMARE("Having a nightmare...", new String[]{"SCARY", "NO!", "HELP", "!!!", "EEK"}),
        MEMORY("Remembering today...", new String[]{"...", "HMM", "OH!", "AH", "..."}),
        PEACEFUL("Peaceful dreams...", new String[]{"ZZZ", "...", "~~~", "Z", "zzz"}),
        FLYING("Dreaming of flying...", new String[]{"FLY!", "WINGS", "SKY", "CLOUDS", "FREE"}),
        WATER("Dreaming of water...", new String[]{"SPLASH", "SWIM", "POND", "QUACK?", "WATER"});

        public final String description;
        public final String[] symbols;

        DreamType(String description, String[] symbols) {
            this.description = description;
            this.symbols = symbols;
        }
    }

    // ============== BURBUJA DE SUEÑO ==============

    public static class DreamBubble {
        public float x, y;
        public float alpha = 0f;
        public float scale = 0.5f;
        public String content;
        public float lifetime;
        public float maxLifetime;
        public boolean isThought; // true = pensamiento, false = ZZZ

        public DreamBubble(float x, float y, String content, float lifetime) {
            this.x = x;
            this.y = y;
            this.content = content;
            this.lifetime = lifetime;
            this.maxLifetime = lifetime;
            this.isThought = !content.contains("Z");
        }

        public void update(float deltaTime) {
            lifetime -= deltaTime;

            // Fade in/out
            float progress = 1f - (lifetime / maxLifetime);
            if (progress < 0.2f) {
                alpha = progress / 0.2f;
                scale = 0.5f + (progress / 0.2f) * 0.5f;
            } else if (progress > 0.8f) {
                alpha = (1f - progress) / 0.2f;
            } else {
                alpha = 1f;
                scale = 1f;
            }

            // Float upward slowly
            y -= 10f * deltaTime;
        }

        public boolean isExpired() {
            return lifetime <= 0;
        }
    }

    // ============== ESTADO ==============

    private static boolean isDreaming = false;
    private static DreamType currentDreamType = DreamType.PEACEFUL;
    private static List<DreamBubble> bubbles = new ArrayList<>();
    private static float nextBubbleTimer = 0;
    private static double dreamStartTime = 0;
    private static Random random = new Random();

    // Memoria del día para influir en sueños
    private static int foodEatenToday = 0;
    private static int petsReceivedToday = 0;
    private static int gamesPlayedToday = 0;
    private static int trollsDoneToday = 0;
    private static boolean hadScaryExperience = false;

    // ============== CONTROL ==============

    /**
     * Iniciar a soñar.
     */
    public static void startDreaming(Vec2 goosePosition) {
        isDreaming = true;
        dreamStartTime = Time.time;
        bubbles.clear();
        currentDreamType = chooseDreamType();
        nextBubbleTimer = 0.5f;
    }

    /**
     * Dejar de soñar.
     */
    public static void stopDreaming() {
        isDreaming = false;
        bubbles.clear();
    }

    /**
     * Elegir tipo de sueño basado en experiencias del día.
     */
    private static DreamType chooseDreamType() {
        float rand = random.nextFloat();

        // Pesadilla si tuvo experiencia aterradora o está muy cansado
        if (hadScaryExperience && rand < 0.2f) {
            return DreamType.NIGHTMARE;
        }

        // Sueños basados en actividades del día
        int totalActivities = foodEatenToday + petsReceivedToday + gamesPlayedToday + trollsDoneToday;

        if (totalActivities == 0) {
            return DreamType.PEACEFUL;
        }

        // Probabilidades basadas en actividades
        float foodWeight = foodEatenToday / (float) Math.max(1, totalActivities);
        float petWeight = petsReceivedToday / (float) Math.max(1, totalActivities);
        float playWeight = gamesPlayedToday / (float) Math.max(1, totalActivities);
        float trollWeight = trollsDoneToday / (float) Math.max(1, totalActivities);

        if (rand < foodWeight) return DreamType.FOOD;
        if (rand < foodWeight + petWeight) return DreamType.LOVE;
        if (rand < foodWeight + petWeight + playWeight) return DreamType.PLAY;
        if (rand < foodWeight + petWeight + playWeight + trollWeight) return DreamType.MISCHIEF;

        // Sueños según personalidad
        if (PetPersonality.get().playfulness > 60 && rand < 0.3f) return DreamType.ADVENTURE;
        if (PetPersonality.get().bravery > 60 && rand < 0.2f) return DreamType.FLYING;

        // Por defecto, sueños pacíficos
        return random.nextFloat() < 0.5f ? DreamType.PEACEFUL : DreamType.MEMORY;
    }

    // ============== UPDATE ==============

    /**
     * Actualizar sistema de sueños.
     */
    public static void update(float deltaTime, Vec2 goosePosition) {
        if (!isDreaming) return;

        // Actualizar burbujas existentes
        for (DreamBubble bubble : bubbles) {
            bubble.update(deltaTime);
        }

        // Remover burbujas expiradas
        bubbles.removeIf(DreamBubble::isExpired);

        // Crear nuevas burbujas
        nextBubbleTimer -= deltaTime;
        if (nextBubbleTimer <= 0) {
            spawnDreamBubble(goosePosition);
            nextBubbleTimer = 1.5f + random.nextFloat() * 2f;

            // Ocasionalmente cambiar tipo de sueño
            if (random.nextFloat() < 0.1f) {
                currentDreamType = chooseDreamType();
            }
        }
    }

    /**
     * Crear una burbuja de sueño.
     */
    private static void spawnDreamBubble(Vec2 goosePosition) {
        String[] symbols = currentDreamType.symbols;
        String content = symbols[random.nextInt(symbols.length)];

        // Posición cerca de la cabeza del ganso
        float offsetX = -30f + random.nextFloat() * 60f;
        float offsetY = -80f - random.nextFloat() * 40f;

        DreamBubble bubble = new DreamBubble(
                goosePosition.x + offsetX,
                goosePosition.y + offsetY,
                content,
                3f + random.nextFloat() * 2f
        );

        bubbles.add(bubble);

        // Limitar cantidad de burbujas
        if (bubbles.size() > 5) {
            bubbles.remove(0);
        }
    }

    // ============== RENDER ==============

    /**
     * Renderizar burbujas de sueño.
     */
    public static void render(Canvas canvas) {
        if (!isDreaming || bubbles.isEmpty()) return;

        Paint bubblePaint = new Paint();
        bubblePaint.setAntiAlias(true);

        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextAlign(Paint.Align.CENTER);

        for (DreamBubble bubble : bubbles) {
            // Color de burbuja según tipo de sueño
            int bubbleColor = getDreamColor(currentDreamType);
            bubblePaint.setColor(bubbleColor);
            bubblePaint.setAlpha((int) (bubble.alpha * 200));

            // Dibujar burbuja
            float radius = 25f * bubble.scale;
            canvas.drawCircle(bubble.x, bubble.y, radius, bubblePaint);

            // Pequeñas burbujas conectando a la cabeza
            if (bubble.isThought) {
                bubblePaint.setAlpha((int) (bubble.alpha * 150));
                canvas.drawCircle(bubble.x + 15, bubble.y + 20, 8 * bubble.scale, bubblePaint);
                canvas.drawCircle(bubble.x + 25, bubble.y + 35, 5 * bubble.scale, bubblePaint);
            }

            // Texto del sueño
            textPaint.setColor(0xFF000000);
            textPaint.setAlpha((int) (bubble.alpha * 255));
            textPaint.setTextSize(14f * bubble.scale);
            canvas.drawText(bubble.content, bubble.x, bubble.y + 5, textPaint);
        }

        // Mostrar descripción del sueño
        if (bubbles.size() > 0) {
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setAlpha(150);
            textPaint.setTextSize(12f);
            // canvas.drawText(currentDreamType.description, bubbles.get(0).x, bubbles.get(0).y - 40, textPaint);
        }
    }

    /**
     * Obtener color de burbuja según tipo de sueño.
     */
    private static int getDreamColor(DreamType type) {
        switch (type) {
            case FOOD:
                return 0xFFFFE4B5; // Moccasin
            case PLAY:
                return 0xFFADD8E6; // Light blue
            case LOVE:
                return 0xFFFFB6C1; // Light pink
            case ADVENTURE:
                return 0xFF98FB98; // Pale green
            case MISCHIEF:
                return 0xFFDDA0DD; // Plum
            case NIGHTMARE:
                return 0xFF808080; // Gray
            case MEMORY:
                return 0xFFE6E6FA; // Lavender
            case PEACEFUL:
                return 0xFFFFFFFF; // White
            case FLYING:
                return 0xFF87CEEB; // Sky blue
            case WATER:
                return 0xFF00CED1; // Dark turquoise
            default:
                return 0xFFFFFFFF;
        }
    }

    // ============== REGISTRO DE EXPERIENCIAS ==============

    public static void recordFoodEaten() {
        foodEatenToday++;
    }

    public static void recordPetReceived() {
        petsReceivedToday++;
    }

    public static void recordGamePlayed() {
        gamesPlayedToday++;
    }

    public static void recordTrollDone() {
        trollsDoneToday++;
    }

    public static void recordScaryExperience() {
        hadScaryExperience = true;
    }

    /**
     * Resetear memoria del día (llamar a medianoche o nuevo día).
     */
    public static void resetDailyMemory() {
        foodEatenToday = 0;
        petsReceivedToday = 0;
        gamesPlayedToday = 0;
        trollsDoneToday = 0;
        hadScaryExperience = false;
    }

    // ============== GETTERS ==============

    public static boolean isDreaming() {
        return isDreaming;
    }

    public static DreamType getCurrentDreamType() {
        return currentDreamType;
    }

    public static String getDreamDescription() {
        return currentDreamType.description;
    }
}
