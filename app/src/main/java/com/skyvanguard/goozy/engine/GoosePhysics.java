package com.skyvanguard.goozy.engine;

import com.skyvanguard.goozy.core.*;

/**
 * Advanced physics system for the goose.
 * Handles movement, jumping, bouncing, friction, impulses, and foot IK.
 */
public class GoosePhysics {

    // ============== PHYSICS STATES ==============

    /**
     * Physical states the goose can be in.
     */
    public enum PhysicsState {
        GROUNDED,       // Normal on-ground movement
        JUMPING,        // Ascending in a jump
        FALLING,        // Descending after jump peak
        BOUNCING,       // Bouncing off a surface
        STUNNED,        // Temporarily unable to move
        SLIDING,        // Sliding on slippery surface
        DRAGGED         // Being dragged by user
    }

    /**
     * Surface types affecting movement.
     */
    public enum SurfaceType {
        NORMAL,         // Standard friction
        SLIPPERY,       // Low friction (ice)
        STICKY,         // High friction (mud)
        BOUNCY          // Bounces on contact
    }

    // ============== CONSTANTS ==============

    // Physics constants
    private static final float GRAVITY = 800f;
    private static final float MAX_FALL_SPEED = 500f;
    private static final float GROUND_LEVEL = 0f;  // Relative to spawn position
    private static final float DRAG_COEFFICIENT = 0.98f;
    private static final float STOP_THRESHOLD = 5f;
    /** Segundos que tarda en girar casi del todo hacia su objetivo. */
    private static final float TURN_TIME_CONSTANT = 0.06f;

    // Friction coefficients by surface
    private static final float FRICTION_NORMAL = 0.92f;
    private static final float FRICTION_SLIPPERY = 0.99f;
    private static final float FRICTION_STICKY = 0.75f;

    // Jump settings
    private static final float JUMP_FORCE = 350f;
    private static final float JUMP_HORIZONTAL_BOOST = 1.2f;
    private static final float SQUASH_AMOUNT = 0.3f;
    private static final float SQUASH_RECOVERY_SPEED = 8f;

    // Bounce settings
    private static final float BOUNCE_DAMPENING = 0.6f;
    private static final float MIN_BOUNCE_VELOCITY = 50f;

    // Wobble settings
    private static final float WOBBLE_DECAY = 5f;
    private static final float WOBBLE_FREQUENCY = 15f;
    private static final float MAX_WOBBLE = 15f;

    // Stun settings
    private static final float STUN_RECOVERY_TIME = 0.5f;

    // Screen bounds (will be set externally)
    private int screenWidth = 1080;
    private int screenHeight = 1920;
    private static final float SCREEN_MARGIN = 20f;

    // ============== MOVEMENT STATE ==============

    private Vec2 position = new Vec2(300f, 300f);
    private Vec2 velocity = new Vec2(0f, 0f);
    private Vec2 targetPos = new Vec2(300f, 300f);
    private float direction = 90f;
    private Vec2 targetDirection;

    // Vertical movement (jumping/falling)
    private float verticalPosition = 0f;  // Height above ground
    private float verticalVelocity = 0f;
    private float groundY = 300f;  // Y position of the ground at spawn

    // Speed settings
    private float currentSpeed = 80f;
    private float currentAcceleration = 1300f;
    private float stepTime = 0.2f;

    // ============== PHYSICS STATE ==============

    private PhysicsState state = PhysicsState.GROUNDED;
    private SurfaceType currentSurface = SurfaceType.NORMAL;

    // Squash/stretch for landing
    private float squashFactor = 1f;
    private float stretchFactor = 1f;

    // Wobble effect
    private float wobbleIntensity = 0f;
    private float wobblePhase = 0f;

    // Stun state
    private float stunTimer = 0f;

    // Impulse accumulator
    private Vec2 pendingImpulse = Vec2.ZERO;

    // Movement flags
    private boolean canMove = true;
    private boolean bounceOnEdges = true;
    private boolean applyGravity = false;

    // ============== FOOTPRINTS ==============

    private final Footprint[] footMarks = new Footprint[64];
    private int footMarkIndex = 0;
    private double muddyFeetUntil = -1f;

    // ============== CALLBACKS ==============

    /**
     * Callback interface for physics events.
     */
    public interface PhysicsCallback {
        void onLanded(float impactVelocity);
        void onBounced(float bounceVelocity);
        void onHitEdge(int edge); // 0=left, 1=top, 2=right, 3=bottom
    }

    private PhysicsCallback callback;

    // ============== CONSTRUCTOR ==============

