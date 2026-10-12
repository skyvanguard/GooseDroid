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

    // ============== FOOT STATE ==============

    private Vec2 lFootPos;
    private Vec2 rFootPos;
    private double lFootMoveTimeStart = -1f;
    private double rFootMoveTimeStart = -1f;
    private Vec2 lFootMoveOrigin;
    private Vec2 rFootMoveOrigin;
    private Vec2 lFootMoveDir;
    private Vec2 rFootMoveDir;

    // Foot height during jump
    private float footHeightOffset = 0f;

    // Step animation
    private float stepBounce = 0f;
    private boolean lastStepLeft = false;

    // ============== FOOTPRINTS ==============

    private final Footprint[] footMarks = new Footprint[64];
    private int footMarkIndex = 0;
    private double trackMudEndTime = -1f;

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
        lFootPos = getFootHome(false);
        rFootPos = getFootHome(true);
    }

    public void initPosition(float x, float y) {
        position = new Vec2(x, y);
        groundY = y;
        targetPos = new Vec2(100f, 150f);
        lFootPos = getFootHome(false);
        rFootPos = getFootHome(true);
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
    public Vec2 getLeftFootPos() { return lFootPos; }
    public Vec2 getRightFootPos() { return rFootPos; }
    public Footprint[] getFootMarks() { return footMarks; }
    public double getTrackMudEndTime() { return trackMudEndTime; }

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
    public float getFootHeightOffset() { return footHeightOffset; }
    public float getStepBounce() { return stepBounce; }

    // ============== SETTERS ==============

    public void setPosition(Vec2 pos) {
        position = pos;
        groundY = pos.y;
    }
    public void setVelocity(Vec2 vel) { velocity = vel; }
    public void setTargetPos(Vec2 target) { targetPos = target; }
    public void setDirection(float dir) { direction = dir; }
    public void setTrackMudEndTime(double time) { trackMudEndTime = time; }
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

        // Update feet - also update when dragged so feet follow the body
        if (state == PhysicsState.DRAGGED) {
            // When dragged, feet should follow the body position smoothly
            Vec2 footHomeL = getFootHome(false);
            Vec2 footHomeR = getFootHome(true);
            lFootPos = Vec2.lerp(lFootPos, footHomeL, 0.3f);
            rFootPos = Vec2.lerp(rFootPos, footHomeR, 0.3f);
        } else {
            solveFeet();
        }

        // Update foot height based on vertical position
        footHeightOffset = verticalPosition;

        // Update step bounce
        updateStepBounce(deltaTime);
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

        // Calculate target direction
        targetDirection = Vec2.normalize(Vec2.sub(targetPos, position));

        // Smoothly rotate towards target
        Vec2 currentDir = Vec2.fromDegrees(direction);
        Vec2 newDir = Vec2.lerp(currentDir, targetDirection, 0.25f);
        direction = (float) Math.atan2(newDir.y, newDir.x) * 57.2957764f;

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

    private void updateStepBounce(float deltaTime) {
        // Decay step bounce
        stepBounce *= 0.85f;
        if (Math.abs(stepBounce) < 0.1f) {
            stepBounce = 0f;
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
        lFootPos = getFootHome(false);
        rFootPos = getFootHome(true);
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
     * Solve foot positions with IK.
     */
    private void solveFeet() {
        // Don't animate feet when airborne
        if (isAirborne()) {
            // Tuck feet slightly when in air
            Vec2 centerPos = getFootHome(false);
            lFootPos = Vec2.lerp(lFootPos, Vec2.add(centerPos, new Vec2(0, -5)), 0.2f);
            rFootPos = Vec2.lerp(rFootPos, Vec2.add(centerPos, new Vec2(0, -5)), 0.2f);
            return;
        }

        Vec2 footHome = getFootHome(false);
        Vec2 footHome2 = getFootHome(true);

        if (lFootMoveTimeStart < 0f && rFootMoveTimeStart < 0f) {
            if (Vec2.dist(lFootPos, footHome) > 5f) {
                lFootMoveOrigin = lFootPos;
                lFootMoveDir = Vec2.normalize(Vec2.sub(footHome, lFootPos));
                lFootMoveTimeStart = Time.time;
                lastStepLeft = true;
                return;
            }
            if (Vec2.dist(rFootPos, footHome2) > 5f) {
                rFootMoveOrigin = rFootPos;
                rFootMoveDir = Vec2.normalize(Vec2.sub(footHome2, rFootPos));
                rFootMoveTimeStart = Time.time;
                lastStepLeft = false;
                return;
            }
        } else if (lFootMoveTimeStart > 0f) {
            Vec2 target = Vec2.add(footHome, Vec2.mul(Vec2.mul(lFootMoveDir, 0.4f), 5f));
            if (Time.time <= lFootMoveTimeStart + stepTime) {
                float p = (float) ((Time.time - lFootMoveTimeStart) / stepTime);
                float eased = MathUtil.easeInOut(p);
                lFootPos = Vec2.lerp(lFootMoveOrigin, target, eased);

                // Add arc to foot movement
                float arcHeight = (float)Math.sin(p * Math.PI) * 3f;
                lFootPos.y -= arcHeight;

                return;
            }
            lFootPos = target;
            lFootMoveTimeStart = -1f;

            // Step bounce
            stepBounce = 2f;

            Sound.pat();
            if (Time.time < trackMudEndTime) {
                addFootMark(lFootPos);
            }
        } else if (rFootMoveTimeStart > 0f) {
            Vec2 target = Vec2.add(footHome2, Vec2.mul(Vec2.mul(rFootMoveDir, 0.4f), 5f));
            if (Time.time > rFootMoveTimeStart + stepTime) {
                rFootPos = target;
                rFootMoveTimeStart = -1f;

                // Step bounce
                stepBounce = 2f;

                Sound.pat();
                if (Time.time < trackMudEndTime) {
                    addFootMark(rFootPos);
                }
            } else {
                float p = (float) ((Time.time - rFootMoveTimeStart) / stepTime);
                float eased = MathUtil.easeInOut(p);
                rFootPos = Vec2.lerp(rFootMoveOrigin, target, eased);

                // Add arc to foot movement
                float arcHeight = (float)Math.sin(p * Math.PI) * 3f;
                rFootPos.y -= arcHeight;
            }
        }
    }

    /**
     * Get foot home position.
     */
    private Vec2 getFootHome(boolean rightFoot) {
        float offset = rightFoot ? 1f : 0f;
        Vec2 perpendicular = Vec2.mul(Vec2.fromDegrees(direction + 90f), offset);
        return Vec2.add(position, Vec2.mul(perpendicular, 6f));
    }

    /**
     * Add a footprint mark.
     */
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
