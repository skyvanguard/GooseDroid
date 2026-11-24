package com.skyvanguard.goozy.engine;

import android.graphics.*;
import com.skyvanguard.goozy.core.*;
import com.skyvanguard.goozy.engine.body.GooseBodyPainter;
import com.skyvanguard.goozy.engine.body.GooseModel;
import com.skyvanguard.goozy.engine.body.Species;
import com.skyvanguard.goozy.PetAppearance;
import com.skyvanguard.goozy.PetNeeds;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Advanced rendering system for the goose.
 * Handles body rendering, particles, trails, glow effects, and status indicators.
 */
public class GooseRenderer {

    // ============== PARTICLE SYSTEM ==============

    /**
     * Represents a single particle in the system.
     */
    public static class Particle {
        public Vec2 position;
        public Vec2 velocity;
        public float life;          // 0 to 1, dies at 0
        public float maxLife;
        public float size;
        public int color;
        public ParticleType type;
        public float rotation;
        public float rotationSpeed;
        public float alpha;

        public Particle(Vec2 pos, Vec2 vel, float life, float size, int color, ParticleType type) {
            this.position = new Vec2(pos.x, pos.y);
            this.velocity = vel;
            this.life = life;
            this.maxLife = life;
            this.size = size;
            this.color = color;
            this.type = type;
            this.rotation = MathUtil.randomRange(0, 360);
            this.rotationSpeed = MathUtil.randomRange(-180, 180);
            this.alpha = 1f;
        }

        public void update(float deltaTime) {
            position = Vec2.add(position, Vec2.mul(velocity, deltaTime));
            life -= deltaTime;
            alpha = Math.max(0, life / maxLife);
            rotation += rotationSpeed * deltaTime;

            // Apply gravity for some particle types
            if (type == ParticleType.CONFETTI || type == ParticleType.SWEAT_DROP) {
                velocity.y += 100f * deltaTime;
            }
        }

        public boolean isDead() {
            return life <= 0;
        }
    }

    /**
     * Types of particles available.
     */
    public enum ParticleType {
        HEART,          // Floating hearts when happy/petted
        STAR,           // Stars when excited/playing
        MUSIC_NOTE,     // Notes when dancing
        CONFETTI,       // Celebration particles
        BUBBLE,         // Bubbles when eating
        SWEAT_DROP,     // When tired/stressed
        SPARKLE,        // General sparkle effect
        DUST,           // Movement dust
        ZZZ,            // Sleep particles
        ANGER           // Anger marks
    }

    // ============== CONSTANTS ==============

    private static final int MAX_PARTICLES = 100;
    private static final float[] GRADIENT_STOPS_50 = {0f, 0.5f, 1f};
    private static final int[] BANNER_GRADIENT_COLORS = {0xFFFFD700, 0xFFFFA500, 0xFFFF8C00};
    /** Radio de una huella de barro en unidades de modelo. */
    private static final float FOOTPRINT_UNITS = 1.3f;
    private static final int CHICK_COLOR = 0xFFFFE680;
    /** Las partículas, más chicas que el ganso para no taparlo. */
    private static final float PARTICLE_SCALE = 0.65f;

    // ============== STATE ==============

    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sleepTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GooseBodyPainter bodyPainter = new GooseBodyPainter();
    private final List<Particle> particles = new ArrayList<>();
    private boolean showShadow = true;

    // Particle spawn timers
    private float heartSpawnTimer = 0;
    private float starSpawnTimer = 0;
    private float bubbleSpawnTimer = 0;

    // Colors
    public int footColor = 0xFFFFA500;
    public int outlineColor = 0xFFD3D3D3;
    public int mouthColor = 0xFFFFA500;
    public int eyeColor = 0xFF000000;
    public int bodyColor = 0xFFFFFFFF;

    public void setShowShadow(boolean show) {
        this.showShadow = show;
    }

    // ============== MAIN RENDER ==============

    /**
     * Main render method.
     */
    /** Capas de dibujo: el mundo (efectos, texto) y el cuerpo del ganso. */
    public enum Layer { WORLD, GOOSE }

    /**
     * Avanza el estado de dibujo. Una vez por frame.
     */
    public void updateFrame(GoosePhysics physics, GooseAI ai, boolean petModeEnabled) {
        float deltaTime = Time.deltaTime;
        updateParticles(deltaTime);

        // Spawn particles based on state
        if (petModeEnabled) {
            spawnStateParticles(physics, ai, deltaTime);
        }
    }

