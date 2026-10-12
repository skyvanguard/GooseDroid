package com.cfks.goosedroid.GooseDesktop;

import com.cfks.goosedroid.core.*;
import com.cfks.goosedroid.PetNeeds;
import com.cfks.goosedroid.PetPersonality;

import java.util.ArrayList;
import java.util.List;

/**
 * Advanced touch interaction handler for the goose.
 * Supports multiple gesture types, combos, zones, and pattern recognition.
 */
public class GooseTouchHandler {

    // ============== GESTURE TYPES ==============

    /**
     * Types of gestures recognized.
     */
    public enum GestureType {
        NONE,
        TAP,            // Quick single tap
        DOUBLE_TAP,     // Two quick taps
        TRIPLE_TAP,     // Three quick taps
        LONG_PRESS,     // Hold without moving
        PET,            // Gentle stroking
        SCRATCH,        // Fast small movements (rascando)
        DRAG,           // Moving the goose
        THROW,          // Drag and release with velocity
        SWIPE_UP,       // Quick upward swipe
        SWIPE_DOWN,     // Quick downward swipe
        SWIPE_LEFT,     // Quick leftward swipe
        SWIPE_RIGHT,    // Quick rightward swipe
        CIRCLE,         // Circular motion
        BOOP,           // Tap on head/beak area
        TICKLE,         // Rapid light touches
        POKE,           // Quick tap on body
        SHAKE,          // Rapid back-and-forth motion
        BELLY_RUB,      // Circular motion on belly
        HUG,            // Multi-touch surrounding gesture
        ZIGZAG,         // Quick zigzag pattern
        HEART_DRAW,     // Heart shape drawing
        NOSE_BOOP,      // Specific tap on beak tip
        WING_FLAP,      // Swipe across wing area
        GENTLE_HOLD     // Soft sustained touch
    }

    /**
     * Interaction zones on the goose.
     */
    public enum TouchZone {
        NONE,
        HEAD,           // Top area - boop, pat
        BEAK,           // Front tip - nose boop
        BODY,           // Middle area - pet, scratch
        BELLY,          // Lower middle - belly rub
        WING_LEFT,      // Left wing area
        WING_RIGHT,     // Right wing area
        FEET,           // Bottom area - tickle
        TAIL            // Back area - poke
    }

    // ============== CONSTANTS ==============

    // Hit detection
    private static final float HIT_RADIUS = 60f;
    private static final float HEAD_ZONE_OFFSET = -30f;
    private static final float FEET_ZONE_OFFSET = 15f;
    private static final float ZONE_HEIGHT = 25f;

    // Timing thresholds (milliseconds)
    private static final long TAP_THRESHOLD_MS = 200;
    private static final long DOUBLE_TAP_WINDOW_MS = 300;
    private static final long LONG_PRESS_THRESHOLD_MS = 500;
    private static final long GESTURE_COOLDOWN_MS = 100;

    // Distance thresholds
    private static final float DRAG_THRESHOLD = 30f;
    private static final float SWIPE_THRESHOLD = 80f;
    private static final float SCRATCH_THRESHOLD = 5f;
    private static final float CIRCLE_MIN_RADIUS = 20f;

    // Velocity thresholds
    private static final float THROW_VELOCITY_THRESHOLD = 200f;
    private static final float SWIPE_VELOCITY_THRESHOLD = 300f;

    // Pattern detection
    private static final int TOUCH_HISTORY_SIZE = 20;
    private static final int SCRATCH_MOVE_COUNT = 8;
    private static final float CIRCLE_ANGLE_THRESHOLD = 270f;

    // New gesture thresholds
    private static final int SHAKE_DIRECTION_CHANGES = 4;
    private static final float SHAKE_MIN_DISTANCE = 40f;
    private static final int ZIGZAG_TURNS = 3;
    private static final float ZIGZAG_MIN_ANGLE = 90f;
    private static final long GENTLE_HOLD_TIME_MS = 1500;
    private static final float BELLY_RUB_MIN_RADIUS = 15f;

    // Zone detection offsets
    private static final float BEAK_ZONE_OFFSET = -45f;
    private static final float BEAK_ZONE_RADIUS = 12f;
    private static final float BELLY_ZONE_OFFSET = 10f;
    private static final float WING_ZONE_X_OFFSET = 25f;

    // Combo system
    private static final long COMBO_WINDOW_MS = 2000;
    private static final int MAX_COMBO = 10;

    // Emoji display
    private static final long EMOJI_DURATION_MS = 2000;
    private static final long EMOJI_FADE_MS = 500;

