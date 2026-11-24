package com.skyvanguard.goozy.engine;

import com.skyvanguard.goozy.core.*;
import com.skyvanguard.goozy.PetNeeds;
import com.skyvanguard.goozy.PetPersonality;
import com.skyvanguard.goozy.PetConfigFile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static com.skyvanguard.goozy.MainActivity.string2boolean;

/**
 * Advanced AI behavior system for the goose.
 * Features: modular behaviors, memory, routines, random events, decision tree.
 */
public class GooseAI {

    // ============== BEHAVIOR INTERFACE ==============

    /**
     * Interface for modular behaviors.
     */
    public interface Behavior {
        void onEnter();
        void onUpdate(float deltaTime);
        void onExit();
        boolean isComplete();
        int getPriority(); // Higher = more important
        String getName();
    }

    // ============== MEMORY SYSTEM ==============

    /**
     * Stores memory of recent events.
     */
    public static class Memory {
        public double lastPetTime = 0;
        public double lastFeedTime = 0;
        public double lastPlayTime = 0;
        public double lastSleepTime = 0;
        public double lastHonkTime = 0;
        public int petCountToday = 0;
        public int feedCountToday = 0;
        public int playCountToday = 0;
        public Vec2 lastExploredArea = Vec2.ZERO;
        public List<Vec2> favoriteSpots = new ArrayList<>();
        public float timeSinceLastInteraction = 0;
        public int consecutiveIgnoredCalls = 0;

        public void recordPet() {
            lastPetTime = Time.time;
            petCountToday++;
            timeSinceLastInteraction = 0;
            consecutiveIgnoredCalls = 0;
            // Record in LLM memory
            GooseLLM.recordEvent("pet", "pet", 0.3f);
        }

        public void recordFeed() {
            lastFeedTime = Time.time;
            feedCountToday++;
            timeSinceLastInteraction = 0;
            // Record in LLM memory
            GooseLLM.recordEvent("feed", "feed", 0.4f);
        }

        public void recordPlay() {
            lastPlayTime = Time.time;
            playCountToday++;
            timeSinceLastInteraction = 0;
            // Record in LLM memory
            GooseLLM.recordEvent("play", "play", 0.5f);
        }

        public void recordSleep() {
            lastSleepTime = Time.time;
            // Record in LLM memory
            GooseLLM.recordEvent("sleep", "sleep", 0.2f);
        }

        public void update(float deltaTime) {
            timeSinceLastInteraction += deltaTime;
        }

        public void addFavoriteSpot(Vec2 spot) {
            if (favoriteSpots.size() >= 5) {
                favoriteSpots.remove(0);
            }
            favoriteSpots.add(spot);
        }

        public boolean hasBeenIgnoredTooLong() {
            return timeSinceLastInteraction > 120f; // 2 minutes
        }

        public void resetDaily() {
            petCountToday = 0;
            feedCountToday = 0;
            playCountToday = 0;
        }
    }

    // ============== ROUTINE SYSTEM ==============

    /**
     * Time-based routine behaviors.
     */
    public enum DayPeriod {
        MORNING,    // 6:00 - 12:00
        AFTERNOON,  // 12:00 - 18:00
        EVENING,    // 18:00 - 22:00
        NIGHT       // 22:00 - 6:00
    }

    // ============== RANDOM EVENTS ==============

    /**
     * Spontaneous random events.
     */
    public enum RandomEvent {
        NONE,
        RANDOM_HONK,      // Sudden honk
        ZOOMIES,          // Run around crazily
        SPIN,             // Spin in place
        SHAKE,            // Shake body
        LOOK_AROUND,      // Look left and right
        JUMP_SCARE,       // Jump suddenly
        DANCE,            // Little dance
        SNEEZE,           // Sneeze animation
        YAWN,             // Big yawn
        STRETCH,          // Stretch body
        CURIOUS_TILT,     // Tilt head curiously
        WING_FLAP,        // Flap wings

        // NEW EVENTS
        SINGING,          // Melodic honk sequence
        PLAY_DEAD,        // Drama queen - fake death
        LAY_EGG,          // Leave a "gift" (egg/feather)
        FIGHT_REFLECTION, // Fight with invisible enemy/reflection
        COSTUME_CHANGE,   // Randomly change accessory
        CHASE_INVISIBLE,  // Chase something nobody can see
        MOONWALK,         // Walk backwards smoothly
        BELLY_FLOP,       // Flop on belly
        TROLL_NOTIFICATION, // Send a troll notification
        VIBRATE_HONK      // Honk with phone vibration
    }

    // ============== STATE ==============

    private GooseTasks.GooseTask currentTask = GooseTasks.GooseTask.Wander;
    private GooseTasks.Stroll stroll;
    private GooseTasks.FetchNote fetchNote;
    private GooseTasks.MudDash mudDash;
    private boolean hasStrolled;

    private boolean overrideExtendNeck = false;

    // Pet state timers
    private double sleepStartTime = 0;
    private double eatStartTime = 0;
    private double playStartTime = 0;
    private double happyStartTime = 0;

    // Durations (affected by personality)
    private float sleepDuration = 5f;
    private float eatDuration = 3f;
    private float playDuration = 4f;

    // Memory
    private final Memory memory = new Memory();

    // Random events
    private RandomEvent activeEvent = RandomEvent.NONE;
    private float eventTimer = 0;
    private float nextEventCheck = 0;
    private float eventDuration = 0;

    // Routine
    private DayPeriod currentPeriod = DayPeriod.AFTERNOON;
    private float routineCheckTimer = 0;

    // Curiosity
    private Vec2 curiosityTarget = null;
    private float curiosityTimer = 0;
    private boolean isExploring = false;

    // Decision priorities
    private static final int PRIORITY_CRITICAL = 100;  // Sleep when exhausted
    private static final int PRIORITY_HIGH = 75;       // User interaction
    private static final int PRIORITY_MEDIUM = 50;     // Needs attention
    private static final int PRIORITY_LOW = 25;        // Random behaviors
    private static final int PRIORITY_IDLE = 0;        // Wandering

    // Behavior Tree for intelligent decision making
    private GooseBehaviorTree behaviorTree;
    private boolean useBehaviorTree = true;  // Toggle between old random AI and new BT

    // Task picker
    /** Mandados que elige al terminar un paseo; los repetidos pesan más. */
    private static final GooseTasks.GooseTask[] ERRANDS = {
        GooseTasks.GooseTask.FetchNote,
        GooseTasks.GooseTask.FetchNote,
        GooseTasks.GooseTask.MudDash,
        GooseTasks.GooseTask.Wander,
    };
    private final ShuffleBag errandPicker = new ShuffleBag(ERRANDS.length);

    // ============== CALLBACK ==============

    public interface AICallback {
        GoosePhysics getPhysics();
        GooseTouchHandler getTouchHandler();
        PetConfigFile getConfig();
        int getScreenWidth();
        int getScreenHeight();
        boolean isPetModeEnabled();
    }

    private AICallback callback;