    public void render(Canvas canvas, Layer layer, GoosePhysics physics, GooseModel body,
                       GooseTouchHandler touchHandler, GooseAI ai, boolean petModeEnabled,
                       int worldWidth, int worldHeight) {
        if (layer == Layer.GOOSE) {
            if (body == null) return;
            // El pollito con el color por defecto es amarillo pollito
            int color = body.species == Species.CHICK && bodyColor == Color.WHITE
                    ? CHICK_COLOR : bodyColor;
            bodyPainter.setColors(color, outlineColor, footColor, mouthColor, eyeColor);
            PetAppearance look = PetAppearance.get();
            boolean dressed = petModeEnabled && look.hasAccessories();
            bodyPainter.draw(canvas, body, showShadow,
                    dressed ? look.hatId : 0, dressed ? look.accessoryId : 0);
            return;
        }

        renderFootprints(canvas, physics);

        // Partículas e indicador de sueño: sus medidas están pensadas a escala del ganso
        Vec2 position = physics.getPosition();
        float drawScale = TheGoose.DrawScale * PARTICLE_SCALE;
        canvas.save();
        canvas.translate(position.x, position.y);
        canvas.scale(drawScale, drawScale);
        canvas.translate(-position.x, -position.y);
        renderParticles(canvas);
        if (petModeEnabled) renderSleepIndicator(canvas, position, ai);
        canvas.restore();

        if (petModeEnabled) {
            renderLabel(canvas, body, touchHandler);
            float bodyTop = body != null && body.hasLayout ? body.minY : position.y - THOUGHT_GAP_ABOVE_GOOSE;
            float bodyBottom = body != null && body.hasLayout ? body.maxY : position.y + THOUGHT_GAP_ABOVE_GOOSE;
            renderThoughtBubble(canvas, position, bodyTop, bodyBottom, worldWidth, worldHeight);
        }
        renderAchievementNotification(canvas, worldWidth, worldHeight);
    }

    // ============== PARTICLE MANAGEMENT ==============