    // ============== STATE ==============

    // Current touch state
    private Vec2 touchStartPos = null;
    private Vec2 lastTouchPos = null;
    private long touchStartTime = 0;
    private long lastTouchEndTime = 0;
    private long lastMoveTime = 0;
    private boolean isTouching = false;
    private boolean isBeingPetted = false;
    private boolean isBeingDragged = false;

    // Gesture detection
    private GestureType currentGesture = GestureType.NONE;
    private GestureType lastGesture = GestureType.NONE;
    private TouchZone currentZone = TouchZone.NONE;
    private int consecutiveTaps = 0;
    private int petStrokes = 0;
    private int scratchMoves = 0;
    private long lastGestureTime = 0;

    // Touch history for pattern detection
    private List<TouchPoint> touchHistory = new ArrayList<>();

    // Velocity tracking
    private Vec2 dragVelocity = Vec2.ZERO;
    private List<VelocitySample> velocitySamples = new ArrayList<>();
    private static final int VELOCITY_SAMPLE_COUNT = 5;

    // Circle detection
    private float totalAngle = 0f;
    private float lastAngle = 0f;
    private boolean trackingCircle = false;

    // Shake detection
    private int shakeDirectionChanges = 0;
    private int lastShakeDirection = 0;  // -1 left, 0 none, 1 right
    private float shakeDistance = 0f;

    // Zigzag detection
    private int zigzagTurns = 0;
    private float lastZigzagAngle = 0f;
    private Vec2 lastZigzagPos = null;

    // Belly rub detection
    private float bellyRubAngle = 0f;
    private boolean trackingBellyRub = false;

    // Gentle hold detection
    private boolean isGentleHolding = false;
    private long gentleHoldStartTime = 0;

    // Touch pressure simulation (based on contact area)
    private float touchPressure = 0.5f;
    private float touchRadius = 0f;

    // Affection accumulator
    private float affectionGain = 0f;

    // Combo system
    private int comboCount = 0;
    private long lastComboTime = 0;
    private float comboMultiplier = 1f;

    // Emoji display
    private String currentEmoji = "";
    private long emojiShowTime = 0;
    private float emojiScale = 1f;
    private float emojiOffsetY = 0f;

    // Statistics
    private long lastPetTime = 0;
    private int totalPets = 0;
    private int totalBoops = 0;
    private int totalThrows = 0;

    // ============== INNER CLASSES ==============

    /**
     * A point in touch history.
     */
    private static class TouchPoint {
        Vec2 position;
        long time;

        TouchPoint(Vec2 pos, long time) {
            this.position = new Vec2(pos.x, pos.y);
            this.time = time;
        }
    }

    /**
     * Velocity sample for throw calculation.
     */
    private static class VelocitySample {
        Vec2 velocity;
        long time;

        VelocitySample(Vec2 vel, long time) {
            this.velocity = vel;
            this.time = time;
        }
    }

    // ============== CALLBACK ==============

    /**
     * Callback interface for touch events.
     */
    public interface TouchCallback {
        void onTaskChange(GooseTasks.GooseTask task, boolean honk);
        void onPositionChange(Vec2 newPos);
        Vec2 getPosition();
        float getDirection();
        GoosePhysics getPhysics();
    }

    private TouchCallback callback;
    private GestureListener gestureListener;

    /**
     * Optional listener for gesture events.
     */
    public interface GestureListener {
        void onGestureRecognized(GestureType gesture, TouchZone zone);
        void onCombo(int count, float multiplier);
    }

    public void setCallback(TouchCallback callback) {
        this.callback = callback;
    }

    public void setGestureListener(GestureListener listener) {
        this.gestureListener = listener;
    }

    // ============== GETTERS ==============

    public boolean isTouching() { return isTouching; }
    public boolean isBeingPetted() { return isBeingPetted; }
    public boolean isBeingDragged() { return isBeingDragged; }
    public boolean isGentleHolding() { return isGentleHolding; }
    public GestureType getCurrentGesture() { return currentGesture; }
    public GestureType getLastGesture() { return lastGesture; }
    public TouchZone getCurrentZone() { return currentZone; }
    public int getComboCount() { return comboCount; }
    public float getComboMultiplier() { return comboMultiplier; }
    public long getLastPetTime() { return lastPetTime; }
    public long getLastTouchTime() { return lastTouchEndTime > 0 ? lastTouchEndTime : touchStartTime; }
    public Vec2 getDragVelocity() { return dragVelocity; }
    public float getAffectionGain() { return affectionGain; }
    public float getTouchPressure() { return touchPressure; }