    public GoosePhysics() {
    }

    public void initPosition(float x, float y) {
        position = new Vec2(x, y);
        groundY = y;
        targetPos = new Vec2(100f, 150f);
        verticalPosition = 0f;
        verticalVelocity = 0f;
        state = PhysicsState.GROUNDED;
    }

    public void setCallback(PhysicsCallback callback) {
        this.callback = callback;
    }

    public void setScreenBounds(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }

    // ============== GETTERS ==============

    public Vec2 getPosition() { return position; }
    public Vec2 getVelocity() { return velocity; }
    public Vec2 getTargetPos() { return targetPos; }
    public float getDirection() { return direction; }
    public Vec2 getTargetDirection() { return targetDirection; }
    public float getCurrentSpeed() {
        return (float) Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
    }
    public float getMaxSpeed() { return currentSpeed; }
    public float getCurrentAcceleration() { return currentAcceleration; }
    public float getStepTime() { return stepTime; }
    public Footprint[] getFootMarks() { return footMarks; }
    public double getMuddyFeetUntil() { return muddyFeetUntil; }

    public PhysicsState getState() { return state; }
    public SurfaceType getSurface() { return currentSurface; }
    public float getVerticalPosition() { return verticalPosition; }
    public float getSquashFactor() { return squashFactor; }
    public float getStretchFactor() { return stretchFactor; }
    public float getWobbleAngle() {
        return wobbleIntensity * (float)Math.sin(wobblePhase) * MAX_WOBBLE;
    }
    public boolean isGrounded() { return state == PhysicsState.GROUNDED; }
    public boolean isAirborne() { return state == PhysicsState.JUMPING || state == PhysicsState.FALLING; }
    public boolean isStunned() { return state == PhysicsState.STUNNED; }

    // ============== SETTERS ==============

    public void setPosition(Vec2 pos) {
        position = pos;
        groundY = pos.y;
    }
    public void setVelocity(Vec2 vel) { velocity = vel; }
    public void setTargetPos(Vec2 target) { targetPos = target; }
    public void setDirection(float dir) { direction = dir; }
    public void setMuddyFeetUntil(double time) { muddyFeetUntil = time; }
    public void setSurface(SurfaceType surface) { currentSurface = surface; }
    public void setBounceOnEdges(boolean bounce) { bounceOnEdges = bounce; }
    public void setCanMove(boolean canMove) { this.canMove = canMove; }

    // ============== MAIN UPDATE ==============

    /**
     * Update physics each frame.
     */
    public void update(boolean overrideExtendNeck) {
        float deltaTime = Time.deltaTime;

        // Update state-specific logic
        updateState(deltaTime);

        // Apply pending impulse
        if (pendingImpulse.x != 0 || pendingImpulse.y != 0) {
            velocity = Vec2.add(velocity, pendingImpulse);
            pendingImpulse = Vec2.ZERO;
        }

        // Update based on current state
        switch (state) {
            case GROUNDED:
            case SLIDING:
                updateGroundedMovement(deltaTime);
                break;
            case JUMPING:
            case FALLING:
                updateAirborneMovement(deltaTime);
                break;
            case BOUNCING:
                updateBouncingMovement(deltaTime);
                break;
            case STUNNED:
                updateStunnedMovement(deltaTime);
                break;
            case DRAGGED:
                // Position controlled externally
                break;
        }

        // Update wobble
        updateWobble(deltaTime);

        // Update squash/stretch
        updateSquashStretch(deltaTime);

        // Handle screen boundaries
        handleScreenBounds();

    }

    // ============== STATE UPDATES ==============

    private void updateState(float deltaTime) {
        switch (state) {
            case STUNNED:
                stunTimer -= deltaTime;
                if (stunTimer <= 0) {
                    state = PhysicsState.GROUNDED;
                    canMove = true;
                }
                break;
            case JUMPING:
                if (verticalVelocity <= 0) {
                    state = PhysicsState.FALLING;
                }
                break;
            case FALLING:
            case BOUNCING:
                if (verticalPosition <= 0 && verticalVelocity <= 0) {
                    land();
                }
                break;
        }
    }