    public void setCallback(AICallback callback) {
        this.callback = callback;
        // Initialize behavior tree
        if (behaviorTree == null) {
            behaviorTree = new GooseBehaviorTree();
        }
        behaviorTree.setCallback(callback);
    }

    // ============== GETTERS ==============

    public GooseTasks.GooseTask getCurrentTask() {
        return currentTask;
    }

    public boolean isOverrideExtendNeck() {
        return overrideExtendNeck;
    }

    public double getSleepStartTime() {
        return sleepStartTime;
    }

    public Memory getMemory() {
        return memory;
    }

    public DayPeriod getCurrentPeriod() {
        return currentPeriod;
    }

    public RandomEvent getActiveEvent() {
        return activeEvent;
    }

    // ============== INITIALIZATION ==============

    public void initTaskPicker() {
        updateDayPeriod();
    }

    // ============== MAIN UPDATE ==============

    /**
     * Run AI logic each frame.
     */
    public void runAI() {
        if (callback == null) return;

        float deltaTime = Time.deltaTime;
        overrideExtendNeck = false;

        // Update systems
        memory.update(deltaTime);
        updateRoutine(deltaTime);

        // Run active event if any
        if (activeEvent != RandomEvent.NONE) {
            // El timer avanza acá: es el único camino que corre mientras hay evento.
            eventTimer += deltaTime;
            if (eventTimer >= eventDuration) {
                endRandomEvent();
            } else {
                runRandomEvent(deltaTime);
            }
            return;
        }

        // Use Behavior Tree for intelligent decisions
        if (useBehaviorTree && behaviorTree != null && callback.isPetModeEnabled()) {
            runBehaviorTree(deltaTime);
        } else {
            // Fallback to old random system
            updateRandomEvents(deltaTime);
            updateCuriosity(deltaTime);
        }

        // Run current task
        runCurrentTask();
    }

    /**
     * Run the behavior tree for intelligent decision making.
     */
    private void runBehaviorTree(float deltaTime) {
        // Update the tree with memory data
        behaviorTree.setTimeSinceInteraction(memory.timeSinceLastInteraction);

        // Tick the behavior tree
        behaviorTree.update(deltaTime);

        // Check if tree requested a task change
        GooseTasks.GooseTask requestedTask = behaviorTree.getRequestedTask();
        if (requestedTask != null && requestedTask != currentTask) {
            setTask(requestedTask, false);
        }

        // Check if tree requested an event
        RandomEvent requestedEvent = behaviorTree.getRequestedEvent();
        if (requestedEvent != null && activeEvent == RandomEvent.NONE) {
            triggerRandomEvent(requestedEvent);
        }
    }

    /**
     * Get the current behavior name from the BT (for debug/display).
     */
    public String getCurrentBehaviorName() {
        if (behaviorTree != null) {
            return behaviorTree.getCurrentBehavior();
        }
        return currentTask.name();
    }

    /**
     * Toggle between Behavior Tree AI and old random AI.
     */
    public void setUseBehaviorTree(boolean use) {
        this.useBehaviorTree = use;
    }

    public boolean isUsingBehaviorTree() {
        return useBehaviorTree;
    }

    private void runCurrentTask() {
        switch (currentTask) {
            case Wander:
                runStroll();
                break;
            case FetchNote:
                runFetchNote();
                break;
            case MudDash:
                runMudDash();
                break;
            case Sleeping:
                runSleeping();
                break;
            case Eating:
                runEating();
                break;
            case Playing:
                runPlaying();
                break;
            case Sad:
                runSad();
                break;
            case Happy:
                runHappy();
                break;
            case Seeking:
                runSeeking();
                break;
            case BeingPetted:
                runBeingPetted();
                break;
            case BeingDragged:
                break;
        }
    }

    // ============== DECISION TREE ==============

    /**
     * Check pet needs and update state using priority system.
     */
    public void checkNeedsAndUpdateState() {
        if (callback == null || !callback.isPetModeEnabled()) return;

        GooseTouchHandler touch = callback.getTouchHandler();
        if (touch.isBeingPetted() || touch.isBeingDragged()) return;
        if (currentTask == GooseTasks.GooseTask.BeingPetted ||
            currentTask == GooseTasks.GooseTask.BeingDragged) return;

        // Priority-based decision tree
        int currentPriority = getTaskPriority(currentTask);

        // CRITICAL: Force sleep if exhausted
        if (PetNeeds.get().energy < 10 && currentTask != GooseTasks.GooseTask.Sleeping) {
            if (PRIORITY_CRITICAL > currentPriority) {
                setTask(GooseTasks.GooseTask.Sleeping, false);
                touch.showEmoji("ZZZ");
                return;
            }
        }

        // HIGH: React to mood
        PetNeeds.MoodState mood = PetNeeds.get().getMoodState();
        if (mood == PetNeeds.MoodState.SAD && currentTask != GooseTasks.GooseTask.Sad) {
            if (PRIORITY_HIGH > currentPriority) {
                setTask(GooseTasks.GooseTask.Sad, false);
                touch.showEmoji(":(");
                return;
            }
        }

        // MEDIUM: Seek attention if ignored
        if (memory.hasBeenIgnoredTooLong() && currentTask == GooseTasks.GooseTask.Wander
                && Time.time - lastSeekingTime > SEEKING_COOLDOWN_SECONDS) {
            lastSeekingTime = Time.time;
            setTask(GooseTasks.GooseTask.Seeking, false);
            touch.showEmoji("?");
            memory.consecutiveIgnoredCalls++;
            return;
        }

        // MEDIUM: Urgent needs
        if (PetNeeds.get().needsUrgentAttention() && currentTask == GooseTasks.GooseTask.Wander
                && Time.time - lastSeekingTime > SEEKING_COOLDOWN_SECONDS) {
            lastSeekingTime = Time.time;
            {
                setTask(GooseTasks.GooseTask.Seeking, false);
                touch.showEmoji("!");
                return;
            }
        }

        // LOW: Spontaneous happiness
        if (PetNeeds.get().happiness > 80 && currentTask == GooseTasks.GooseTask.Wander) {
            if (Math.random() < 0.005) {
                setTask(GooseTasks.GooseTask.Happy, false);
                return;
            }
        }
    }

    private int getTaskPriority(GooseTasks.GooseTask task) {
        switch (task) {
            case Sleeping:
                return PRIORITY_CRITICAL;
            case BeingPetted:
            case BeingDragged:
                return PRIORITY_HIGH;
            case Eating:
            case Playing:
            case Sad:
            case Seeking:
                return PRIORITY_MEDIUM;
            case Happy:
            case MudDash:
                return PRIORITY_LOW;
            default:
                return PRIORITY_IDLE;
        }
    }

    // ============== ROUTINE SYSTEM ==============

    private void updateRoutine(float deltaTime) {
        routineCheckTimer += deltaTime;
        if (routineCheckTimer > 60f) { // Check every minute
            routineCheckTimer = 0;
            updateDayPeriod();
            applyRoutineBehavior();
        }
    }