    /**
     * Get a description of the current zone for UI display.
     */
    public String getZoneDescription() {
        switch (currentZone) {
            case BEAK: return "Pico";
            case HEAD: return "Cabeza";
            case BODY: return "Cuerpo";
            case BELLY: return "Panza";
            case WING_LEFT: return "Ala Izquierda";
            case WING_RIGHT: return "Ala Derecha";
            case FEET: return "Patas";
            case TAIL: return "Cola";
            default: return "";
        }
    }

    /**
     * Check if the current zone is a sensitive/affectionate area.
     */
    public boolean isSensitiveZone() {
        return currentZone == TouchZone.BEAK ||
               currentZone == TouchZone.BELLY ||
               currentZone == TouchZone.HEAD;
    }

    public String getCurrentEmoji() {
        long elapsed = System.currentTimeMillis() - emojiShowTime;
        if (elapsed < EMOJI_DURATION_MS) {
            return currentEmoji;
        }
        return "";
    }

    public float getEmojiAlpha() {
        long elapsed = System.currentTimeMillis() - emojiShowTime;
        if (elapsed >= EMOJI_DURATION_MS) return 0f;
        if (elapsed > EMOJI_DURATION_MS - EMOJI_FADE_MS) {
            return 1f - (float)(elapsed - (EMOJI_DURATION_MS - EMOJI_FADE_MS)) / EMOJI_FADE_MS;
        }
        return 1f;
    }

    public float getEmojiScale() {
        long elapsed = System.currentTimeMillis() - emojiShowTime;
        if (elapsed < 100) {
            // Bounce in
            return 0.5f + (elapsed / 100f) * 0.7f;
        }
        if (elapsed < 200) {
            // Settle
            return 1.2f - ((elapsed - 100) / 100f) * 0.2f;
        }
        return 1f;
    }

    // ============== TOUCH HANDLING ==============

    /**
     * Called when user starts touching the screen.
     */
    public void onTouchStart(float x, float y) {
        if (callback == null) return;

        Vec2 touchPos = new Vec2(x, y);
        Vec2 goosePos = callback.getPosition();
        float dist = Vec2.dist(goosePos, touchPos);

        // Check if touching the goose
        if (dist < Math.max(HIT_RADIUS, TheGoose.getTouchRadius())) {
            isTouching = true;
            touchStartPos = touchPos;
            lastTouchPos = touchPos;
            touchStartTime = System.currentTimeMillis();
            lastMoveTime = touchStartTime;

            // Determine touch zone
            currentZone = determineTouchZone(touchPos, goosePos);

            // Clear history
            touchHistory.clear();
            velocitySamples.clear();
            addTouchPoint(touchPos);

            // Reset tracking
            petStrokes = 0;
            scratchMoves = 0;
            totalAngle = 0f;
            trackingCircle = false;
            currentGesture = GestureType.NONE;

            // Reset new gesture tracking
            shakeDirectionChanges = 0;
            lastShakeDirection = 0;
            shakeDistance = 0f;
            zigzagTurns = 0;
            lastZigzagAngle = 0f;
            lastZigzagPos = null;
            trackingBellyRub = false;
            bellyRubAngle = 0f;
            isGentleHolding = false;
            gentleHoldStartTime = 0;
            affectionGain = 0f;

            // Check for consecutive taps
            long timeSinceLastTouch = touchStartTime - lastTouchEndTime;
            if (timeSinceLastTouch < DOUBLE_TAP_WINDOW_MS) {
                consecutiveTaps++;
            } else {
                consecutiveTaps = 1;
            }

            // Start being petted
            isBeingPetted = true;
            isBeingDragged = false;
            callback.onTaskChange(GooseTasks.GooseTask.BeingPetted, false);
        }
    }