    private void updateGroundedMovement(float deltaTime) {
        if (!canMove) return;

        // Gira hacia el objetivo con una constante de tiempo (igual a cualquier fps).
        // Cerca del objetivo la dirección no está definida: se conserva.
        targetDirection = Vec2.normalize(Vec2.sub(targetPos, position));
        if (Vec2.dist(targetPos, position) > STOP_THRESHOLD) {
            float turn = 1f - (float) Math.exp(-deltaTime / TURN_TIME_CONSTANT);
            Vec2 newDir = Vec2.lerp(Vec2.fromDegrees(direction), targetDirection, turn);
            direction = (float) Math.toDegrees(Math.atan2(newDir.y, newDir.x));
        }

        // Apply acceleration towards target
        Vec2 toTarget = Vec2.sub(targetPos, position);
        float distToTarget = Vec2.length(toTarget);

        if (distToTarget > STOP_THRESHOLD) {
            Vec2 accel = Vec2.mul(
                Vec2.normalize(toTarget),
                currentAcceleration * deltaTime
            );
            velocity = Vec2.add(velocity, accel);
        }

        // Clamp velocity to max speed
        float speed = Vec2.length(velocity);
        if (speed > currentSpeed) {
            velocity = Vec2.mul(Vec2.normalize(velocity), currentSpeed);
        }

        // Apply friction based on surface
        float friction = getFrictionForSurface();
        velocity = Vec2.mul(velocity, friction);

        // Apply drag
        velocity = Vec2.mul(velocity, DRAG_COEFFICIENT);

        // Stop if very slow
        if (Vec2.length(velocity) < 1f) {
            velocity = Vec2.ZERO;
        }

        // Update position
        position = Vec2.add(position, Vec2.mul(velocity, deltaTime));
    }

    private void updateAirborneMovement(float deltaTime) {
        // Apply gravity
        verticalVelocity -= GRAVITY * deltaTime;
        verticalVelocity = Math.max(verticalVelocity, -MAX_FALL_SPEED);

        // Update vertical position
        verticalPosition += verticalVelocity * deltaTime;

        // Horizontal movement continues but with reduced control
        if (canMove) {
            Vec2 toTarget = Vec2.sub(targetPos, position);
            Vec2 accel = Vec2.mul(
                Vec2.normalize(toTarget),
                currentAcceleration * 0.3f * deltaTime  // Reduced air control
            );
            velocity = Vec2.add(velocity, accel);
        }

        // Apply air drag
        velocity = Vec2.mul(velocity, 0.995f);

        // Update position
        position = Vec2.add(position, Vec2.mul(velocity, deltaTime));

        // Update direction based on velocity
        if (Vec2.length(velocity) > 10f) {
            float targetDir = (float) Math.atan2(velocity.y, velocity.x) * 57.2957764f;
            direction = MathUtil.lerpAngle(direction, targetDir, 0.1f);
        }
    }

    private void updateBouncingMovement(float deltaTime) {
        // Similar to falling but with horizontal bounce handling
        updateAirborneMovement(deltaTime);
    }

    private void updateStunnedMovement(float deltaTime) {
        // Apply heavy friction while stunned
        velocity = Vec2.mul(velocity, 0.9f);
        position = Vec2.add(position, Vec2.mul(velocity, deltaTime));

        // Add wobble when stunned
        if (wobbleIntensity < 0.5f) {
            wobbleIntensity = 0.8f;
        }
    }

    // ============== PHYSICS EFFECTS ==============

    private void updateWobble(float deltaTime) {
        if (wobbleIntensity > 0.01f) {
            wobblePhase += WOBBLE_FREQUENCY * deltaTime;
            wobbleIntensity *= (1f - WOBBLE_DECAY * deltaTime);

            if (wobbleIntensity < 0.01f) {
                wobbleIntensity = 0f;
                wobblePhase = 0f;
            }
        }
    }

    private void updateSquashStretch(float deltaTime) {
        // Recover towards normal
        squashFactor = MathUtil.lerp(squashFactor, 1f, SQUASH_RECOVERY_SPEED * deltaTime);
        stretchFactor = MathUtil.lerp(stretchFactor, 1f, SQUASH_RECOVERY_SPEED * deltaTime);

        // Apply stretch when jumping/falling fast
        if (isAirborne()) {
            float vertSpeed = Math.abs(verticalVelocity);
            if (vertSpeed > 100f) {
                float stretchAmount = Math.min(vertSpeed / 500f, 0.3f);
                stretchFactor = 1f + stretchAmount;
                squashFactor = 1f - stretchAmount * 0.5f;
            }
        }
    }

    private float getFrictionForSurface() {
        switch (currentSurface) {
            case SLIPPERY:
                return FRICTION_SLIPPERY;
            case STICKY:
                return FRICTION_STICKY;
            case BOUNCY:
            case NORMAL:
            default:
                return FRICTION_NORMAL;
        }
    }

    // ============== SCREEN BOUNDS ==============