    private void updateParticles(float deltaTime) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update(deltaTime);
            if (p.isDead()) {
                it.remove();
            }
        }
    }

    /**
     * Spawn a single particle.
     */
    public void spawnParticle(Vec2 position, ParticleType type) {
        if (particles.size() >= MAX_PARTICLES) return;

        Vec2 vel;
        float life, size;
        int color;

        switch (type) {
            case HEART:
                vel = new Vec2(MathUtil.randomRange(-20, 20), MathUtil.randomRange(-50, -30));
                life = MathUtil.randomRange(1f, 2f);
                size = MathUtil.randomRange(8, 14);
                color = 0xFFFF69B4;
                break;
            case STAR:
                vel = new Vec2(MathUtil.randomRange(-40, 40), MathUtil.randomRange(-60, -20));
                life = MathUtil.randomRange(0.8f, 1.5f);
                size = MathUtil.randomRange(6, 12);
                color = 0xFFFFD700;
                break;
            case MUSIC_NOTE:
                vel = new Vec2(MathUtil.randomRange(-15, 15), MathUtil.randomRange(-40, -25));
                life = MathUtil.randomRange(1.5f, 2.5f);
                size = MathUtil.randomRange(10, 16);
                color = 0xFF9370DB;
                break;
            case CONFETTI:
                vel = new Vec2(MathUtil.randomRange(-80, 80), MathUtil.randomRange(-100, -50));
                life = MathUtil.randomRange(1f, 2f);
                size = MathUtil.randomRange(4, 8);
                // Random bright color
                int[] confettiColors = {0xFFFF6B6B, 0xFF4ECDC4, 0xFFFFE66D, 0xFF95E1D3, 0xFFDDA0DD};
                color = confettiColors[(int)(Math.random() * confettiColors.length)];
                break;
            case BUBBLE:
                vel = new Vec2(MathUtil.randomRange(-10, 10), MathUtil.randomRange(-30, -15));
                life = MathUtil.randomRange(0.8f, 1.2f);
                size = MathUtil.randomRange(4, 10);
                color = 0x8800BFFF;
                break;
            case SWEAT_DROP:
                vel = new Vec2(MathUtil.randomRange(-5, 5), -20);
                life = MathUtil.randomRange(0.6f, 1f);
                size = MathUtil.randomRange(4, 8);
                color = 0xFF87CEEB;
                break;
            case SPARKLE:
                vel = new Vec2(MathUtil.randomRange(-30, 30), MathUtil.randomRange(-30, 30));
                life = MathUtil.randomRange(0.3f, 0.6f);
                size = MathUtil.randomRange(3, 6);
                color = 0xFFFFFFFF;
                break;
            case DUST:
                vel = new Vec2(MathUtil.randomRange(-20, 20), MathUtil.randomRange(-10, 5));
                life = MathUtil.randomRange(0.3f, 0.5f);
                size = MathUtil.randomRange(2, 5);
                color = 0x88C4A484;
                break;
            case ZZZ:
                vel = new Vec2(15, -25);
                life = MathUtil.randomRange(1.5f, 2f);
                size = MathUtil.randomRange(12, 18);
                color = 0xFF6666FF;
                break;
            case ANGER:
                vel = new Vec2(MathUtil.randomRange(-25, 25), MathUtil.randomRange(-35, -20));
                life = MathUtil.randomRange(0.5f, 0.8f);
                size = MathUtil.randomRange(6, 10);
                color = 0xFFFF4444;
                break;
            default:
                return;
        }

        particles.add(new Particle(position, vel, life, size, color, type));
    }

    /**
     * Spawn burst of particles.
     */
    public void spawnParticleBurst(Vec2 position, ParticleType type, int count) {
        for (int i = 0; i < count; i++) {
            Vec2 offset = new Vec2(
                MathUtil.randomRange(-15, 15),
                MathUtil.randomRange(-15, 15)
            );
            spawnParticle(Vec2.add(position, offset), type);
        }
    }

    /**
     * Spawn particles based on current state.
     */
    private void spawnStateParticles(GoosePhysics physics, GooseAI ai, float deltaTime) {
        Vec2 pos = physics.getPosition();
        GooseTasks.GooseTask task = ai.getCurrentTask();
        PetNeeds.MoodState mood = PetNeeds.get().getMoodState();

        // Heart particles when being petted or happy
        if (task == GooseTasks.GooseTask.BeingPetted) {
            heartSpawnTimer -= deltaTime;
            if (heartSpawnTimer <= 0) {
                spawnParticle(Vec2.add(pos, new Vec2(0, -40)), ParticleType.HEART);
                heartSpawnTimer = 0.3f;
            }
        }

        // Star particles when playing or excited
        if (task == GooseTasks.GooseTask.Playing || task == GooseTasks.GooseTask.Happy) {
            starSpawnTimer -= deltaTime;
            if (starSpawnTimer <= 0) {
                spawnParticle(Vec2.add(pos, new Vec2(MathUtil.randomRange(-20, 20), -35)), ParticleType.STAR);
                starSpawnTimer = 0.5f;
            }
        }

        // Bubble particles when eating
        if (task == GooseTasks.GooseTask.Eating) {
            bubbleSpawnTimer -= deltaTime;
            if (bubbleSpawnTimer <= 0) {
                spawnParticle(Vec2.add(pos, new Vec2(MathUtil.randomRange(-10, 10), -30)), ParticleType.BUBBLE);
                bubbleSpawnTimer = 0.4f;
            }
        }

        // Sweat drops when tired
        if (mood == PetNeeds.MoodState.TIRED && Math.random() < 0.02) {
            spawnParticle(Vec2.add(pos, new Vec2(15, -35)), ParticleType.SWEAT_DROP);
        }

        // Dust when moving fast
        if (physics.getCurrentSpeed() > 100f && Math.random() < 0.1) {
            spawnParticle(pos, ParticleType.DUST);
        }

        // Music notes during dance event
        if (ai.getActiveEvent() == GooseAI.RandomEvent.DANCE) {
            if (Math.random() < 0.08) {
                spawnParticle(Vec2.add(pos, new Vec2(MathUtil.randomRange(-20, 20), -40)), ParticleType.MUSIC_NOTE);
            }
        }

        // ZZZ when sleeping
        if (task == GooseTasks.GooseTask.Sleeping && Math.random() < 0.03) {
            spawnParticle(Vec2.add(pos, new Vec2(20, -45)), ParticleType.ZZZ);
        }
    }

    private void renderParticles(Canvas canvas) {
        for (Particle p : particles) {
            particlePaint.setColor(p.color);
            particlePaint.setAlpha((int)(p.alpha * 255));

            canvas.save();
            canvas.translate(p.position.x, p.position.y);
            canvas.rotate(p.rotation);

            switch (p.type) {
                case HEART:
                    drawHeart(canvas, particlePaint, Vec2.ZERO, p.size);
                    break;
                case STAR:
                    drawStar(canvas, particlePaint, Vec2.ZERO, p.size);
                    break;
                case MUSIC_NOTE:
                    drawMusicNote(canvas, particlePaint, Vec2.ZERO, p.size);
                    break;
                case CONFETTI:
                    particlePaint.setStyle(Paint.Style.FILL);
                    canvas.drawRect(-p.size/2, -p.size/4, p.size/2, p.size/4, particlePaint);
                    break;
                case BUBBLE:
                    particlePaint.setStyle(Paint.Style.STROKE);
                    particlePaint.setStrokeWidth(1.5f);
                    canvas.drawCircle(0, 0, p.size, particlePaint);
                    // Highlight
                    particlePaint.setStyle(Paint.Style.FILL);
                    particlePaint.setColor(0x88FFFFFF);
                    canvas.drawCircle(-p.size * 0.3f, -p.size * 0.3f, p.size * 0.25f, particlePaint);
                    break;
                case SWEAT_DROP:
                    drawSweatDrop(canvas, particlePaint, Vec2.ZERO, p.size);
                    break;
                case SPARKLE:
                    drawSparkle(canvas, particlePaint, Vec2.ZERO, p.size);
                    break;
                case DUST:
                    particlePaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(0, 0, p.size, particlePaint);
                    break;
                case ZZZ:
                    particlePaint.setTextSize(p.size);
                    particlePaint.setStyle(Paint.Style.FILL);
                    canvas.drawText("Z", 0, 0, particlePaint);
                    break;
                case ANGER:
                    drawAngerMark(canvas, particlePaint, Vec2.ZERO, p.size);
                    break;
            }

            canvas.restore();
        }
    }

    private void renderFootprints(Canvas canvas, GoosePhysics physics) {
        Footprint[] footMarks = physics.getFootMarks();
        mudPaint.setColor(0xCC7A4A24);
        mudPaint.setAntiAlias(true);
        float footprintRadius = FOOTPRINT_UNITS * TheGoose.DrawScale;

        for (int i = 0; i < footMarks.length; i++) {
            float visibility = footMarks[i] != null ? footMarks[i].visibility(Time.time) : 0f;
            if (visibility > 0f) {
                float radius = footprintRadius * visibility;
                canvas.drawCircle(footMarks[i].position.x, footMarks[i].position.y, radius, mudPaint);
            }
        }
    }

    // ============== ETIQUETA SOBRE LA CABEZA ==============

    private static final float LABEL_TEXT_SIZE = 13f;
    private static final float LABEL_PADDING = 5f;
    private static final float LABEL_GAP = 6f;
    /** Emoticones que la cara del ganso ya expresa: no hace falta escribirlos. */
    private static final java.util.Set<String> FACE_EMOTICONS = new java.util.HashSet<>(
            java.util.Arrays.asList(":)", ":D", ":(", ">:)", "?", "!", "!!!", "...", "~",
                    "v", "^", "<3", "T_T", "X_X", "^_^"));

    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF labelRect = new RectF();

    /** Texto corto de una reacción ("¡EVOLUCIONÓ!", "Nv.2!"), en una etiqueta que se desvanece. */
    private void renderLabel(Canvas canvas, GooseModel body, GooseTouchHandler touchHandler) {
        if (body == null || touchHandler == null) return;
        String text = touchHandler.getCurrentEmoji();
        if (text.isEmpty() || FACE_EMOTICONS.contains(text.trim())) return;

        float progress = touchHandler.getEmojiProgress();
        float alpha = progress < 0.7f ? 1f : (1f - progress) / 0.3f;
        float rise = progress * 8f;

        labelTextPaint.setTextSize(LABEL_TEXT_SIZE);
        labelTextPaint.setTextAlign(Paint.Align.CENTER);
        labelTextPaint.setFakeBoldText(true);
        float width = labelTextPaint.measureText(text);
        float cx = body.headX;
        float bottom = body.minY - LABEL_GAP - rise;
        labelRect.set(cx - width / 2f - LABEL_PADDING, bottom - LABEL_TEXT_SIZE - LABEL_PADDING,
                cx + width / 2f + LABEL_PADDING, bottom + LABEL_PADDING * 0.6f);

        int a = Math.round(alpha * 235f);
        labelFillPaint.setColor(Color.argb(a, 255, 255, 255));
        canvas.drawRoundRect(labelRect, 8f, 8f, labelFillPaint);
        labelTextPaint.setColor(Color.argb(Math.round(alpha * 255f), 38, 38, 46));
        canvas.drawText(text, cx, bottom - 1f, labelTextPaint);
    }

    // ============== THOUGHT BUBBLE ==============

    private static final float THOUGHT_TEXT_SIZE = 15f;
    private static final float THOUGHT_LINE_HEIGHT = 19f;
    private static final float THOUGHT_MAX_TEXT_WIDTH = 170f;
    private static final float THOUGHT_PADDING = 11f;
    private static final float THOUGHT_CORNER_RADIUS = 10f;
    private static final float THOUGHT_OFFSET_X = 38f;
    private static final float THOUGHT_GAP_ABOVE_GOOSE = 62f;
    private static final float THOUGHT_BODY_GAP = 8f;
    private static final float THOUGHT_SCREEN_MARGIN = 6f;
    private static final int THOUGHT_MAX_LINES = 5;
    private static final String THOUGHT_ELLIPSIS = "…";

    private final Paint thoughtTextPaint = createThoughtTextPaint();
    private final Paint thoughtFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thoughtOutlinePaint = createThoughtOutlinePaint();
    private final RectF thoughtRect = new RectF();
    private final List<String> thoughtLines = new ArrayList<>();
    private String wrappedThought = null;

    private static Paint createThoughtTextPaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(THOUGHT_TEXT_SIZE);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        return paint;
    }

    private static Paint createThoughtOutlinePaint() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        return paint;
    }

    /**
     * Parte el texto en líneas que entren en el ancho máximo. Se recalcula solo
     * cuando cambia el pensamiento.
     */
    private void wrapThought(String thought) {
        if (thought.equals(wrappedThought)) return;
        wrappedThought = thought;
        thoughtLines.clear();

        StringBuilder line = new StringBuilder();
        for (String word : thought.trim().split("\\s+")) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (thoughtTextPaint.measureText(candidate) <= THOUGHT_MAX_TEXT_WIDTH) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                thoughtLines.add(line.toString());
                line.setLength(0);
            }
            // Una palabra más ancha que la burbuja se corta por caracteres
            String rest = word;
            while (thoughtTextPaint.measureText(rest) > THOUGHT_MAX_TEXT_WIDTH
                    && rest.length() > 1) {
                int count = Math.max(1, thoughtTextPaint.breakText(
                        rest, true, THOUGHT_MAX_TEXT_WIDTH, null));
                thoughtLines.add(rest.substring(0, count));
                rest = rest.substring(count);
            }
            line.append(rest);
        }
        if (line.length() > 0) {
            thoughtLines.add(line.toString());
        }

        if (thoughtLines.size() > THOUGHT_MAX_LINES) {
            String last = thoughtLines.get(THOUGHT_MAX_LINES - 1);
            while (thoughtLines.size() > THOUGHT_MAX_LINES) {
                thoughtLines.remove(thoughtLines.size() - 1);
            }
            thoughtLines.set(THOUGHT_MAX_LINES - 1, last + THOUGHT_ELLIPSIS);
        }
    }

    /**
     * Render the thought bubble showing what the goose is thinking.
     */
    private void renderThoughtBubble(Canvas canvas, Vec2 position, float bodyTop, float bodyBottom,
                                     int worldWidth, int worldHeight) {
        String thought = TheGoose.getCurrentThought();
        if (thought == null || thought.isEmpty()) return;

        float alpha = TheGoose.getThoughtAlpha();
        if (alpha <= 0) return;
        int alphaInt = (int) (Math.min(1f, alpha) * 255);

        wrapThought(thought);
        if (thoughtLines.isEmpty()) return;

        float textWidth = 0f;
        for (String line : thoughtLines) {
            textWidth = Math.max(textWidth, thoughtTextPaint.measureText(line));
        }
        float width = textWidth + 2f * THOUGHT_PADDING;
        float height = thoughtLines.size() * THOUGHT_LINE_HEIGHT + 2f * THOUGHT_PADDING - 4f;

        // Arriba a la derecha del ganso; si no entra, del otro lado o abajo
        float left = position.x + THOUGHT_OFFSET_X;
        boolean isOnRight = left + width <= worldWidth - THOUGHT_SCREEN_MARGIN;
        if (!isOnRight) {
            left = position.x - THOUGHT_OFFSET_X - width;
        }
        float top = bodyTop - THOUGHT_BODY_GAP - height;
        boolean isAbove = top >= THOUGHT_SCREEN_MARGIN;
        if (!isAbove) {
            top = bodyBottom + THOUGHT_BODY_GAP;
        }
        left = MathUtil.clamp(left, THOUGHT_SCREEN_MARGIN,
                Math.max(THOUGHT_SCREEN_MARGIN, worldWidth - THOUGHT_SCREEN_MARGIN - width));
        top = MathUtil.clamp(top, THOUGHT_SCREEN_MARGIN,
                Math.max(THOUGHT_SCREEN_MARGIN, worldHeight - THOUGHT_SCREEN_MARGIN - height));
        thoughtRect.set(left, top, left + width, top + height);

        thoughtFillPaint.setColor(Color.argb(alphaInt, 255, 255, 255));
        thoughtOutlinePaint.setColor(Color.argb(alphaInt, 170, 170, 170));
        thoughtTextPaint.setColor(Color.argb(alphaInt, 45, 45, 45));

        // Cola: tres círculos que van de la burbuja hacia el ganso
        float tailX = isOnRight ? thoughtRect.left : thoughtRect.right;
        float tailY = isAbove ? thoughtRect.bottom : thoughtRect.top;
        float stepX = isOnRight ? -5f : 5f;
        float stepY = isAbove ? 8f : -8f;
        float[] radii = {6f, 4f, 2.5f};
        for (int i = 0; i < radii.length; i++) {
            float cx = tailX + stepX * (i + 0.4f);
            float cy = tailY + stepY * (i + 1);
            canvas.drawCircle(cx, cy, radii[i], thoughtFillPaint);
            canvas.drawCircle(cx, cy, radii[i], thoughtOutlinePaint);
        }

        canvas.drawRoundRect(thoughtRect, THOUGHT_CORNER_RADIUS, THOUGHT_CORNER_RADIUS,
                thoughtFillPaint);
        canvas.drawRoundRect(thoughtRect, THOUGHT_CORNER_RADIUS, THOUGHT_CORNER_RADIUS,
                thoughtOutlinePaint);

        float baseline = thoughtRect.top + THOUGHT_PADDING + THOUGHT_TEXT_SIZE - 3f;
        for (String line : thoughtLines) {
            canvas.drawText(line, thoughtRect.left + THOUGHT_PADDING, baseline, thoughtTextPaint);
            baseline += THOUGHT_LINE_HEIGHT;
        }
    }

    private void renderSleepIndicator(Canvas canvas, Vec2 position, GooseAI ai) {
        if (ai.getCurrentTask() != GooseTasks.GooseTask.Sleeping) return;

        Paint textPaint = sleepTextPaint;
        textPaint.setColor(0xFF6666FF);
        textPaint.setAntiAlias(true);

        float elapsed = (float) (Time.time - ai.getSleepStartTime());
        float yOffset = (elapsed % 1f) * 20f;

        textPaint.setTextSize(18f);
        canvas.drawText("Z", position.x + 20, position.y - 40 - yOffset, textPaint);
        textPaint.setTextSize(14f);
        canvas.drawText("z", position.x + 30, position.y - 50 - yOffset * 0.8f, textPaint);
        textPaint.setTextSize(10f);
        canvas.drawText("z", position.x + 38, position.y - 58 - yOffset * 0.6f, textPaint);
    }

    // ============== ACHIEVEMENT NOTIFICATION RENDERING ==============

    // Objetos reutilizables del banner de logros (se reconfiguran en cada frame)
    private final Paint bannerShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BlurMaskFilter bannerShadowBlur = new BlurMaskFilter(12f, BlurMaskFilter.Blur.NORMAL);
    private final Paint bannerBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerIconBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerHeaderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerNamePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerDescPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerCounterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bannerCounterTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notificationSparklePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /**
     * Render the achievement notification banner.
     * Slides in from top of screen with elegant animation.
     */
    private void renderAchievementNotification(Canvas canvas, int screenWidth, int screenHeight) {
        TheGoose.AchievementNotification notification = TheGoose.getCurrentAchievementNotification();
        if (notification == null) return;

        TheGoose.Achievement achievement = notification.achievement;
        float animPhase = notification.animPhase;

        // Animation: slide in from top
        float slideOffset = (1f - easeOutBack(animPhase)) * -120f;

        // Banner dimensions
        float bannerWidth = Math.min(screenWidth * 0.85f, 350f);
        float bannerHeight = 80f;
        float bannerX = (screenWidth - bannerWidth) / 2f;
        float bannerY = 40f + slideOffset;

        // Shadow
        Paint shadowPaint = bannerShadowPaint;
        shadowPaint.setColor(Color.argb((int)(60 * animPhase), 0, 0, 0));
        shadowPaint.setAntiAlias(true);
        shadowPaint.setMaskFilter(bannerShadowBlur);
        canvas.drawRoundRect(bannerX + 4, bannerY + 6, bannerX + bannerWidth + 4,
                bannerY + bannerHeight + 6, 16f, 16f, shadowPaint);

        // Main banner gradient background
        Paint bgPaint = bannerBgPaint;
        bgPaint.setAntiAlias(true);
        LinearGradient gradient = new LinearGradient(
                bannerX, bannerY, bannerX + bannerWidth, bannerY + bannerHeight,
                BANNER_GRADIENT_COLORS,
                GRADIENT_STOPS_50,
                Shader.TileMode.CLAMP
        );
        bgPaint.setShader(gradient);
        canvas.drawRoundRect(bannerX, bannerY, bannerX + bannerWidth,
                bannerY + bannerHeight, 16f, 16f, bgPaint);

        // Inner glow
        Paint glowPaint = bannerGlowPaint;
        glowPaint.setAntiAlias(true);
        RadialGradient innerGlow = new RadialGradient(
                bannerX + bannerWidth / 2, bannerY + bannerHeight / 2,
                bannerWidth / 2,
                Color.argb((int)(80 * animPhase), 255, 255, 255),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        );
        glowPaint.setShader(innerGlow);
        canvas.drawRoundRect(bannerX, bannerY, bannerX + bannerWidth,
                bannerY + bannerHeight, 16f, 16f, glowPaint);

        // Border
        Paint borderPaint = bannerBorderPaint;
        borderPaint.setColor(Color.argb((int)(200 * animPhase), 255, 255, 255));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        borderPaint.setAntiAlias(true);
        canvas.drawRoundRect(bannerX + 1, bannerY + 1, bannerX + bannerWidth - 1,
                bannerY + bannerHeight - 1, 15f, 15f, borderPaint);

        // Icon circle background
        float iconSize = 50f;
        float iconX = bannerX + 20f + iconSize / 2;
        float iconY = bannerY + bannerHeight / 2;

        Paint iconBgPaint = bannerIconBgPaint;
        iconBgPaint.setColor(Color.argb((int)(220 * animPhase), 255, 255, 255));
        iconBgPaint.setAntiAlias(true);
        canvas.drawCircle(iconX, iconY, iconSize / 2 + 3, iconBgPaint);

        // Achievement icon (emoji)
        Paint iconPaint = bannerIconPaint;
        iconPaint.setTextSize(32f);
        iconPaint.setTextAlign(Paint.Align.CENTER);
        iconPaint.setAntiAlias(true);
        iconPaint.setColor(Color.argb((int)(255 * animPhase), 0, 0, 0));
        canvas.drawText(achievement.icon, iconX, iconY + 10f, iconPaint);

        // "Achievement Unlocked!" text
        Paint headerPaint = bannerHeaderPaint;
        headerPaint.setColor(Color.argb((int)(255 * animPhase), 255, 255, 255));
        headerPaint.setTextSize(12f);
        headerPaint.setAntiAlias(true);
        headerPaint.setTypeface(Typeface.DEFAULT_BOLD);
        headerPaint.setLetterSpacing(0.1f);
        float textStartX = bannerX + 20f + iconSize + 15f;
        canvas.drawText("ACHIEVEMENT UNLOCKED!", textStartX, bannerY + 22f, headerPaint);

        // Achievement name
        Paint namePaint = bannerNamePaint;
        namePaint.setColor(Color.argb((int)(255 * animPhase), 80, 40, 0));
        namePaint.setTextSize(18f);
        namePaint.setAntiAlias(true);
        namePaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(achievement.name, textStartX, bannerY + 44f, namePaint);

        // Achievement description
        Paint descPaint = bannerDescPaint;
        descPaint.setColor(Color.argb((int)(200 * animPhase), 80, 50, 20));
        descPaint.setTextSize(11f);
        descPaint.setAntiAlias(true);

        // Truncate if too long
        String desc = achievement.description;
        float maxDescWidth = bannerWidth - iconSize - 50f;
        while (descPaint.measureText(desc) > maxDescWidth && desc.length() > 3) {
            desc = desc.substring(0, desc.length() - 4) + "...";
        }
        canvas.drawText(desc, textStartX, bannerY + 62f, descPaint);

        // Sparkle particles around banner
        if (animPhase > 0.5f) {
            renderNotificationSparkles(canvas, bannerX + bannerWidth / 2, bannerY + bannerHeight / 2,
                    bannerWidth, bannerHeight, animPhase);
        }

        // Pending notifications indicator
        int pending = TheGoose.getPendingNotificationCount();
        if (pending > 1) {
            Paint counterPaint = bannerCounterPaint;
            counterPaint.setColor(Color.argb((int)(220 * animPhase), 255, 100, 100));
            counterPaint.setAntiAlias(true);
            float counterX = bannerX + bannerWidth - 20f;
            float counterY = bannerY + bannerHeight - 10f;
            canvas.drawCircle(counterX, counterY, 12f, counterPaint);

            Paint counterTextPaint = bannerCounterTextPaint;
            counterTextPaint.setColor(Color.WHITE);
            counterTextPaint.setTextSize(12f);
            counterTextPaint.setTextAlign(Paint.Align.CENTER);
            counterTextPaint.setAntiAlias(true);
            counterTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("+" + (pending - 1), counterX, counterY + 4f, counterTextPaint);
        }
    }

    /**
     * Render sparkle particles around the notification banner.
     */
    private void renderNotificationSparkles(Canvas canvas, float centerX, float centerY,
                                            float width, float height, float alpha) {
        Paint sparklePaint = notificationSparklePaint;
        sparklePaint.setAntiAlias(true);

        float time = Time.timeF() * 2f;

        for (int i = 0; i < 8; i++) {
            float angle = (float)(i * Math.PI / 4 + time * 0.5f);
            float distance = width / 2 + 15f + (float)Math.sin(time * 3 + i) * 10f;
            float x = centerX + (float)Math.cos(angle) * distance * 0.6f;
            float y = centerY + (float)Math.sin(angle) * distance * 0.3f;

            float sparkleAlpha = (float)(0.3f + 0.7f * Math.abs(Math.sin(time * 4 + i * 0.7f)));
            float size = 3f + 2f * (float)Math.abs(Math.sin(time * 5 + i * 1.2f));

            sparklePaint.setColor(Color.argb((int)(255 * alpha * sparkleAlpha), 255, 255, 200));
            canvas.drawCircle(x, y, size, sparklePaint);

            // Cross sparkle effect
            sparklePaint.setStrokeWidth(1.5f);
            sparklePaint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x - size * 1.5f, y, x + size * 1.5f, y, sparklePaint);
            canvas.drawLine(x, y - size * 1.5f, x, y + size * 1.5f, sparklePaint);
            sparklePaint.setStyle(Paint.Style.FILL);
        }
    }

    /**
     * Ease out back function for bouncy animation.
     */
    private float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        return 1 + c3 * (float)Math.pow(t - 1, 3) + c1 * (float)Math.pow(t - 1, 2);
    }

    // ============== SHAPE DRAWING UTILITIES ==============

    // Objetos reutilizables de las formas (cada llamada dibuja antes de la siguiente)
    private final Path heartPath = new Path();
    private final Path starPath = new Path();
    private final RectF noteHeadRect = new RectF();
    private final Path noteFlagPath = new Path();
    private final Path dropPath = new Path();
    private final RectF ellipseRect = new RectF();

    /**
     * Draw a heart shape.
     */
    private void drawHeart(Canvas canvas, Paint paint, Vec2 pos, float size) {
        Path heart = heartPath;
        heart.rewind();
        float x = pos.x;
        float y = pos.y;
        float s = size * 0.5f;

        heart.moveTo(x, y + s * 0.3f);
        heart.cubicTo(x - s, y - s * 0.5f, x - s * 1.5f, y + s * 0.3f, x, y + s);
        heart.cubicTo(x + s * 1.5f, y + s * 0.3f, x + s, y - s * 0.5f, x, y + s * 0.3f);
        heart.close();

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(heart, paint);
    }

    /**
     * Draw a 5-pointed star.
     */
    private void drawStar(Canvas canvas, Paint paint, Vec2 pos, float size) {
        Path star = starPath;
        star.rewind();
        float outerRadius = size;
        float innerRadius = size * 0.4f;

        for (int i = 0; i < 10; i++) {
            float radius = (i % 2 == 0) ? outerRadius : innerRadius;
            float angle = (float)(i * Math.PI / 5 - Math.PI / 2);
            float px = pos.x + (float)Math.cos(angle) * radius;
            float py = pos.y + (float)Math.sin(angle) * radius;

            if (i == 0) {
                star.moveTo(px, py);
            } else {
                star.lineTo(px, py);
            }
        }
        star.close();

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(star, paint);
    }

    /**
     * Draw a music note.
     */
    private void drawMusicNote(Canvas canvas, Paint paint, Vec2 pos, float size) {
        paint.setStyle(Paint.Style.FILL);

        // Note head
        noteHeadRect.set(
            pos.x - size * 0.4f,
            pos.y + size * 0.3f,
            pos.x + size * 0.3f,
            pos.y + size * 0.7f
        );
        canvas.drawOval(noteHeadRect, paint);

        // Stem
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.15f);
        canvas.drawLine(pos.x + size * 0.25f, pos.y + size * 0.4f,
                       pos.x + size * 0.25f, pos.y - size * 0.5f, paint);

        // Flag
        Path flag = noteFlagPath;
        flag.rewind();
        flag.moveTo(pos.x + size * 0.25f, pos.y - size * 0.5f);
        flag.quadTo(pos.x + size * 0.6f, pos.y - size * 0.3f,
                   pos.x + size * 0.4f, pos.y);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawPath(flag, paint);
    }

    /**
     * Draw a sweat drop.
     */
    private void drawSweatDrop(Canvas canvas, Paint paint, Vec2 pos, float size) {
        Path drop = dropPath;
        drop.rewind();
        drop.moveTo(pos.x, pos.y - size);
        drop.quadTo(pos.x + size * 0.6f, pos.y, pos.x, pos.y + size * 0.5f);
        drop.quadTo(pos.x - size * 0.6f, pos.y, pos.x, pos.y - size);
        drop.close();

        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(drop, paint);
    }

    /**
     * Draw a sparkle/twinkle.
     */
    private void drawSparkle(Canvas canvas, Paint paint, Vec2 pos, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);

        // Four pointed sparkle
        canvas.drawLine(pos.x - size, pos.y, pos.x + size, pos.y, paint);
        canvas.drawLine(pos.x, pos.y - size, pos.x, pos.y + size, paint);

        // Diagonal lines (smaller)
        float diagSize = size * 0.6f;
        canvas.drawLine(pos.x - diagSize, pos.y - diagSize,
                       pos.x + diagSize, pos.y + diagSize, paint);
        canvas.drawLine(pos.x + diagSize, pos.y - diagSize,
                       pos.x - diagSize, pos.y + diagSize, paint);
    }

    /**
     * Draw anger mark (cross veins).
     */
    private void drawAngerMark(Canvas canvas, Paint paint, Vec2 pos, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);

        canvas.drawLine(pos.x - size, pos.y - size,
                       pos.x + size, pos.y + size, paint);
        canvas.drawLine(pos.x + size, pos.y - size,
                       pos.x - size, pos.y + size, paint);
    }

    // ============== BASIC DRAWING UTILITIES ==============

    public void fillEllipseFromCenter(Canvas canvas, Paint paint, int x, int y, int xRadius, int yRadius) {
        ellipseRect.set(x - xRadius, y - yRadius, x + xRadius, y + yRadius);
        canvas.drawOval(ellipseRect, paint);
    }

    public void fillCircleFromCenter(Canvas canvas, Paint paint, Vec2 pos, int radius) {
        fillEllipseFromCenter(canvas, paint, (int) pos.x, (int) pos.y, radius, radius);
    }

    private void drawLine(Canvas canvas, Paint paint, Vec2 start, Vec2 end) {
        canvas.drawLine(start.x, start.y, end.x, end.y, paint);
    }

    // ============== PUBLIC METHODS FOR EXTERNAL TRIGGERS ==============

    /**
     * Trigger a celebration effect (confetti burst).
     */
    public void triggerCelebration(Vec2 position) {
        spawnParticleBurst(position, ParticleType.CONFETTI, 20);
        spawnParticleBurst(position, ParticleType.STAR, 8);
    }

    /**
     * Trigger love effect (heart burst).
     */
    public void triggerLoveEffect(Vec2 position) {
        spawnParticleBurst(position, ParticleType.HEART, 10);
        spawnParticleBurst(position, ParticleType.SPARKLE, 5);
    }

    /**
     * Trigger excitement effect (stars).
     */
    public void triggerExcitement(Vec2 position) {
        spawnParticleBurst(position, ParticleType.STAR, 12);
    }

    /**
     * Clear all particles.
     */
    public void clearParticles() {
        particles.clear();
    }
}