    /**
     * Called when user moves finger while touching.
     */
    public void onTouchMove(float x, float y) {
        if (!isTouching || callback == null) return;

        Vec2 touchPos = new Vec2(x, y);
        long currentTime = System.currentTimeMillis();

        // Add to history
        addTouchPoint(touchPos);

        // Calculate movement
        Vec2 delta = Vec2.sub(touchPos, lastTouchPos);
        float moveDist = Vec2.length(delta);

        // Update velocity samples
        if (lastTouchPos != null) {
            // Tiempo desde el evento de movimiento anterior, no desde el toque anterior
            float dt = (currentTime - lastMoveTime) / 1000f;
            lastMoveTime = currentTime;
            if (dt > 0) {
                Vec2 velocity = Vec2.mul(delta, 1f / dt);
                addVelocitySample(velocity, currentTime);
            }
        }

        // Check for drag
        float totalDist = Vec2.dist(touchStartPos, touchPos);
        if (totalDist > DRAG_THRESHOLD && !isBeingDragged) {
            startDrag();
        }

        if (isBeingDragged) {
            // Copia: la física modifica su posición en el lugar y touchPos
            // se guarda como lastTouchPos
            callback.onPositionChange(new Vec2(touchPos.x, touchPos.y));
        } else {
            // Detect patterns
            detectPatterns(touchPos, delta, moveDist);
        }

        // Update zone
        currentZone = determineTouchZone(touchPos, callback.getPosition());

        // Track circle gesture
        trackCircleGesture(touchPos);

        lastTouchPos = touchPos;
    }

    /**
     * Called when user lifts finger from screen.
     */
    public void onTouchEnd(float x, float y) {
        if (!isTouching || callback == null) return;

        Vec2 touchPos = new Vec2(x, y);
        long currentTime = System.currentTimeMillis();
        long duration = currentTime - touchStartTime;

        // Calculate final velocity
        dragVelocity = calculateAverageVelocity();

        // Determine gesture
        GestureType gesture = determineGesture(touchPos, duration);

        // El estado se resetea pase lo que pase: si un gesto falla, el ganso
        // no puede quedar "agarrado".
        try {
            if (gesture != GestureType.NONE) {
                executeGesture(gesture, touchPos);
            }
            updateCombo(gesture);
        } finally {
            if (isBeingDragged) {
                endDrag(touchPos);
            }
            isTouching = false;
            isBeingPetted = false;
            isBeingDragged = false;
            lastTouchEndTime = currentTime;
            lastGesture = gesture;
            currentGesture = GestureType.NONE;

            // Return to wandering
            callback.onTaskChange(GooseTasks.GooseTask.Wander, false);
        }
    }

    // ============== GESTURE DETECTION ==============

    private GestureType determineGesture(Vec2 touchPos, long duration) {
        float totalDist = Vec2.dist(touchStartPos, touchPos);
        float velocity = Vec2.length(dragVelocity);

        // Check for throw
        if (isBeingDragged && velocity > THROW_VELOCITY_THRESHOLD) {
            return GestureType.THROW;
        }

        // Check for drag (already handled)
        if (isBeingDragged) {
            return GestureType.DRAG;
        }

        // Check for belly rub (circular motion on belly)
        if (trackingBellyRub && bellyRubAngle > CIRCLE_ANGLE_THRESHOLD) {
            return GestureType.BELLY_RUB;
        }

        // Check for circle
        if (Math.abs(totalAngle) > CIRCLE_ANGLE_THRESHOLD) {
            return GestureType.CIRCLE;
        }

        // Check for shake (rapid back-and-forth)
        if (shakeDirectionChanges >= SHAKE_DIRECTION_CHANGES && shakeDistance > SHAKE_MIN_DISTANCE) {
            return GestureType.SHAKE;
        }

        // Check for zigzag
        if (zigzagTurns >= ZIGZAG_TURNS && duration < 1000) {
            return GestureType.ZIGZAG;
        }

        // Check for scratch
        if (scratchMoves >= SCRATCH_MOVE_COUNT && duration < 1000) {
            return GestureType.SCRATCH;
        }

        // Check for wing flap (horizontal swipe across wing zones)
        if (totalDist > SWIPE_THRESHOLD && duration < 300) {
            if ((currentZone == TouchZone.WING_LEFT || currentZone == TouchZone.WING_RIGHT)) {
                return GestureType.WING_FLAP;
            }
            return determineSwipeDirection(touchPos);
        }

        // Check for gentle hold
        if (isGentleHolding && duration > GENTLE_HOLD_TIME_MS && totalDist < 10f) {
            return GestureType.GENTLE_HOLD;
        }

        // Check for long press
        if (duration > LONG_PRESS_THRESHOLD_MS && totalDist < DRAG_THRESHOLD) {
            return GestureType.LONG_PRESS;
        }

        // Check for taps
        if (duration < TAP_THRESHOLD_MS && totalDist < DRAG_THRESHOLD) {
            if (consecutiveTaps >= 3) {
                return GestureType.TRIPLE_TAP;
            } else if (consecutiveTaps == 2) {
                return GestureType.DOUBLE_TAP;
            } else {
                // Check zone-specific taps
                if (currentZone == TouchZone.BEAK) {
                    return GestureType.NOSE_BOOP;
                }
                if (currentZone == TouchZone.HEAD) {
                    return GestureType.BOOP;
                }
                return GestureType.TAP;
            }
        }

        // Check for pet
        if (petStrokes > 3 && duration > 300) {
            return GestureType.PET;
        }

        // Check for tickle
        if (currentZone == TouchZone.FEET && scratchMoves > 3) {
            return GestureType.TICKLE;
        }

        return GestureType.POKE;
    }