    private void handleScreenBounds() {
        boolean hitEdge = false;
        int edge = -1;

        // Left edge
        if (position.x < SCREEN_MARGIN) {
            position.x = SCREEN_MARGIN;
            if (bounceOnEdges && Math.abs(velocity.x) > MIN_BOUNCE_VELOCITY) {
                velocity.x = -velocity.x * BOUNCE_DAMPENING;
                hitEdge = true;
                edge = 0;
            } else {
                velocity.x = 0;
            }
        }

        // Right edge
        if (position.x > screenWidth - SCREEN_MARGIN) {
            position.x = screenWidth - SCREEN_MARGIN;
            if (bounceOnEdges && Math.abs(velocity.x) > MIN_BOUNCE_VELOCITY) {
                velocity.x = -velocity.x * BOUNCE_DAMPENING;
                hitEdge = true;
                edge = 2;
            } else {
                velocity.x = 0;
            }
        }

        // Top edge
        if (position.y < SCREEN_MARGIN) {
            position.y = SCREEN_MARGIN;
            if (bounceOnEdges && Math.abs(velocity.y) > MIN_BOUNCE_VELOCITY) {
                velocity.y = -velocity.y * BOUNCE_DAMPENING;
                hitEdge = true;
                edge = 1;
            } else {
                velocity.y = 0;
            }
        }

        // Bottom edge
        if (position.y > screenHeight - SCREEN_MARGIN) {
            position.y = screenHeight - SCREEN_MARGIN;
            if (bounceOnEdges && Math.abs(velocity.y) > MIN_BOUNCE_VELOCITY) {
                velocity.y = -velocity.y * BOUNCE_DAMPENING;
                hitEdge = true;
                edge = 3;
            } else {
                velocity.y = 0;
            }
        }

        if (hitEdge) {
            addWobble(0.3f);
            if (callback != null) {
                callback.onHitEdge(edge);
            }
        }
    }

    // ============== ACTIONS ==============

    /**
     * Make the goose jump.
     */
    public void jump() {
        jump(1f);
    }

    /**
     * Make the goose jump with custom force multiplier.
     */
    public void jump(float forceMultiplier) {
        if (state != PhysicsState.GROUNDED && state != PhysicsState.SLIDING) {
            return;
        }

        state = PhysicsState.JUMPING;
        verticalVelocity = JUMP_FORCE * forceMultiplier;

        // Boost horizontal velocity slightly
        velocity = Vec2.mul(velocity, JUMP_HORIZONTAL_BOOST);

        // Stretch on jump
        stretchFactor = 1f + SQUASH_AMOUNT;
        squashFactor = 1f - SQUASH_AMOUNT * 0.5f;

        Sound.honk();
    }

    /**
     * Called when landing on ground.
     */
    private void land() {
        float impactVelocity = Math.abs(verticalVelocity);

        // Check for bounce
        if (currentSurface == SurfaceType.BOUNCY && impactVelocity > MIN_BOUNCE_VELOCITY * 2) {
            verticalVelocity = impactVelocity * BOUNCE_DAMPENING;
            state = PhysicsState.BOUNCING;
            if (callback != null) {
                callback.onBounced(verticalVelocity);
            }
            return;
        }

        // Normal landing
        verticalPosition = 0f;
        verticalVelocity = 0f;
        state = PhysicsState.GROUNDED;

        // Apply squash on landing
        float squashIntensity = Math.min(impactVelocity / 400f, SQUASH_AMOUNT);
        squashFactor = 1f - squashIntensity;
        stretchFactor = 1f + squashIntensity * 0.5f;

        // Add wobble based on impact
        addWobble(impactVelocity / 500f);

        // Play sound for hard landings
        if (impactVelocity > 200f) {
            Sound.pat();
        }

        if (callback != null) {
            callback.onLanded(impactVelocity);
        }
    }

    /**
     * Apply an impulse to the goose.
     */
    public void applyImpulse(Vec2 impulse) {
        pendingImpulse = Vec2.add(pendingImpulse, impulse);
        addWobble(Vec2.length(impulse) / 200f);
    }

    /**
     * Apply an impulse in a direction.
     */
    public void applyImpulse(float angle, float force) {
        Vec2 dir = Vec2.fromDegrees(angle);
        applyImpulse(Vec2.mul(dir, force));
    }

    /**
     * Knock back the goose.
     */
    public void knockBack(Vec2 fromPosition, float force) {
        Vec2 dir = Vec2.normalize(Vec2.sub(position, fromPosition));
        applyImpulse(Vec2.mul(dir, force));

        // Small hop on knockback
        if (isGrounded() && force > 100f) {
            verticalVelocity = force * 0.3f;
            state = PhysicsState.JUMPING;
        }
    }

