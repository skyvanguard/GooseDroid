package com.skyvanguard.goozy.engine;

import android.graphics.*;
import com.skyvanguard.goozy.core.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Sistema de efectos visuales avanzados para el ganso.
 * Partículas, trails, efectos especiales, y más.
 */
public class GooseVisualEffects {

    private static final String TAG = "GooseVisualEffects";

    // ============== TIPOS DE EFECTOS ==============

    public enum EffectType {
        HEARTS,          // Corazones flotantes
        SPARKLES,        // Brillitos
        CONFETTI,        // Confetti de colores
        MUSICAL_NOTES,   // Notas musicales
        ANGER,           // Nubes de enojo
        SLEEPY,          // ZZZs
        HUNGRY,          // Platos/comida
        FOOTPRINTS,      // Huellas
        DUST,            // Polvo al correr
        SPLASH,          // Salpicaduras
        FEATHERS,        // Plumas volando
        STINK_LINES,     // Líneas de "olor"
        LOVE_BURST,      // Explosión de amor
        HONK_WAVE,       // Onda de sonido
        RAINBOW_TRAIL,   // Estela arcoíris
        GOLDEN_GLOW,     // Brillo dorado
        GHOST_FADE,      // Efecto fantasma
        DISCO_LIGHTS     // Luces disco
    }

    // ============== PARTÍCULAS ==============

    public static class Particle {
        public float x, y;
        public float vx, vy;
        public float life;
        public float maxLife;
        public float size;
        public int color;
        public float rotation;
        public float rotationSpeed;
        public EffectType type;
        public String text; // Para notas, ZZZ, etc.

        public Particle(float x, float y, EffectType type) {
            this.x = x;
            this.y = y;
            this.type = type;
            this.life = 1f;
            this.maxLife = 1f;
            this.size = 10f;
            this.rotation = 0f;
            this.rotationSpeed = 0f;
        }

        public void update(float deltaTime) {
            x += vx * deltaTime;
            y += vy * deltaTime;
            life -= deltaTime / maxLife;
            rotation += rotationSpeed * deltaTime;

            // Gravedad para algunos tipos
            switch (type) {
                case CONFETTI:
                case FEATHERS:
                    vy += 50f * deltaTime; // Caída suave
                    vx += (float)(Math.sin(life * 10) * 20) * deltaTime; // Zigzag
                    break;
                case DUST:
                case SPLASH:
                    vy += 100f * deltaTime;
                    break;
                default:
                    break;
            }
        }

        public boolean isDead() {
            return life <= 0;
        }

        public float getAlpha() {
            // Fade out
            if (life < 0.3f) {
                return life / 0.3f;
            }
            return 1f;
        }
    }

    // ============== TRAIL POINT ==============

    public static class TrailPoint {
        public float x, y;
        public float life;
        public int color;

        public TrailPoint(float x, float y, int color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.life = 1f;
        }

        public void update(float deltaTime) {
            life -= deltaTime * 2f;
        }

        public boolean isDead() {
            return life <= 0;
        }
    }

    // ============== ESTADO ==============

    private static List<Particle> particles = new ArrayList<>();
    private static List<TrailPoint> trail = new ArrayList<>();
    private static Random random = new Random();

    // Efectos activos
    private static boolean rainbowTrailActive = false;
    private static boolean goldenGlowActive = false;
    private static boolean ghostModeActive = false;
    private static boolean discoModeActive = false;

    // Contadores
    private static float footprintTimer = 0;
    private static int rainbowColorIndex = 0;
    private static float discoHue = 0;

    // Colores
    private static final int[] RAINBOW_COLORS = {
            0xFFFF0000, // Rojo
            0xFFFF7F00, // Naranja
            0xFFFFFF00, // Amarillo
            0xFF00FF00, // Verde
            0xFF0000FF, // Azul
            0xFF4B0082, // Índigo
            0xFF9400D3  // Violeta
    };

    private static final int[] CONFETTI_COLORS = {
            0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFE66D,
            0xFF95E1D3, 0xFFF38181, 0xFFAA96DA,
            0xFFFCBF49, 0xFF2EC4B6
    };

    // ============== CREACIÓN DE EFECTOS ==============