    private GestureType determineSwipeDirection(Vec2 touchPos) {
        Vec2 delta = Vec2.sub(touchPos, touchStartPos);
        float angle = (float) Math.atan2(delta.y, delta.x) * MathUtil.RAD_TO_DEG;

        // Normalize angle to 0-360
        if (angle < 0) angle += 360;

        if (angle >= 315 || angle < 45) {
            return GestureType.SWIPE_RIGHT;
        } else if (angle >= 45 && angle < 135) {
            return GestureType.SWIPE_DOWN;
        } else if (angle >= 135 && angle < 225) {
            return GestureType.SWIPE_LEFT;
        } else {
            return GestureType.SWIPE_UP;
        }
    }

    private TouchZone determineTouchZone(Vec2 touchPos, Vec2 goosePos) {
        float relativeY = touchPos.y - goosePos.y;
        float relativeX = touchPos.x - goosePos.x;
        float gooseDir = callback != null ? callback.getDirection() : 0f;
        Vec2 forward = Vec2.fromDegrees(gooseDir);
        Vec2 right = Vec2.fromDegrees(gooseDir + 90f);

        // Check beak (tip of head)
        float beakY = goosePos.y + BEAK_ZONE_OFFSET;
        float beakX = goosePos.x + forward.x * 20f;
        float distToBeak = (float) Math.sqrt(Math.pow(touchPos.x - beakX, 2) + Math.pow(touchPos.y - beakY, 2));
        if (distToBeak < BEAK_ZONE_RADIUS) {
            return TouchZone.BEAK;
        }

        // Check head area
        if (relativeY < HEAD_ZONE_OFFSET) {
            return TouchZone.HEAD;
        }

        // Check feet area
        if (relativeY > FEET_ZONE_OFFSET) {
            return TouchZone.FEET;
        }

        // Check wing areas (sides of the body)
        float sideOffset = relativeX * right.x + relativeY * right.y;
        if (Math.abs(sideOffset) > WING_ZONE_X_OFFSET) {
            if (sideOffset > 0) {
                return TouchZone.WING_RIGHT;
            } else {
                return TouchZone.WING_LEFT;
            }
        }

        // Check tail (behind the goose)
        float forwardOffset = relativeX * forward.x + relativeY * forward.y;
        if (forwardOffset < -15) {
            return TouchZone.TAIL;
        }

        // Check belly (lower center)
        if (relativeY > BELLY_ZONE_OFFSET && Math.abs(sideOffset) < WING_ZONE_X_OFFSET) {
            return TouchZone.BELLY;
        }

        // Default to body
        return TouchZone.BODY;
    }

    private void detectPatterns(Vec2 touchPos, Vec2 delta, float moveDist) {
        // Pet detection (smooth strokes)
        if (moveDist > 2f && moveDist < 30f) {
            petStrokes++;
            if (petStrokes % 5 == 0) {
                affectionGain += comboMultiplier;
                PetNeeds.get().happiness = Math.min(100, PetNeeds.get().happiness + 1 * comboMultiplier);
            }
        }

        // Scratch detection (fast small movements)
        if (moveDist > SCRATCH_THRESHOLD && moveDist < 15f) {
            scratchMoves++;
        } else if (moveDist > 20f) {
            // Reset if movement too large
            scratchMoves = Math.max(0, scratchMoves - 2);
        }

        // Shake detection (rapid left-right movement)
        if (Math.abs(delta.x) > 10f) {
            int newDirection = delta.x > 0 ? 1 : -1;
            if (newDirection != lastShakeDirection && lastShakeDirection != 0) {
                shakeDirectionChanges++;
                shakeDistance += Math.abs(delta.x);
            }
            lastShakeDirection = newDirection;
        }

        // Zigzag detection
        detectZigzag(touchPos, delta);

        // Belly rub detection (circular on belly zone)
        if (currentZone == TouchZone.BELLY && trackingCircle) {
            trackingBellyRub = true;
            bellyRubAngle += Math.abs(totalAngle);
        }

        // Gentle hold detection
        if (moveDist < 3f && !isBeingDragged) {
            if (!isGentleHolding) {
                isGentleHolding = true;
                gentleHoldStartTime = System.currentTimeMillis();
            }
        } else {
            isGentleHolding = false;
        }
    }