    /**
     * Stun the goose temporarily.
     */
    public void stun(float duration) {
        state = PhysicsState.STUNNED;
        stunTimer = duration;
        canMove = false;
        addWobble(1f);
    }

    /**
     * Add wobble effect.
     */
    public void addWobble(float intensity) {
        wobbleIntensity = Math.min(wobbleIntensity + intensity, 1f);
    }

    /**
     * Start being dragged.
     */
    public void startDrag() {
        state = PhysicsState.DRAGGED;
        velocity = Vec2.ZERO;
    }

    /**
     * Stop being dragged.
     */
    public void endDrag(Vec2 throwVelocity) {
        state = PhysicsState.GROUNDED;
        velocity = throwVelocity;

        // If thrown upward, start jumping
        if (throwVelocity.y < -50f) {
            verticalVelocity = -throwVelocity.y * 0.5f;
            state = PhysicsState.JUMPING;
        }

        addWobble(Vec2.length(throwVelocity) / 300f);
    }

    /**
     * Teleport to position instantly.
     */
    public void teleport(Vec2 newPosition) {
        position = newPosition;
        velocity = Vec2.ZERO;
        verticalPosition = 0f;
        verticalVelocity = 0f;
        state = PhysicsState.GROUNDED;
    }

    // ============== SPEED SETTINGS ==============

    /**
     * Set speed tier.
     */
    public void setSpeed(GooseTasks.SpeedTier tier) {
        switch (tier) {
            case Walk:
                currentSpeed = 80f;
                currentAcceleration = 1300f;
                stepTime = 0.2f;
                break;
            case Run:
                currentSpeed = 200f;
                currentAcceleration = 1300f;
                stepTime = 0.15f;
                break;
            case Charge:
                currentSpeed = 400f;
                currentAcceleration = 2300f;
                stepTime = 0.08f;
                break;
        }
    }

    /**
     * Set custom speed parameters.
     */
    public void setCustomSpeed(float maxSpeed, float acceleration, float stepDuration) {
        this.currentSpeed = maxSpeed;
        this.currentAcceleration = acceleration;
        this.stepTime = stepDuration;
    }

    // ============== FOOT SOLVING ==============

    /**
     * Add a footprint mark.
     */
    /** Estampa una huella de barro si el ganso todavía tiene las patas embarradas. */
    public void stampFootprint(float x, float y) {
        if (Time.time < muddyFeetUntil) addFootMark(new Vec2(x, y));
    }

    private void addFootMark(Vec2 markPos) {
        if (footMarks[footMarkIndex] == null) {
            footMarks[footMarkIndex] = new Footprint();
        }
        footMarks[footMarkIndex].stampedAt = Time.time;
        footMarks[footMarkIndex].position = markPos;
        footMarkIndex++;
        if (footMarkIndex >= footMarks.length) {
            footMarkIndex = 0;
        }
    }

    // ============== UTILITY ==============

    /**
     * Get distance to target.
     */
    public float getDistanceToTarget() {
        return Vec2.dist(position, targetPos);
    }

    /**
     * Check if close to target.
     */
    public boolean isNearTarget(float threshold) {
        return getDistanceToTarget() < threshold;
    }

    /**
     * Get the actual render Y position (accounting for vertical position).
     */
    public float getRenderY() {
        return position.y - verticalPosition;
    }

    /**
     * Check if moving.
     */
    public boolean isMoving() {
        return Vec2.length(velocity) > 5f;
    }

    /**
     * Get movement direction in degrees.
     */
    public float getMovementDirection() {
        if (!isMoving()) return direction;
        return (float) Math.atan2(velocity.y, velocity.x) * 57.2957764f;
    }

    /**
     * Stop all movement immediately.
     */
    public void stop() {
        velocity = Vec2.ZERO;
        verticalVelocity = 0f;
        if (isAirborne()) {
            state = PhysicsState.FALLING;
        }
    }

    /**
     * Reset physics to initial state.
     */
    public void reset() {
        velocity = Vec2.ZERO;
        verticalPosition = 0f;
        verticalVelocity = 0f;
        state = PhysicsState.GROUNDED;
        currentSurface = SurfaceType.NORMAL;
        wobbleIntensity = 0f;
        squashFactor = 1f;
        stretchFactor = 1f;
        stunTimer = 0f;
        canMove = true;
        pendingImpulse = Vec2.ZERO;
    }
}