    /**
     * Crear efecto de corazones.
     */
    public static void spawnHearts(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle(x + random.nextFloat() * 40 - 20, y, EffectType.HEARTS);
            p.vx = random.nextFloat() * 60 - 30;
            p.vy = -50 - random.nextFloat() * 50;
            p.maxLife = 1.5f + random.nextFloat() * 0.5f;
            p.life = p.maxLife;
            p.size = 15 + random.nextFloat() * 10;
            p.color = 0xFFFF69B4; // Hot pink
            p.text = "♥";
            particles.add(p);
        }
    }

    /**
     * Crear brillitos/sparkles.
     */
    public static void spawnSparkles(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle(
                    x + random.nextFloat() * 60 - 30,
                    y + random.nextFloat() * 60 - 30,
                    EffectType.SPARKLES
            );
            p.vx = random.nextFloat() * 40 - 20;
            p.vy = random.nextFloat() * 40 - 20;
            p.maxLife = 0.5f + random.nextFloat() * 0.3f;
            p.life = p.maxLife;
            p.size = 5 + random.nextFloat() * 8;
            p.color = 0xFFFFFFAA; // Amarillo brillante
            p.rotationSpeed = random.nextFloat() * 360;
            particles.add(p);
        }
    }

    /**
     * Crear confetti.
     */
    public static void spawnConfetti(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle(x, y - 50, EffectType.CONFETTI);
            p.vx = random.nextFloat() * 200 - 100;
            p.vy = -100 - random.nextFloat() * 100;
            p.maxLife = 3f + random.nextFloat();
            p.life = p.maxLife;
            p.size = 8 + random.nextFloat() * 6;
            p.color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.length)];
            p.rotationSpeed = random.nextFloat() * 720 - 360;
            particles.add(p);
        }
    }

    /**
     * Crear notas musicales.
     */
    public static void spawnMusicalNotes(float x, float y) {
        String[] notes = {"♪", "♫", "♬", "♩"};
        for (int i = 0; i < 3; i++) {
            Particle p = new Particle(x + i * 15, y - 30, EffectType.MUSICAL_NOTES);
            p.vx = random.nextFloat() * 30 - 15;
            p.vy = -40 - random.nextFloat() * 20;
            p.maxLife = 2f;
            p.life = p.maxLife;
            p.size = 18;
            p.color = 0xFF333333;
            p.text = notes[random.nextInt(notes.length)];
            particles.add(p);
        }
    }

    /**
     * Crear nubes de enojo.
     */
    public static void spawnAnger(float x, float y) {
        for (int i = 0; i < 3; i++) {
            Particle p = new Particle(x + random.nextFloat() * 40 - 20, y - 40, EffectType.ANGER);
            p.vx = random.nextFloat() * 20 - 10;
            p.vy = -30 - random.nextFloat() * 20;
            p.maxLife = 1f;
            p.life = p.maxLife;
            p.size = 12;
            p.color = 0xFFFF4444;
            p.text = "💢";
            particles.add(p);
        }
    }

    /**
     * Crear ZZZs de sueño.
     */
    public static void spawnSleepy(float x, float y) {
        String[] zzz = {"z", "Z", "z"};
        for (int i = 0; i < 3; i++) {
            Particle p = new Particle(x + i * 10, y - 40 - i * 15, EffectType.SLEEPY);
            p.vx = 10 + i * 5;
            p.vy = -20;
            p.maxLife = 2f + i * 0.5f;
            p.life = p.maxLife;
            p.size = 14 + i * 4;
            p.color = 0xFF6666FF;
            p.text = zzz[i];
            particles.add(p);
        }
    }

    /**
     * Crear íconos de hambre.
     */
    public static void spawnHungry(float x, float y) {
        String[] food = {"🍞", "🌽", "🐟"};
        Particle p = new Particle(x, y - 50, EffectType.HUNGRY);
        p.vx = 0;
        p.vy = -10;
        p.maxLife = 2f;
        p.life = p.maxLife;
        p.size = 20;
        p.text = food[random.nextInt(food.length)];
        particles.add(p);
    }

    /**
     * Crear huellas.
     */
    public static void spawnFootprint(float x, float y, boolean isLeft) {
        Particle p = new Particle(x, y, EffectType.FOOTPRINTS);
        p.vx = 0;
        p.vy = 0;
        p.maxLife = 3f;
        p.life = p.maxLife;
        p.size = 10;
        p.color = 0x44FF6600; // Naranja semi-transparente
        p.rotation = isLeft ? -15 : 15;
        particles.add(p);
    }

    /**
     * Crear polvo al correr.
     */
    public static void spawnDust(float x, float y) {
        for (int i = 0; i < 5; i++) {
            Particle p = new Particle(x + random.nextFloat() * 20 - 10, y, EffectType.DUST);
            p.vx = random.nextFloat() * 60 - 30;
            p.vy = -20 - random.nextFloat() * 30;
            p.maxLife = 0.5f + random.nextFloat() * 0.3f;
            p.life = p.maxLife;
            p.size = 8 + random.nextFloat() * 8;
            p.color = 0x88AA8866; // Marrón semi-transparente
            particles.add(p);
        }
    }

    /**
     * Crear salpicadura.
     */
    public static void spawnSplash(float x, float y) {
        for (int i = 0; i < 8; i++) {
            float angle = (float)(i * Math.PI * 2 / 8);
            Particle p = new Particle(x, y, EffectType.SPLASH);
            p.vx = (float)Math.cos(angle) * (60 + random.nextFloat() * 40);
            p.vy = (float)Math.sin(angle) * (60 + random.nextFloat() * 40) - 50;
            p.maxLife = 0.6f;
            p.life = p.maxLife;
            p.size = 6 + random.nextFloat() * 4;
            p.color = 0xFF4FC3F7; // Azul agua
            particles.add(p);
        }
    }

    /**
     * Crear plumas volando.
     */
    public static void spawnFeathers(float x, float y, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle(x, y, EffectType.FEATHERS);
            p.vx = random.nextFloat() * 100 - 50;
            p.vy = -50 - random.nextFloat() * 50;
            p.maxLife = 2f + random.nextFloat();
            p.life = p.maxLife;
            p.size = 12 + random.nextFloat() * 8;
            p.color = 0xFFFFFFFF; // Blanco
            p.rotationSpeed = random.nextFloat() * 180 - 90;
            particles.add(p);
        }
    }

    /**
     * Crear onda de honk.
     */
    public static void spawnHonkWave(float x, float y) {
        for (int i = 0; i < 3; i++) {
            Particle p = new Particle(x, y, EffectType.HONK_WAVE);
            p.vx = 0;
            p.vy = 0;
            p.maxLife = 0.5f + i * 0.2f;
            p.life = p.maxLife;
            p.size = 20 + i * 15;
            p.color = 0x44FFAA00; // Naranja semi-transparente
            particles.add(p);
        }
    }

    /**
     * Crear explosión de amor.
     */
    public static void spawnLoveBurst(float x, float y) {
        spawnHearts(x, y, 10);
        spawnSparkles(x, y, 15);
    }

    // ============== TRAILS ==============

    /**
     * Agregar punto al trail arcoíris.
     */
    public static void addRainbowTrailPoint(float x, float y) {
        if (!rainbowTrailActive) return;

        TrailPoint tp = new TrailPoint(x, y, RAINBOW_COLORS[rainbowColorIndex]);
        trail.add(tp);
        rainbowColorIndex = (rainbowColorIndex + 1) % RAINBOW_COLORS.length;

        // Limitar tamaño del trail
        while (trail.size() > 50) {
            trail.remove(0);
        }
    }

    // ============== UPDATE ==============

    /**
     * Actualizar todas las partículas y efectos.
     */
    public static void update(float deltaTime, Vec2 goosePosition, boolean isMoving) {
        // Actualizar partículas existentes
        Iterator<Particle> particleIterator = particles.iterator();
        while (particleIterator.hasNext()) {
            Particle p = particleIterator.next();
            p.update(deltaTime);
            if (p.isDead()) {
                particleIterator.remove();
            }
        }

        // Actualizar trail
        Iterator<TrailPoint> trailIterator = trail.iterator();
        while (trailIterator.hasNext()) {
            TrailPoint tp = trailIterator.next();
            tp.update(deltaTime);
            if (tp.isDead()) {
                trailIterator.remove();
            }
        }

        // Rainbow trail
        if (rainbowTrailActive && isMoving) {
            addRainbowTrailPoint(goosePosition.x, goosePosition.y);
        }

        // Disco mode color cycling
        if (discoModeActive) {
            discoHue += deltaTime * 180; // 180 grados por segundo
            if (discoHue >= 360) discoHue -= 360;
        }

        // Huellas periódicas
        if (isMoving) {
            footprintTimer += deltaTime;
            if (footprintTimer >= 0.3f) {
                footprintTimer = 0;
                // Las huellas se crean desde el código de movimiento
            }
        }
    }

    // ============== RENDER ==============

    /**
     * Renderizar todos los efectos.
     */
    public static void render(Canvas canvas, Vec2 goosePosition) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // Renderizar trail primero (detrás de todo)
        renderTrail(canvas, paint);

        // Renderizar glow si está activo
        if (goldenGlowActive) {
            renderGoldenGlow(canvas, paint, goosePosition);
        }

        // Renderizar partículas
        for (Particle p : particles) {
            renderParticle(canvas, paint, p);
        }

        // Efecto disco
        if (discoModeActive) {
            renderDiscoEffect(canvas, paint, goosePosition);
        }
    }

    /**
     * Renderizar una partícula individual.
     */
    private static void renderParticle(Canvas canvas, Paint paint, Particle p) {
        int alpha = (int)(p.getAlpha() * 255);

        canvas.save();
        canvas.translate(p.x, p.y);
        canvas.rotate(p.rotation);

        switch (p.type) {
            case HEARTS:
            case MUSICAL_NOTES:
            case SLEEPY:
            case HUNGRY:
            case ANGER:
                // Texto/emoji
                paint.setColor(p.color);
                paint.setAlpha(alpha);
                paint.setTextSize(p.size);
                paint.setTextAlign(Paint.Align.CENTER);
                if (p.text != null) {
                    canvas.drawText(p.text, 0, p.size / 3, paint);
                }
                break;

            case SPARKLES:
                // Estrella de 4 puntas
                paint.setColor(p.color);
                paint.setAlpha(alpha);
                paint.setStyle(Paint.Style.FILL);
                drawStar(canvas, paint, 0, 0, p.size, 4);
                break;

            case CONFETTI:
                // Rectángulo rotado
                paint.setColor(p.color);
                paint.setAlpha(alpha);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(-p.size/2, -p.size/4, p.size/2, p.size/4, paint);
                break;

            case FOOTPRINTS:
                // Huella simple
                paint.setColor(p.color);
                paint.setAlpha(alpha);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawOval(-p.size/2, -p.size/3, p.size/2, p.size/3, paint);
                // Dedos
                canvas.drawCircle(-p.size/3, -p.size/2, p.size/5, paint);
                canvas.drawCircle(0, -p.size/2 - 2, p.size/5, paint);
                canvas.drawCircle(p.size/3, -p.size/2, p.size/5, paint);
                break;

            case DUST:
            case SPLASH:
                // Círculo simple
                paint.setColor(p.color);
                paint.setAlpha(alpha);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(0, 0, p.size, paint);
                break;

            case FEATHERS:
                // Forma de pluma
                paint.setColor(p.color);
                paint.setAlpha(alpha);
                paint.setStyle(Paint.Style.FILL);
                Path featherPath = new Path();
                featherPath.moveTo(0, -p.size);
                featherPath.quadTo(p.size/2, -p.size/2, p.size/3, p.size);
                featherPath.quadTo(0, p.size/2, -p.size/3, p.size);
                featherPath.quadTo(-p.size/2, -p.size/2, 0, -p.size);
                canvas.drawPath(featherPath, paint);
                break;

            case HONK_WAVE:
                // Círculo expandiéndose
                float progress = 1f - p.life / p.maxLife;
                float radius = p.size * (1f + progress * 2f);
                paint.setColor(p.color);
                paint.setAlpha((int)(alpha * (1f - progress)));
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f);
                canvas.drawCircle(0, 0, radius, paint);
                break;

            default:
                // Círculo genérico
                paint.setColor(p.color);
                paint.setAlpha(alpha);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(0, 0, p.size, paint);
                break;
        }

        canvas.restore();
    }

    /**
     * Renderizar trail arcoíris.
     */
    private static void renderTrail(Canvas canvas, Paint paint) {
        if (trail.isEmpty()) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);

        for (int i = 1; i < trail.size(); i++) {
            TrailPoint prev = trail.get(i - 1);
            TrailPoint curr = trail.get(i);

            paint.setColor(curr.color);
            paint.setAlpha((int)(curr.life * 200));
            paint.setStrokeWidth(8f * curr.life);

            canvas.drawLine(prev.x, prev.y, curr.x, curr.y, paint);
        }
    }

    /**
     * Renderizar glow dorado.
     */
    private static void renderGoldenGlow(Canvas canvas, Paint paint, Vec2 pos) {
        paint.setStyle(Paint.Style.FILL);

        // Múltiples círculos con gradiente
        for (int i = 3; i >= 0; i--) {
            float radius = 60 + i * 20;
            int alpha = 30 - i * 8;
            paint.setColor(0xFFFFD700); // Gold
            paint.setAlpha(alpha);
            canvas.drawCircle(pos.x, pos.y, radius, paint);
        }
    }

    /**
     * Renderizar efecto disco.
     */
    private static void renderDiscoEffect(Canvas canvas, Paint paint, Vec2 pos) {
        // Color HSV cambiante
        float[] hsv = {discoHue, 1f, 1f};
        int color = Color.HSVToColor(hsv);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha(40);

        // Rayos de luz
        for (int i = 0; i < 8; i++) {
            float angle = (float)(i * Math.PI / 4 + discoHue * Math.PI / 180);
            Path ray = new Path();
            ray.moveTo(pos.x, pos.y);
            float endX = pos.x + (float)Math.cos(angle) * 150;
            float endY = pos.y + (float)Math.sin(angle) * 150;
            float perpX = (float)Math.cos(angle + Math.PI/2) * 20;
            float perpY = (float)Math.sin(angle + Math.PI/2) * 20;
            ray.lineTo(endX + perpX, endY + perpY);
            ray.lineTo(endX - perpX, endY - perpY);
            ray.close();
            canvas.drawPath(ray, paint);
        }
    }

    /**
     * Dibujar estrella.
     */
    private static void drawStar(Canvas canvas, Paint paint, float cx, float cy, float size, int points) {
        Path path = new Path();
        float angle = (float)(-Math.PI / 2);
        float angleStep = (float)(Math.PI / points);

        path.moveTo(cx + (float)Math.cos(angle) * size, cy + (float)Math.sin(angle) * size);

        for (int i = 0; i < points * 2; i++) {
            angle += angleStep;
            float r = (i % 2 == 0) ? size * 0.4f : size;
            path.lineTo(cx + (float)Math.cos(angle) * r, cy + (float)Math.sin(angle) * r);
        }

        path.close();
        canvas.drawPath(path, paint);
    }

    // ============== CONTROL DE MODOS ==============

    public static void setRainbowTrailActive(boolean active) {
        rainbowTrailActive = active;
        if (!active) trail.clear();
    }

    public static void setGoldenGlowActive(boolean active) {
        goldenGlowActive = active;
    }

    public static void setGhostModeActive(boolean active) {
        ghostModeActive = active;
    }

    public static void setDiscoModeActive(boolean active) {
        discoModeActive = active;
    }

    public static boolean isGhostModeActive() {
        return ghostModeActive;
    }

    public static float getGhostAlpha() {
        return ghostModeActive ? 0.5f : 1f;
    }

    public static int getDiscoColor() {
        if (!discoModeActive) return 0xFFFFFFFF;
        float[] hsv = {discoHue, 0.8f, 1f};
        return Color.HSVToColor(hsv);
    }

    /**
     * Limpiar todos los efectos.
     */
    public static void clearAll() {
        particles.clear();
        trail.clear();
    }

    /**
     * Obtener cantidad de partículas activas.
     */
    public static int getParticleCount() {
        return particles.size();
    }
}