    /**
     * Detect zigzag pattern in touch movement.
     */
    private void detectZigzag(Vec2 touchPos, Vec2 delta) {
        if (lastZigzagPos == null) {
            lastZigzagPos = new Vec2(touchPos.x, touchPos.y);
            return;
        }

        float distance = Vec2.dist(touchPos, lastZigzagPos);
        if (distance > 20f) {
            // Calculate angle change
            float currentAngle = (float) Math.toDegrees(Math.atan2(delta.y, delta.x));
            float angleDiff = Math.abs(currentAngle - lastZigzagAngle);

            // Normalize angle difference
            if (angleDiff > 180f) angleDiff = 360f - angleDiff;

            // Check for sharp turn
            if (angleDiff > ZIGZAG_MIN_ANGLE) {
                zigzagTurns++;
            }

            lastZigzagAngle = currentAngle;
            lastZigzagPos = new Vec2(touchPos.x, touchPos.y);
        }
    }

    private void trackCircleGesture(Vec2 touchPos) {
        if (touchStartPos == null) return;

        Vec2 center = touchStartPos;
        Vec2 toTouch = Vec2.sub(touchPos, center);
        float dist = Vec2.length(toTouch);

        if (dist > CIRCLE_MIN_RADIUS) {
            float angle = (float) Math.atan2(toTouch.y, toTouch.x) * MathUtil.RAD_TO_DEG;

            if (trackingCircle) {
                float angleDelta = angle - lastAngle;

                // Handle wraparound
                if (angleDelta > 180) angleDelta -= 360;
                if (angleDelta < -180) angleDelta += 360;

                totalAngle += angleDelta;
            }

            trackingCircle = true;
            lastAngle = angle;
        }
    }

    // ============== GESTURE EXECUTION ==============

    private void executeGesture(GestureType gesture, Vec2 touchPos) {
        currentGesture = gesture;
        lastGestureTime = System.currentTimeMillis();

        // Notify listener
        if (gestureListener != null) {
            gestureListener.onGestureRecognized(gesture, currentZone);
        }

        switch (gesture) {
            case TAP:
                onTap();
                break;
            case DOUBLE_TAP:
                onDoubleTap();
                break;
            case TRIPLE_TAP:
                onTripleTap();
                break;
            case LONG_PRESS:
                onLongPress();
                break;
            case PET:
                onPet();
                break;
            case SCRATCH:
                onScratch();
                break;
            case BOOP:
                onBoop();
                break;
            case TICKLE:
                onTickle();
                break;
            case POKE:
                onPoke();
                break;
            case SWIPE_UP:
                onSwipeUp();
                break;
            case SWIPE_DOWN:
                onSwipeDown();
                break;
            case SWIPE_LEFT:
            case SWIPE_RIGHT:
                onSwipeSide(gesture == GestureType.SWIPE_RIGHT);
                break;
            case CIRCLE:
                onCircle(totalAngle > 0);
                break;
            case THROW:
                onThrow();
                break;
            case SHAKE:
                onShake();
                break;
            case BELLY_RUB:
                onBellyRub();
                break;
            case ZIGZAG:
                onZigzag();
                break;
            case NOSE_BOOP:
                onNoseBoop();
                break;
            case WING_FLAP:
                onWingFlap();
                break;
            case GENTLE_HOLD:
                onGentleHold();
                break;
        }
    }

    // ============== GESTURE HANDLERS ==============

    private void onTap() {
        Sound.HONCC();
        showEmoji("!");
        PetPersonality.get().onPetted();
        addHappiness(3);
    }

    private void onDoubleTap() {
        Sound.PlayHonk();
        showEmoji("!!");

        // Make goose jump
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.jump(0.7f);
        }