    private void updateDayPeriod() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        if (hour >= 6 && hour < 12) {
            currentPeriod = DayPeriod.MORNING;
        } else if (hour >= 12 && hour < 18) {
            currentPeriod = DayPeriod.AFTERNOON;
        } else if (hour >= 18 && hour < 22) {
            currentPeriod = DayPeriod.EVENING;
        } else {
            currentPeriod = DayPeriod.NIGHT;
        }
    }

    private void applyRoutineBehavior() {
        if (currentTask != GooseTasks.GooseTask.Wander) return;

        GooseTouchHandler touch = callback.getTouchHandler();

        switch (currentPeriod) {
            case MORNING:
                // More energetic in the morning
                if (Math.random() < 0.1 && PetNeeds.get().energy > 50) {
                    triggerRandomEvent(RandomEvent.STRETCH);
                    touch.showEmoji(":)");
                }
                break;
            case AFTERNOON:
                // Normal behavior
                break;
            case EVENING:
                // Start getting sleepy
                if (Math.random() < 0.05) {
                    triggerRandomEvent(RandomEvent.YAWN);
                }
                break;
            case NIGHT:
                // Very sleepy, may fall asleep spontaneously
                if (Math.random() < 0.1 && PetNeeds.get().energy < 60) {
                    setTask(GooseTasks.GooseTask.Sleeping, false);
                    touch.showEmoji("ZZZ");
                }
                break;
        }
    }

    // ============== RANDOM EVENTS ==============

    private void updateRandomEvents(float deltaTime) {
        nextEventCheck -= deltaTime;
        if (nextEventCheck <= 0) {
            // Check more frequently (every 3-8 seconds)
            nextEventCheck = MathUtil.randomRange(3f, 8f);
            maybeStartRandomEvent();
        }

        // Show random thoughts more frequently based on boredom
        float thoughtChance = 0.005f + GooseLLM.getBoredom() * 0.01f;
        if (activeEvent == RandomEvent.NONE && Math.random() < thoughtChance) {
            showRandomThought();
        }
    }

    /**
     * Show a random thought bubble while idle.
     * Uses the advanced GooseLLM local AI system.
     */
    private void showRandomThought() {
        if (callback == null || callback.getTouchHandler() == null) return;

        // Check for milestone thoughts first
        String milestoneThought = GooseLLM.getMilestoneThought();
        if (milestoneThought != null) {
            callback.getTouchHandler().showEmoji(milestoneThought);
            return;
        }

        // Check for favorite time thoughts
        String favoriteTimeThought = GooseLLM.getFavoriteTimeThought();
        if (favoriteTimeThought != null && Math.random() < 0.3f) {
            callback.getTouchHandler().showEmoji(favoriteTimeThought);
            return;
        }

        // Use the advanced local AI system
        GooseLLM.generateThought(null, thought -> {
            if (thought != null && !thought.isEmpty()) {
                callback.getTouchHandler().showEmoji(thought);
            }
        });
    }

    /**
     * Get thoughts based on current context (time, needs, etc.)
     */
    private String[] getContextualThoughts() {
        java.util.List<String> thoughts = new java.util.ArrayList<>();

        // Time-based
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 6 && hour < 9) {
            thoughts.add("morning!");
            thoughts.add("*yawn*");
            thoughts.add("coffee?");
        } else if (hour >= 12 && hour < 14) {
            thoughts.add("lunch?");
            thoughts.add("hungry!");
            thoughts.add("food time?");
        } else if (hour >= 22 || hour < 6) {
            thoughts.add("sleepy...");
            thoughts.add("*yawn*");
            thoughts.add("bedtime?");
            thoughts.add("zzz...");
        }

        // Needs-based
        if (PetNeeds.get().hunger > 60) {
            thoughts.add("BREAD!");
            thoughts.add("feed me!");
            thoughts.add("snack?");
        }
        if (PetNeeds.get().energy < 40) {
            thoughts.add("tired...");
            thoughts.add("nap time?");
        }
        if (PetNeeds.get().happiness < 40) {
            thoughts.add("lonely...");
            thoughts.add("pet me?");
            thoughts.add(":(");
        }
        if (PetNeeds.get().happiness > 80) {
            thoughts.add("HAPPY!");
            thoughts.add("best day!");
            thoughts.add("love u!");
        }

        // Memory-based
        if (memory.timeSinceLastInteraction > 60) {
            thoughts.add("hello?");
            thoughts.add("notice me!");
            thoughts.add("*poke*");
        }

        return thoughts.toArray(new String[0]);
    }

    private void maybeStartRandomEvent() {
        // Allow events in more states, not just Wander
        if (currentTask == GooseTasks.GooseTask.Sleeping) return;
        if (currentTask == GooseTasks.GooseTask.BeingDragged) return;
        if (!callback.isPetModeEnabled()) return;

        // Higher probability of events (40% base + personality bonus)
        float eventChance = 0.40f + (PetPersonality.get().playfulness / 200f);

        if (Math.random() < eventChance) {
            RandomEvent event = pickRandomEvent();
            triggerRandomEvent(event);
        }
    }

    private RandomEvent pickRandomEvent() {
        float rand = (float) Math.random();

        // Weight events by personality and energy
        if (PetNeeds.get().energy < 30) {
            // Tired - prefer calm events
            if (rand < 0.4f) return RandomEvent.YAWN;
            if (rand < 0.6f) return RandomEvent.STRETCH;
            return RandomEvent.LOOK_AROUND;
        }

        if (PetPersonality.get().playfulness > 50) {
            // Playful - prefer active events
            if (rand < 0.15f) return RandomEvent.ZOOMIES;
            if (rand < 0.25f) return RandomEvent.SPIN;
            if (rand < 0.35f) return RandomEvent.DANCE;
            if (rand < 0.45f) return RandomEvent.JUMP_SCARE;
        }

        // Default distribution with new events
        if (rand < 0.08f) return RandomEvent.RANDOM_HONK;
        if (rand < 0.14f) return RandomEvent.LOOK_AROUND;
        if (rand < 0.20f) return RandomEvent.CURIOUS_TILT;
        if (rand < 0.26f) return RandomEvent.SHAKE;
        if (rand < 0.32f) return RandomEvent.WING_FLAP;
        if (rand < 0.38f) return RandomEvent.STRETCH;
        if (rand < 0.44f) return RandomEvent.YAWN;
        if (rand < 0.50f) return RandomEvent.SNEEZE;
        if (rand < 0.56f) return RandomEvent.SPIN;

        // New events (mischief-influenced)
        float mischiefBonus = PetPersonality.get().mischief / 200f;
        if (rand < 0.60f + mischiefBonus) return RandomEvent.SINGING;
        if (rand < 0.65f + mischiefBonus) return RandomEvent.PLAY_DEAD;
        if (rand < 0.70f + mischiefBonus) return RandomEvent.LAY_EGG;
        if (rand < 0.75f + mischiefBonus) return RandomEvent.FIGHT_REFLECTION;
        if (rand < 0.80f + mischiefBonus) return RandomEvent.CHASE_INVISIBLE;
        if (rand < 0.85f + mischiefBonus) return RandomEvent.MOONWALK;
        if (rand < 0.90f + mischiefBonus) return RandomEvent.BELLY_FLOP;
        if (rand < 0.95f + mischiefBonus) return RandomEvent.TROLL_NOTIFICATION;
        return RandomEvent.VIBRATE_HONK;
    }

    public void triggerRandomEvent(RandomEvent event) {
        activeEvent = event;
        eventTimer = 0;

        switch (event) {
            case RANDOM_HONK:
                eventDuration = 0.5f;
                Sound.honk();
                break;
            case ZOOMIES:
                eventDuration = 3f;
                callback.getPhysics().setSpeed(GooseTasks.SpeedTier.Charge);
                break;
            case SPIN:
                eventDuration = 1.5f;
                break;
            case SHAKE:
                eventDuration = 1f;
                break;
            case LOOK_AROUND:
                eventDuration = 2f;
                break;
            case JUMP_SCARE:
                eventDuration = 0.8f;
                Sound.honk();
                break;
            case DANCE:
                eventDuration = 2.5f;
                break;
            case SNEEZE:
                eventDuration = 1f;
                Sound.honk();
                break;
            case YAWN:
                eventDuration = 2f;
                break;
            case STRETCH:
                eventDuration = 2f;
                break;
            case CURIOUS_TILT:
                eventDuration = 1.5f;
                break;
            case WING_FLAP:
                eventDuration = 1.2f;
                break;

            // NEW EVENTS
            case SINGING:
                eventDuration = 3f;
                // Play melodic honk sequence
                playSingingSequence();
                break;
            case PLAY_DEAD:
                eventDuration = 4f;
                callback.getTouchHandler().showEmoji("X_X");
                break;
            case LAY_EGG:
                eventDuration = 2f;
                callback.getTouchHandler().showEmoji("EGG!");
                Sound.pat();
                break;
            case FIGHT_REFLECTION:
                eventDuration = 3f;
                callback.getTouchHandler().showEmoji("FIGHT!");
                Sound.honk();
                break;
            case COSTUME_CHANGE:
                eventDuration = 1f;
                randomizeCostume();
                callback.getTouchHandler().showEmoji("NEW!");
                break;
            case CHASE_INVISIBLE:
                eventDuration = 2.5f;
                callback.getPhysics().setSpeed(GooseTasks.SpeedTier.Run);
                callback.getTouchHandler().showEmoji("?!");
                break;
            case MOONWALK:
                eventDuration = 2f;
                callback.getTouchHandler().showEmoji("SMOOTH");
                break;
            case BELLY_FLOP:
                eventDuration = 1.5f;
                Sound.pat();
                callback.getTouchHandler().showEmoji("FLOP!");
                break;
            case TROLL_NOTIFICATION:
                eventDuration = 0.5f;
                GooseTrolling.trySendTrollNotification();
                callback.getTouchHandler().showEmoji(">:)");
                break;
            case VIBRATE_HONK:
                eventDuration = 1f;
                Sound.honk();
                GooseTrolling.vibrateHonk();
                break;
            default:
                eventDuration = 1f;
                break;
        }
    }

    /**
     * Play a melodic sequence of honks for SINGING event.
     */
    private void playSingingSequence() {
        callback.getTouchHandler().showEmoji("LA LA LA");
        // Schedule honks at musical intervals
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> Sound.honk(), 0);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> Sound.honk(), 400);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> Sound.honk(), 800);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> Sound.honk(), 1000);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> Sound.honk(), 1400);
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> Sound.happy(), 2000);
    }

    /**
     * Randomize costume for COSTUME_CHANGE event.
     */
    private void randomizeCostume() {
        int newHat = (int)(Math.random() * 5); // 0-4 (0 = none)
        int newAccessory = (int)(Math.random() * 5); // 0-4 (0 = none)
        com.skyvanguard.goozy.PetAppearance.get().hatId = newHat;
        com.skyvanguard.goozy.PetAppearance.get().accessoryId = newAccessory;
    }

    private void runRandomEvent(float deltaTime) {
        GoosePhysics physics = callback.getPhysics();
        float progress = eventTimer / eventDuration;

        switch (activeEvent) {
            case ZOOMIES:
                // Run around crazily
                if (eventTimer % 0.5f < deltaTime) {
                    physics.setTargetPos(new Vec2(
                        MathUtil.randomRange(50f, callback.getScreenWidth() - 50f),
                        MathUtil.randomRange(50f, callback.getScreenHeight() - 50f)
                    ));
                }
                break;

            case SPIN:
                // Spin in place
                float spinAngle = progress * 720f; // Two full rotations
                physics.setTargetPos(Vec2.add(
                    physics.getPosition(),
                    Vec2.mul(Vec2.fromDegrees(spinAngle), 5f)
                ));
                physics.setVelocity(Vec2.ZERO);
                break;

            case SHAKE:
                // Shake body (handled by renderer using this state)
                physics.setVelocity(Vec2.ZERO);
                break;

            case LOOK_AROUND:
                // Look left and right
                physics.setVelocity(Vec2.ZERO);
                overrideExtendNeck = true;
                break;

            case DANCE:
                // Little dance - side to side
                float danceOffset = (float) Math.sin(progress * Math.PI * 6) * 20f;
                physics.setTargetPos(Vec2.add(
                    physics.getPosition(),
                    new Vec2(danceOffset, 0)
                ));
                break;

            case JUMP_SCARE:
                // Quick jump
                if (progress < 0.5f) {
                    overrideExtendNeck = true;
                }
                break;

            case YAWN:
            case STRETCH:
                // Stay still, animation handled by renderer
                physics.setVelocity(Vec2.ZERO);
                overrideExtendNeck = progress > 0.3f && progress < 0.7f;
                break;

            case CURIOUS_TILT:
                // Head tilt
                physics.setVelocity(Vec2.ZERO);
                break;

            case WING_FLAP:
                // Stay still, animation handled
                physics.setVelocity(Vec2.ZERO);
                break;

            // NEW EVENT BEHAVIORS
            case SINGING:
                // Slight bobbing while singing
                physics.setVelocity(Vec2.ZERO);
                overrideExtendNeck = (int)(progress * 10) % 2 == 0;
                break;

            case PLAY_DEAD:
                // Fall over and stay still
                physics.setVelocity(Vec2.ZERO);
                // Occasional twitch to show "not really dead"
                if (progress > 0.7f && Math.random() < 0.1f) {
                    callback.getTouchHandler().showEmoji("...");
                }
                break;

            case LAY_EGG:
                // Crouch and concentrate
                physics.setVelocity(Vec2.ZERO);
                if (progress > 0.8f) {
                    // "Produced" the egg
                    callback.getTouchHandler().showEmoji("TADA!");
                }
                break;

            case FIGHT_REFLECTION:
                // Aggressive movements toward nothing
                float fightAngle = progress * 360f * 3f;
                physics.setTargetPos(Vec2.add(
                    physics.getPosition(),
                    Vec2.mul(Vec2.fromDegrees(fightAngle), 30f)
                ));
                if (Math.random() < 0.1f) {
                    Sound.honk();
                }
                overrideExtendNeck = Math.random() < 0.5f;
                break;

            case CHASE_INVISIBLE:
                // Run erratically
                if (eventTimer % 0.3f < deltaTime) {
                    physics.setTargetPos(new Vec2(
                        MathUtil.randomRange(100f, callback.getScreenWidth() - 100f),
                        MathUtil.randomRange(100f, callback.getScreenHeight() - 100f)
                    ));
                }
                break;

            case MOONWALK:
                // Walk backwards (reverse direction)
                Vec2 moonwalkDir = Vec2.fromDegrees(physics.getDirection() + 180f);
                physics.setTargetPos(Vec2.add(
                    physics.getPosition(),
                    Vec2.mul(moonwalkDir, 50f)
                ));
                physics.setSpeed(GooseTasks.SpeedTier.Walk);
                break;

            case BELLY_FLOP:
                // Quick forward lunge then stop
                if (progress < 0.3f) {
                    physics.setSpeed(GooseTasks.SpeedTier.Run);
                    physics.setTargetPos(Vec2.add(
                        physics.getPosition(),
                        Vec2.mul(Vec2.fromDegrees(physics.getDirection()), 100f)
                    ));
                } else {
                    physics.setVelocity(Vec2.ZERO);
                }
                break;

            case TROLL_NOTIFICATION:
            case VIBRATE_HONK:
                // Mischievous look
                physics.setVelocity(Vec2.ZERO);
                break;

            default:
                break;
        }
    }

    private void endRandomEvent() {
        switch (activeEvent) {
            case ZOOMIES:
            case CHASE_INVISIBLE:
            case MOONWALK:
            case BELLY_FLOP:
                callback.getPhysics().setSpeed(GooseTasks.SpeedTier.Walk);
                break;
            default:
                break;
        }
        activeEvent = RandomEvent.NONE;
        eventTimer = 0;
    }

    // ============== CURIOSITY SYSTEM ==============

    private void updateCuriosity(float deltaTime) {
        if (!callback.isPetModeEnabled()) return;
        if (currentTask != GooseTasks.GooseTask.Wander) {
            isExploring = false;
            return;
        }

        curiosityTimer += deltaTime;

        // Occasionally get curious about a new area
        if (curiosityTimer > 30f && !isExploring) {
            curiosityTimer = 0;

            if (Math.random() < 0.2f + (PetPersonality.get().bravery / 300f)) {
                startExploring();
            }
        }

        if (isExploring && curiosityTarget != null) {
            GoosePhysics physics = callback.getPhysics();
            float dist = Vec2.dist(physics.getPosition(), curiosityTarget);

            if (dist < 30f) {
                // Reached target, remember this spot
                memory.addFavoriteSpot(curiosityTarget);
                memory.lastExploredArea = curiosityTarget;
                isExploring = false;

                // Show curiosity satisfied
                callback.getTouchHandler().showEmoji("!");
            }
        }
    }

    private void startExploring() {
        GoosePhysics physics = callback.getPhysics();
        int screenWidth = callback.getScreenWidth();
        int screenHeight = callback.getScreenHeight();

        // Pick an unexplored area (corners, edges)
        float rand = (float) Math.random();
        if (rand < 0.25f) {
            // Top-left corner
            curiosityTarget = new Vec2(100, 100);
        } else if (rand < 0.5f) {
            // Top-right corner
            curiosityTarget = new Vec2(screenWidth - 100, 100);
        } else if (rand < 0.75f) {
            // Bottom-left corner
            curiosityTarget = new Vec2(100, screenHeight - 100);
        } else {
            // Bottom-right corner
            curiosityTarget = new Vec2(screenWidth - 100, screenHeight - 100);
        }

        // Avoid recently explored areas
        if (memory.lastExploredArea != null) {
            if (Vec2.dist(curiosityTarget, memory.lastExploredArea) < 200) {
                // Pick center instead
                curiosityTarget = new Vec2(screenWidth / 2f, screenHeight / 2f);
            }
        }

        physics.setTargetPos(curiosityTarget);
        isExploring = true;
        callback.getTouchHandler().showEmoji("?");
    }

    // ============== TASK IMPLEMENTATIONS ==============

    // Wander state for expressions
    private double lastWanderExpressionTime = 0;
    private float nextWanderExpressionInterval = 2f;
    private int wanderActionCount = 0;

    private void runStroll() {
        GoosePhysics physics = callback.getPhysics();
        if (Time.time > stroll.endsAt) {
            chooseNextTask();
            return;
        }

        // Expresarse mientras pasea (cada 2-5 segundos)
        if (Time.time - lastWanderExpressionTime > nextWanderExpressionInterval) {
            lastWanderExpressionTime = Time.time;
            nextWanderExpressionInterval = MathUtil.randomRange(2f, 5f);
            doWanderExpression();
        }

        if (stroll.pauseUntil >= 0) {
            if (Time.time < stroll.pauseUntil) {
                physics.setVelocity(Vec2.ZERO);
                if (Math.random() < 0.02f) doPauseAction();
                return;
            }
            stroll.pauseUntil = -1;
            startStrollLeg(physics);
            return;
        }

        if (Vec2.dist(physics.getPosition(), physics.getTargetPos())
                < GooseTasks.Stroll.ARRIVED_DISTANCE) {
            stroll.pauseUntil = Time.time + GooseTasks.Stroll.pauseSeconds();
            wanderActionCount++;
        }
    }

    /** Elige el próximo destino del paseo: un lugar favorito o un punto al azar, no muy lejos. */
    private void startStrollLeg(GoosePhysics physics) {
        Vec2 target;
        if (!memory.favoriteSpots.isEmpty() && Math.random() < 0.3f) {
            target = memory.favoriteSpots.get((int) (Math.random() * memory.favoriteSpots.size()));
        } else {
            float margin = STROLL_SCREEN_MARGIN;
            target = new Vec2(
                MathUtil.randomRange(margin, callback.getScreenWidth() - margin),
                MathUtil.randomRange(margin, callback.getScreenHeight() - margin));
        }
        float reach = GooseTasks.Stroll.legSeconds() * physics.getMaxSpeed();
        Vec2 toTarget = Vec2.sub(target, physics.getPosition());
        if (Vec2.length(toTarget) > reach) {
            target = Vec2.add(physics.getPosition(), Vec2.mul(Vec2.normalize(toTarget), reach));
        }
        physics.setTargetPos(target);
        if (Math.random() < 0.15f) {
            physics.setSpeed(Math.random() < 0.5f ? GooseTasks.SpeedTier.Run : GooseTasks.SpeedTier.Walk);
        }
    }

    /**
     * Do an expression/action while wandering.
     */
    private void doWanderExpression() {
        if (callback == null || callback.getTouchHandler() == null) return;

        float rand = (float) Math.random();

        // Contextual expressions based on state
        if (PetNeeds.get().hunger > 70 && rand < 0.2f) {
            callback.getTouchHandler().showEmoji("hungry");
            return;
        }
        if (PetNeeds.get().energy < 30 && rand < 0.2f) {
            callback.getTouchHandler().showEmoji("sleepy...");
            return;
        }
        if (PetNeeds.get().happiness > 80 && rand < 0.3f) {
            String[] happyEmojis = {":D", "YAY", "<3", "^_^", "hehe"};
            callback.getTouchHandler().showEmoji(happyEmojis[(int)(Math.random() * happyEmojis.length)]);
            return;
        }

        // Random expressions
        if (rand < 0.1f) {
            // Look around
            overrideExtendNeck = true;
            callback.getTouchHandler().showEmoji("?");
        } else if (rand < 0.15f) {
            // Honk for no reason
            Sound.honk();
            callback.getTouchHandler().showEmoji("HONK!");
        } else if (rand < 0.25f) {
            // Show a thought
            showRandomThought();
        } else if (rand < 0.3f) {
            // Small reaction
            String[] reactions = {"oh!", "hmm", "~", "...", "*waddle*"};
            callback.getTouchHandler().showEmoji(reactions[(int)(Math.random() * reactions.length)]);
        }
    }

    /**
     * Do something while paused.
     */
    private void doPauseAction() {
        if (callback == null) return;

        float rand = (float) Math.random();

        if (rand < 0.2f) {
            // Peck at ground
            overrideExtendNeck = true;
            callback.getTouchHandler().showEmoji("*peck*");
        } else if (rand < 0.35f) {
            // Look around
            callback.getTouchHandler().showEmoji("?");
        } else if (rand < 0.45f) {
            // Preen feathers
            callback.getTouchHandler().showEmoji("*preen*");
        } else if (rand < 0.55f) {
            // Honk
            Sound.honk();
        } else if (rand < 0.65f) {
            // Shake
            triggerRandomEvent(RandomEvent.SHAKE);
        } else if (rand < 0.75f) {
            // Stretch
            overrideExtendNeck = true;
            callback.getTouchHandler().showEmoji("stretch~");
        }
    }



    private void runFetchNote() {
        GoosePhysics physics = callback.getPhysics();
        switch (fetchNote.step) {
            case TO_EDGE:
                if (hasReachedTargetOrTimedOut(physics)) {
                    fetchNote.awayUntil = Time.time + GooseTasks.FetchNote.awaySeconds();
                    fetchNote.step = GooseTasks.FetchNote.Step.AWAY;
                }
                break;
            case AWAY:
                if (Time.time >= fetchNote.awayUntil) {
                    physics.setTargetPos(returnPointFrom(fetchNote.edge, physics.getPosition()));
                    stageStartTime = Time.time;
                    fetchNote.step = GooseTasks.FetchNote.Step.BRING_BACK;
                    pickUpNote(physics, fetchNote.edge);
                }
                break;
            case BRING_BACK:
                if (hasReachedTargetOrTimedOut(physics)) {
                    // Suelta la nota y se aleja unos pasos para que se vea
                    physics.setTargetPos(Vec2.add(physics.getPosition(),
                        Vec2.mul(Vec2.fromDegrees(physics.getDirection() + 180f), NOTE_STEP_BACK)));
                    setTask(GooseTasks.GooseTask.Wander, false);
                } else {
                    overrideExtendNeck = true;
                }
                break;
        }
    }

    /** Punto hacia adentro de la pantalla para volver desde un borde. */
    private Vec2 returnPointFrom(GooseTasks.Edge edge, Vec2 from) {
        float width = callback.getScreenWidth();
        float height = callback.getScreenHeight();
        float depth = MathUtil.randomRange(0.2f, 0.35f);
        switch (edge) {
            case TOP:
                return new Vec2(from.x, height * depth);
            case RIGHT:
                return new Vec2(width * (1f - depth), MathUtil.lerp(from.y, height / 2f, 0.3f));
            default:
                return new Vec2(width * depth, MathUtil.lerp(from.y, height / 2f, 0.3f));
        }
    }

    private void runMudDash() {
        GoosePhysics physics = callback.getPhysics();
        switch (mudDash.step) {
            case RUN_OUT:
                if (hasReachedTargetOrTimedOut(physics)) {
                    physics.setMuddyFeetUntil(Time.time + GooseTasks.MudDash.MUDDY_FEET_SECONDS);
                    PetNeeds.get().soil(PetNeeds.MUD_SOIL_AMOUNT);
                    Sound.mud();
                    mudDash.endsAt = Time.time + GooseTasks.MudDash.ZIGZAG_SECONDS;
                    mudDash.nextTurnAt = 0;
                    mudDash.step = GooseTasks.MudDash.Step.ZIGZAG;
                }
                break;
            case ZIGZAG:
                if (Time.time >= mudDash.nextTurnAt
                        || Vec2.dist(physics.getPosition(), physics.getTargetPos()) < 5f) {
                    physics.setTargetPos(new Vec2(
                        MathUtil.randomRange(0f, callback.getScreenWidth()),
                        MathUtil.randomRange(0f, callback.getScreenHeight())));
                    mudDash.nextTurnAt = Time.time + GooseTasks.MudDash.TURN_SECONDS;
                }
                if (Time.time > mudDash.endsAt) {
                    setTask(GooseTasks.GooseTask.Wander, false);
                }
                break;
        }
    }

    // ============== PET STATE IMPLEMENTATIONS ==============

    private void runSleeping() {
        GoosePhysics physics = callback.getPhysics();

        if (sleepStartTime == 0) {
            sleepStartTime = Time.time;
            physics.setVelocity(Vec2.ZERO);
            // Adjust duration by tiredness
            sleepDuration = 5f + (100f - PetNeeds.get().energy) * 0.05f;
        }

        physics.setVelocity(Vec2.ZERO);
        PetNeeds.get().energy = Math.min(100, PetNeeds.get().energy + 0.5f * Time.deltaTime);

        if (Time.time - sleepStartTime > sleepDuration || PetNeeds.get().energy >= 100) {
            PetNeeds.get().sleep();
            memory.recordSleep();
            sleepStartTime = 0;
            callback.getTouchHandler().showEmoji(":)");
            setTask(GooseTasks.GooseTask.Wander, false);
        }
    }

    private void runEating() {
        GoosePhysics physics = callback.getPhysics();

        if (eatStartTime == 0) {
            eatStartTime = Time.time;
            physics.setVelocity(Vec2.ZERO);
            // Adjust duration by hunger
            eatDuration = 2f + PetNeeds.get().hunger * 0.02f;
        }

        physics.setVelocity(Vec2.ZERO);
        overrideExtendNeck = true;

        // Pecking animation timing
        if ((int)(Time.time * 4) % 2 == 0) {
            overrideExtendNeck = false;
        }

        if (Time.time - eatStartTime > eatDuration) {
            PetNeeds.get().feed();
            memory.recordFeed();
            eatStartTime = 0;
            callback.getTouchHandler().showEmoji("YUM");
            Sound.pat();
            setTask(GooseTasks.GooseTask.Wander, false);
        }
    }

    private void runPlaying() {
        GoosePhysics physics = callback.getPhysics();

        if (playStartTime == 0) {
            playStartTime = Time.time;
            physics.setSpeed(GooseTasks.SpeedTier.Run);
            // Adjust duration by energy
            playDuration = 3f + PetNeeds.get().energy * 0.02f;
        }

        float elapsed = (float) (Time.time - playStartTime);
        float angle = elapsed * 180f;
        physics.setTargetPos(Vec2.add(
            physics.getPosition(),
            Vec2.mul(Vec2.fromDegrees(angle), 50f)
        ));

        // Occasionally honk while playing
        if (Math.random() < 0.01) {
            Sound.honk();
        }

        if (elapsed > playDuration) {
            PetNeeds.get().play();
            memory.recordPlay();
            playStartTime = 0;
            callback.getTouchHandler().showEmoji(":D");
            setTask(GooseTasks.GooseTask.Wander, false);
        }
    }

    private float sadTime = 0;
    private int sadExpressionCount = 0;

    private void runSad() {
        GoosePhysics physics = callback.getPhysics();
        physics.setSpeed(GooseTasks.SpeedTier.Walk);
        sadTime += Time.deltaTime;

        // Move slowly and aimlessly
        if (Math.random() < 0.01) {
            physics.setTargetPos(Vec2.add(
                physics.getPosition(),
                new Vec2(MathUtil.randomRange(-30f, 30f), MathUtil.randomRange(-30f, 30f))
            ));
        }

        // Varied sad expressions
        if (sadTime > 2f && sadExpressionCount == 0) {
            callback.getTouchHandler().showEmoji(":(");
            sadExpressionCount++;
        } else if (sadTime > 5f && sadExpressionCount == 1) {
            callback.getTouchHandler().showEmoji("lonely...");
            sadExpressionCount++;
        } else if (sadTime > 8f && sadExpressionCount == 2) {
            callback.getTouchHandler().showEmoji("*sigh*");
            sadExpressionCount++;
        } else if (Math.random() < 0.008) {
            String[] sadEmojis = {":(", "T_T", "*sniff*", "why...", "sad goose"};
            callback.getTouchHandler().showEmoji(sadEmojis[(int)(Math.random() * sadEmojis.length)]);
        }

        // Occasionally stop and look around hoping for attention
        if (Math.random() < 0.02) {
            physics.setVelocity(Vec2.ZERO);
            callback.getTouchHandler().showEmoji("?");
        }

        if (PetNeeds.get().happiness > 40) {
            sadTime = 0;
            sadExpressionCount = 0;
            callback.getTouchHandler().showEmoji(":)");
            setTask(GooseTasks.GooseTask.Wander, false);
        }
    }

    private int happyActionCount = 0;

    private void runHappy() {
        GoosePhysics physics = callback.getPhysics();

        if (happyStartTime == 0) {
            happyStartTime = Time.time;
            happyActionCount = 0;
            physics.setSpeed(GooseTasks.SpeedTier.Run);
            callback.getTouchHandler().showEmoji(":D");
            Sound.happy();
        }

        float elapsed = (float) (Time.time - happyStartTime);

        // Varied happy actions
        if (elapsed > 0.5f && happyActionCount == 0) {
            triggerRandomEvent(RandomEvent.SPIN);
            happyActionCount++;
        } else if (elapsed > 1.5f && happyActionCount == 1) {
            callback.getTouchHandler().showEmoji("WHEEE!");
            Sound.honk();
            happyActionCount++;
        } else if (elapsed > 2.5f && happyActionCount == 2) {
            callback.getTouchHandler().showEmoji("<3");
            happyActionCount++;
        }

        // Run around excitedly
        if (Math.random() < 0.03) {
            physics.setTargetPos(Vec2.add(
                physics.getPosition(),
                new Vec2(MathUtil.randomRange(-100f, 100f), MathUtil.randomRange(-100f, 100f))
            ));
        }

        // Happy honks and expressions
        if (Math.random() < 0.03) {
            Sound.honk();
            String[] happyEmojis = {":D", "YAY!", "^_^", "hehe!", "WOOP!"};
            callback.getTouchHandler().showEmoji(happyEmojis[(int)(Math.random() * happyEmojis.length)]);
        }

        // Extended happy time
        if (elapsed > 4f) {
            happyStartTime = 0;
            happyActionCount = 0;
            callback.getTouchHandler().showEmoji(":)");
            setTask(GooseTasks.GooseTask.Wander, false);
        }
    }

    /**
     * Called when dragging ends - react to being released.
     */
    public void onDragEnd(Vec2 releasePosition) {
        if (callback == null) return;

        // React based on where released
        int screenWidth = callback.getScreenWidth();
        int screenHeight = callback.getScreenHeight();

        if (releasePosition.y < 100) {
            // Released at top
            callback.getTouchHandler().showEmoji("whoa!");
        } else if (releasePosition.y > screenHeight - 100) {
            // Released at bottom
            callback.getTouchHandler().showEmoji("oof!");
        } else if (releasePosition.x < 100 || releasePosition.x > screenWidth - 100) {
            // Released at edge
            callback.getTouchHandler().showEmoji("hey!");
        } else {
            // Normal release
            String[] releaseReactions = {"ok!", "wheee!", "zoom!", ":)", "*bounce*"};
            callback.getTouchHandler().showEmoji(releaseReactions[(int)(Math.random() * releaseReactions.length)]);
        }

        // Sometimes get dizzy
        if (Math.random() < 0.2f) {
            callback.getTouchHandler().showEmoji("dizzy~");
            triggerRandomEvent(RandomEvent.SPIN);
        }
    }

    private void runSeeking() {
        GoosePhysics physics = callback.getPhysics();
        physics.setSpeed(GooseTasks.SpeedTier.Walk);

        Vec2 screenCenter = new Vec2(
            callback.getScreenWidth() / 2f,
            callback.getScreenHeight() / 2f
        );
        physics.setTargetPos(screenCenter);

        if (Math.random() < 0.01) {
            Sound.honk();
            callback.getTouchHandler().showEmoji("!");
        }

        // Termina al llegar al centro o tras un tiempo máximo
        if (Vec2.dist(physics.getPosition(), screenCenter) < 100f
                || Time.time - lastSeekingTime > SEEKING_MAX_SECONDS) {
            setTask(GooseTasks.GooseTask.Wander, false);
        }
    }

    private static final float SEEKING_COOLDOWN_SECONDS = 90f;
    private static final float SEEKING_MAX_SECONDS = 10f;
    private double lastSeekingTime = -SEEKING_COOLDOWN_SECONDS;

    private float pettingTime = 0;
    private int pettingReactionCount = 0;

    private void runBeingPetted() {
        GoosePhysics physics = callback.getPhysics();
        physics.setVelocity(Vec2.ZERO);
        pettingTime += Time.deltaTime;

        // More varied reactions based on petting duration
        if (pettingTime > 1f && pettingReactionCount == 0) {
            callback.getTouchHandler().showEmoji(":)");
            pettingReactionCount++;
        } else if (pettingTime > 2.5f && pettingReactionCount == 1) {
            callback.getTouchHandler().showEmoji("<3");
            Sound.pat();
            pettingReactionCount++;
        } else if (pettingTime > 4f && pettingReactionCount == 2) {
            callback.getTouchHandler().showEmoji("LOVE!");
            Sound.happy();
            pettingReactionCount++;
        } else if (pettingTime > 6f && pettingReactionCount == 3) {
            // Extra happy - special reaction
            String[] superHappy = {"BEST!", "<3<3<3", "purr~", "bliss!", "heaven~"};
            callback.getTouchHandler().showEmoji(superHappy[(int)(Math.random() * superHappy.length)]);
            pettingReactionCount++;
        }

        // Occasional small reactions
        if (Math.random() < 0.03) {
            String[] petReactions = {"<3", ":)", "~", "mmm", "*happy*"};
            callback.getTouchHandler().showEmoji(petReactions[(int)(Math.random() * petReactions.length)]);
        }
    }

    /**
     * Called when petting ends.
     */
    public void onPettingEnd() {
        if (pettingTime > 2f) {
            // Show appreciation for good pet session
            callback.getTouchHandler().showEmoji("thanks!");
            PetNeeds.get().pet(); // Increase happiness
        }
        pettingTime = 0;
        pettingReactionCount = 0;
    }

    // ============== TASK MANAGEMENT ==============

    // La física limita la posición al área visible, así que "fuera de pantalla"
    // es el borde alcanzable, no una coordenada negativa.
    private static final float OFFSCREEN_EDGE_INSET = 24f;
    private static final float EDGE_REACHED_DISTANCE = 12f;
    private static final float STAGE_TIMEOUT_SECONDS = 12f;
    private static final float STROLL_SCREEN_MARGIN = 50f;
    private static final float NOTE_STEP_BACK = 40f;
    private double stageStartTime = 0f;

    private boolean hasReachedTargetOrTimedOut(GoosePhysics physics) {
        return Vec2.dist(physics.getPosition(), physics.getTargetPos()) < EDGE_REACHED_DISTANCE
            || Time.time - stageStartTime > STAGE_TIMEOUT_SECONDS;
    }

    /** Manda al ganso al borde más cercano (izquierdo, derecho o, si se permite, el de arriba). */
    private GooseTasks.Edge headToNearestEdge(boolean allowTop) {
        stageStartTime = Time.time;
        GoosePhysics physics = callback.getPhysics();
        float width = callback.getScreenWidth();
        float height = callback.getScreenHeight();
        Vec2 pos = physics.getPosition();

        float toLeft = pos.x;
        float toRight = width - pos.x;
        GooseTasks.Edge edge = toLeft <= toRight ? GooseTasks.Edge.LEFT : GooseTasks.Edge.RIGHT;
        float nearest = Math.min(toLeft, toRight);
        if (allowTop && pos.y < nearest) edge = GooseTasks.Edge.TOP;

        float midY = MathUtil.lerp(pos.y, height / 2f, 0.4f);
        switch (edge) {
            case TOP:
                physics.setTargetPos(new Vec2(MathUtil.lerp(pos.x, width / 2f, 0.4f), OFFSCREEN_EDGE_INSET));
                break;
            case RIGHT:
                physics.setTargetPos(new Vec2(width - OFFSCREEN_EDGE_INSET, midY));
                break;
            default:
                physics.setTargetPos(new Vec2(OFFSCREEN_EDGE_INSET, midY));
                break;
        }
        return edge;
    }

    private void chooseNextTask() {
        setTask(ERRANDS[errandPicker.next()], true);
    }

    /**
     * Deja en cero los timers de la tarea que se abandona. Sin esto, una tarea
     * interrumpida arranca la próxima vez con el reloj de la vez anterior y
     * termina en un frame.
     */
    /** La nota viene del borde por el que salió el ganso. */
    private static void pickUpNote(GoosePhysics physics, GooseTasks.Edge edge) {
        float dirX = edge == GooseTasks.Edge.LEFT ? -1f : (edge == GooseTasks.Edge.RIGHT ? 1f : 0f);
        float dirY = edge == GooseTasks.Edge.TOP ? -1f : 0f;
        GooseNotes.pickUp(dirX, dirY, physics.getPosition());
    }

    private void resetTaskTimers(GooseTasks.GooseTask task) {
        if (task == null) return;
        switch (task) {
            case FetchNote:
                // Terminó o la interrumpieron: la nota queda donde está
                TheGoose.onNoteTaskEnded();
                break;
            case Sleeping:
                sleepStartTime = 0;
                break;
            case Eating:
                eatStartTime = 0;
                break;
            case Playing:
                playStartTime = 0;
                break;
            case Happy:
                happyStartTime = 0;
                break;
            case Sad:
                sadTime = 0;
                sadExpressionCount = 0;
                break;
            case BeingPetted:
                onPettingEnd();
                break;
            default:
                break;
        }
    }

    public void setTask(GooseTasks.GooseTask task, boolean honk) {
        if (honk) {
            Sound.honk();
            memory.lastHonkTime = Time.time;
        }

        GooseTasks.GooseTask previousTask = currentTask;
        if (previousTask != task) {
            resetTaskTimers(previousTask);
            if (task == GooseTasks.GooseTask.BeingPetted) {
                // Una caricia por sesión de toque, no una por frame
                memory.recordPet();
            }
        }

        currentTask = task;
        GoosePhysics physics = callback.getPhysics();

        switch (task) {
            case Wander:
                physics.setSpeed(GooseTasks.SpeedTier.Walk);
                float scale = callback.isPetModeEnabled()
                    ? PetPersonality.get().getWanderDurationMultiplier() : 1f;
                stroll = new GooseTasks.Stroll(!hasStrolled, scale);
                hasStrolled = true;
                break;
            case FetchNote:
                fetchNote = new GooseTasks.FetchNote();
                fetchNote.edge = headToNearestEdge(false);
                TheGoose.onNoteTaskStarted();
                break;
            case MudDash:
                mudDash = new GooseTasks.MudDash();
                headToNearestEdge(true);
                physics.setSpeed(GooseTasks.SpeedTier.Run);
                break;
            default:
                break;
        }
    }

    // ============== PUBLIC TRIGGERS ==============

    public void startEating() {
        if (callback != null && callback.isPetModeEnabled()) {
            setTask(GooseTasks.GooseTask.Eating, false);
            callback.getTouchHandler().showEmoji("?");
        }
    }

    public void startPlaying() {
        if (callback != null && callback.isPetModeEnabled() && PetNeeds.get().energy > 20) {
            setTask(GooseTasks.GooseTask.Playing, false);
            callback.getTouchHandler().showEmoji(":D");
            Sound.honk();
        }
    }

    public void startSleeping() {
        if (callback != null && callback.isPetModeEnabled()) {
            setTask(GooseTasks.GooseTask.Sleeping, false);
            callback.getTouchHandler().showEmoji("ZZZ");
        }
    }

    /**
     * Set a target position for the goose to move towards.
     * Used by minigames and external triggers.
     */
    public void setTarget(Vec2 target) {
        if (target != null && callback != null && callback.getPhysics() != null) {
            callback.getPhysics().setTargetPos(target);
        }
    }

    /**
     * Force a random event (for testing or UI triggers).
     */
    public void forceRandomEvent(RandomEvent event) {
        if (activeEvent == RandomEvent.NONE) {
            triggerRandomEvent(event);
        }
    }
}