        addHappiness(5);
    }

    private void onTripleTap() {
        Sound.PlayHonk();
        showEmoji("!!!");

        // Make goose do a big jump
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.jump(1.2f);
        }

        addHappiness(10);
        PetPersonality.get().onPlayed();
    }

    private void onLongPress() {
        showEmoji("...");
        PetNeeds.get().pet();
        addHappiness(8);

        // Calm down effect
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.addWobble(0.2f);
        }
    }

    private void onPet() {
        PetNeeds.get().pet();
        Sound.PlayPat();
        showEmoji("<3");
        lastPetTime = System.currentTimeMillis();
        totalPets++;
        addHappiness(15);
        PetPersonality.get().onPetted();
    }

    private void onScratch() {
        Sound.PlayPat();
        showEmoji("~");
        addHappiness(12);

        // Add wobble from scratching
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.addWobble(0.5f);
        }
    }

    private void onBoop() {
        Sound.HONCC();
        showEmoji("*boop*");
        totalBoops++;
        addHappiness(8);

        // Small knockback from boop
        GoosePhysics physics = callback.getPhysics();
        if (physics != null && touchStartPos != null) {
            physics.applyImpulse(
                Vec2.mul(
                    Vec2.normalize(Vec2.sub(physics.getPosition(), touchStartPos)),
                    50f
                )
            );
        }
    }

    private void onTickle() {
        Sound.PlayPat();
        showEmoji("jaja");
        addHappiness(10);
        PetPersonality.get().onPlayed();

        // Wobble from tickling
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.addWobble(0.7f);
        }
    }

    private void onPoke() {
        Sound.HONCC();
        showEmoji("?");
        addHappiness(2);
    }

    private void onSwipeUp() {
        showEmoji("^");

        // Launch upward
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.jump(1.5f);
            physics.applyImpulse(new Vec2(0, -100f));
        }

        addHappiness(5);
    }

    private void onSwipeDown() {
        showEmoji("v");

        // Press down effect
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.addWobble(0.6f);
        }
    }

    private void onSwipeSide(boolean right) {
        showEmoji(right ? ">" : "<");

        // Push sideways
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            float force = right ? 150f : -150f;
            physics.applyImpulse(new Vec2(force, 0));
        }

        addHappiness(3);
    }

    private void onCircle(boolean clockwise) {
        showEmoji(clockwise ? "@" : "@");
        Sound.PlayHonk();

        // Spin effect
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            float spinForce = clockwise ? 100f : -100f;
            physics.applyImpulse(new Vec2(spinForce, -50f));
            physics.addWobble(0.8f);
        }

        addHappiness(15);
        PetPersonality.get().onPlayed();
    }

    private void onThrow() {
        showEmoji("!");
        totalThrows++;

        // Apply throw velocity through physics
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.endDrag(dragVelocity);
        }

        // Happiness depends on throw strength
        float throwStrength = Vec2.length(dragVelocity);
        if (throwStrength > 500f) {
            showEmoji("!!!");
            addHappiness(-5); // Too rough!
        } else {
            addHappiness(5);
        }
    }

    private void onShake() {
        Sound.PlayHonk();
        showEmoji("@_@");

        // Goose gets dizzy!
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            // Apply wobble effect
            physics.applyImpulse(new Vec2(
                    (float)(Math.random() - 0.5) * 200f,
                    (float)(Math.random() - 0.5) * 200f
            ));
        }

        // A bit rough but playful
        addHappiness(3);
        PetNeeds.get().energy = Math.max(0, PetNeeds.get().energy - 5);
    }

    private void onBellyRub() {
        Sound.PlayHonk();
        showEmoji("\u2764\uFE0F"); // Heart emoji

        // Goose loves belly rubs!
        addHappiness(15);
        PetNeeds.get().pet();
        PetPersonality.get().onPetted();

        // Squash and relax effect
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.setVelocity(Vec2.ZERO);
        }
    }

    private void onZigzag() {
        Sound.PlayHonk();
        showEmoji("\u26A1"); // Lightning bolt

        // Exciting! Make goose energetic
        addHappiness(8);
        PetNeeds.get().energy = Math.min(100, PetNeeds.get().energy + 10);
        PetPersonality.get().onPlayed();

        // Random jump direction
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.applyImpulse(new Vec2(
                    (float)(Math.random() - 0.5) * 150f,
                    -100f
            ));
        }
    }

    private void onNoseBoop() {
        Sound.HONCC();
        showEmoji("\uD83D\uDC23"); // Hatching chick (cute)

        // Very specific and affectionate
        addHappiness(12);
        totalBoops++;
        PetPersonality.get().onPetted();

        // Small recoil animation
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            float dir = callback.getDirection();
            Vec2 back = Vec2.fromDegrees(dir + 180f);
            physics.applyImpulse(Vec2.mul(back, 50f));
        }
    }

    private void onWingFlap() {
        Sound.PlayHonk();
        showEmoji("\uD83E\uDD85"); // Eagle (flying)

        // Makes goose want to fly/jump
        addHappiness(6);

        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            // Small lift
            physics.applyImpulse(new Vec2(0, -150f));
        }
    }

    private void onGentleHold() {
        showEmoji("\u2728"); // Sparkles

        // Very calming and bonding
        addHappiness(12);
        PetNeeds.get().pet();
        PetNeeds.get().energy = Math.min(100, PetNeeds.get().energy + 5);

        // Calm goose completely
        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.setVelocity(Vec2.ZERO);
        }
    }

    // ============== DRAG HANDLING ==============

    private void startDrag() {
        isBeingDragged = true;
        callback.onTaskChange(GooseTasks.GooseTask.BeingDragged, false);

        GoosePhysics physics = callback.getPhysics();
        if (physics != null) {
            physics.startDrag();
        }
    }

    private void endDrag(Vec2 touchPos) {
        // Throw handled in gesture execution if velocity is high enough
        GoosePhysics physics = callback.getPhysics();
        if (physics != null && Vec2.length(dragVelocity) < THROW_VELOCITY_THRESHOLD) {
            physics.endDrag(Vec2.ZERO);
        }

        showEmoji("?");
    }

    // ============== COMBO SYSTEM ==============

    private void updateCombo(GestureType gesture) {
        if (gesture == GestureType.NONE || gesture == GestureType.DRAG) {
            return;
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime - lastComboTime < COMBO_WINDOW_MS) {
            comboCount = Math.min(comboCount + 1, MAX_COMBO);
        } else {
            comboCount = 1;
        }

        // Calculate multiplier (1.0 to 2.5)
        comboMultiplier = 1f + (comboCount - 1) * 0.15f;

        lastComboTime = currentTime;

        // Notify listener
        if (gestureListener != null && comboCount > 1) {
            gestureListener.onCombo(comboCount, comboMultiplier);
        }

        // Show combo indicator
        if (comboCount >= 3) {
            showEmoji(comboCount + "x!");
        }
    }

    // ============== HELPERS ==============

    private void addTouchPoint(Vec2 pos) {
        touchHistory.add(new TouchPoint(pos, System.currentTimeMillis()));
        if (touchHistory.size() > TOUCH_HISTORY_SIZE) {
            touchHistory.remove(0);
        }
    }

    private void addVelocitySample(Vec2 velocity, long time) {
        velocitySamples.add(new VelocitySample(velocity, time));
        if (velocitySamples.size() > VELOCITY_SAMPLE_COUNT) {
            velocitySamples.remove(0);
        }
    }

    private Vec2 calculateAverageVelocity() {
        if (velocitySamples.isEmpty()) return Vec2.ZERO;

        Vec2 sum = Vec2.ZERO;
        float totalWeight = 0f;

        // Weight recent samples more heavily
        for (int i = 0; i < velocitySamples.size(); i++) {
            float weight = (i + 1f) / velocitySamples.size();
            sum = Vec2.add(sum, Vec2.mul(velocitySamples.get(i).velocity, weight));
            totalWeight += weight;
        }

        if (totalWeight > 0) {
            return Vec2.mul(sum, 1f / totalWeight);
        }
        return Vec2.ZERO;
    }

    private void addHappiness(float amount) {
        PetNeeds.get().happiness = Math.min(100, Math.max(0,
            PetNeeds.get().happiness + amount * comboMultiplier));
    }

    /**
     * Show an emoji/expression above the pet.
     */
    public void showEmoji(String emoji) {
        // Las expresiones escritas en inglés en el código se traducen si el idioma es español;
        // null queda como "" porque el renderer llama a isEmpty()
        GoosePhrases.Language language = GooseLLM.isSpanish()
                ? GoosePhrases.Language.SPANISH : GoosePhrases.Language.ENGLISH;
        currentEmoji = emoji == null ? "" : GoosePhrases.localizeExpression(emoji, language);
        emojiShowTime = System.currentTimeMillis();
        emojiScale = 0.5f;
        emojiOffsetY = 0f;
    }

    /**
     * Force clear current gesture state.
     */
    public void cancelTouch() {
        isTouching = false;
        isBeingPetted = false;
        isBeingDragged = false;
        currentGesture = GestureType.NONE;
        touchHistory.clear();
        velocitySamples.clear();
    }

    /**
     * Get statistics.
     */
    public int getTotalPets() { return totalPets; }
    public int getTotalBoops() { return totalBoops; }
    public int getTotalThrows() { return totalThrows; }

    /**
     * Reset all statistics.
     */
    public void resetStats() {
        totalPets = 0;
        totalBoops = 0;
        totalThrows = 0;
        comboCount = 0;
        comboMultiplier = 1f;
    }
}
