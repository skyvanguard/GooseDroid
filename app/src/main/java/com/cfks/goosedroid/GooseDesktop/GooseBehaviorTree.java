package com.cfks.goosedroid.GooseDesktop;

import com.cfks.goosedroid.PetNeeds;
import com.cfks.goosedroid.PetPersonality;
import com.cfks.goosedroid.core.*;

/**
 * Behavior Tree implementation for the goose.
 * Creates intelligent, contextual behaviors based on needs, personality, and environment.
 */
public class GooseBehaviorTree {

    // ============== TREE STRUCTURE ==============

    private BehaviorTree tree;
    private BehaviorTree.Blackboard blackboard;
    private GooseAI.AICallback callback;

    // Current action tracking
    private String currentBehavior = "Idle";
    private double behaviorStartTime = 0f;

    private static final float CRITICAL_COOLDOWN_SECONDS = 12f;
    private static final String[] PET_STAGE_EMOJIS = {":)", "<3", "<3<3", "BLISS!"};
    private static final int NO_PET_STAGE = -1;
    private float petDuration = 0f;
    private int lastPetEmojiStage = NO_PET_STAGE;

    // ============== CONSTRUCTOR ==============

    public GooseBehaviorTree() {
        blackboard = new BehaviorTree.Blackboard();
        tree = new BehaviorTree(buildTree(), blackboard);
    }

    public void setCallback(GooseAI.AICallback callback) {
        this.callback = callback;
    }

    // ============== TREE BUILDING ==============

    private BehaviorTree.Node buildTree() {
        // Main tree: Priority-based selector
        return new BehaviorTree.Selector("Root")
            // Highest priority: Critical needs
            .addChild(buildCriticalNeedsBranch())
            // High priority: User interaction response
            .addChild(buildInteractionBranch())
            // Medium priority: Basic needs
            .addChild(buildNeedsBranch())
            // Low priority: Personality-driven behaviors
            .addChild(buildPersonalityBranch())
            // Default: Idle/wander behaviors
            .addChild(buildIdleBranch());
    }

    /**
     * Critical needs - must be addressed immediately.
     */
    private BehaviorTree.Node buildCriticalNeedsBranch() {
        return new BehaviorTree.Sequence("CriticalNeeds")
            // Check if any need is critical
            .addChild(new BehaviorTree.LambdaCondition("IsCritical", bb -> {
                return PetNeeds.get().energy < 10 || PetNeeds.get().hunger > 90 || PetNeeds.get().happiness < 10;
            }))
            // Handle critical need
            .addChild(new BehaviorTree.Selector("HandleCritical")
                // Exhausted - must sleep
                .addChild(new BehaviorTree.Sequence("Exhausted")
                    .addChild(new BehaviorTree.LambdaCondition("NoEnergy", bb -> PetNeeds.get().energy < 10))
                    .addChild(new BehaviorTree.Cooldown(
                        new BehaviorTree.LambdaAction("ForceSleep", bb -> {
                            showEmoji("ZZZ");
                            currentBehavior = "ForcedSleep";
                            setTask(GooseTasks.GooseTask.Sleeping);
                            return BehaviorTree.Status.SUCCESS;
                        }), CRITICAL_COOLDOWN_SECONDS)))
                // Starving - desperately seek food
                .addChild(new BehaviorTree.Sequence("Starving")
                    .addChild(new BehaviorTree.LambdaCondition("Starving", bb -> PetNeeds.get().hunger > 90))
                    .addChild(new BehaviorTree.Cooldown(
                        new BehaviorTree.LambdaAction("BegForFood", bb -> {
                            showEmoji("HUNGRY!");
                            Sound.honk();
                            currentBehavior = "BeggingForFood";
                            seekAttention();
                            return BehaviorTree.Status.SUCCESS;
                        }), CRITICAL_COOLDOWN_SECONDS)))
                // Depressed - need attention
                .addChild(new BehaviorTree.Sequence("Depressed")
                    .addChild(new BehaviorTree.LambdaCondition("VeryUnhappy", bb -> PetNeeds.get().happiness < 10))
                    .addChild(new BehaviorTree.Cooldown(
                        new BehaviorTree.LambdaAction("SeekComfort", bb -> {
                            showEmoji("T_T");
                            currentBehavior = "SeekingComfort";
                            setTask(GooseTasks.GooseTask.Sad);
                            return BehaviorTree.Status.SUCCESS;
                        }), CRITICAL_COOLDOWN_SECONDS))));
    }

    /**
     * User interaction responses.
     */
    private BehaviorTree.Node buildInteractionBranch() {
        return new BehaviorTree.Selector("Interactions")
            // Being petted
            .addChild(new BehaviorTree.Sequence("BeingPetted")
                .addChild(new BehaviorTree.LambdaCondition("IsPetted", bb ->
                    bb.getBool("isBeingPetted", false)))
                .addChild(new BehaviorTree.LambdaAction("EnjoyPets", bb -> {
                    float petDuration = bb.getFloat("petDuration", 0f);
                    currentBehavior = "EnjoyingPets";

                    // Reacción progresiva; el emoji solo se muestra al cambiar de etapa.
                    int stage = petDuration > 5f ? 3 : petDuration > 3f ? 2 : petDuration > 1f ? 1 : 0;
                    if (stage != lastPetEmojiStage) {
                        lastPetEmojiStage = stage;
                        showEmoji(PET_STAGE_EMOJIS[stage]);
                    }
                    // SUCCESS y no RUNNING: los composites guardan el hijo en curso y
                    // con RUNNING el árbol no volvía a evaluar ninguna otra rama.
                    return BehaviorTree.Status.SUCCESS;
                })))
            // Being dragged
            .addChild(new BehaviorTree.Sequence("BeingDragged")
                .addChild(new BehaviorTree.LambdaCondition("IsDragged", bb ->
                    bb.getBool("isBeingDragged", false)))
                .addChild(new BehaviorTree.LambdaAction("ReactToDrag", bb -> {
                    currentBehavior = "BeingDragged";
                    if (Math.random() < 0.02) {
                        String[] reactions = {"WHOA!", "HEY!", "wheee~", "!!!"};
                        showEmoji(reactions[(int)(Math.random() * reactions.length)]);
                    }
                    return BehaviorTree.Status.SUCCESS;
                })))
            // Recently interacted - show gratitude
            .addChild(new BehaviorTree.Sequence("RecentInteraction")
                .addChild(new BehaviorTree.LambdaCondition("JustInteracted", bb -> {
                    float timeSinceInteraction = bb.getFloat("timeSinceInteraction", 999f);
                    return timeSinceInteraction < 2f;
                }))
                .addChild(new BehaviorTree.LambdaAction("ShowGratitude", bb -> {
                    currentBehavior = "Grateful";
                    return BehaviorTree.Status.SUCCESS;
                })));
    }

    /**
     * Basic needs management.
     */
    private BehaviorTree.Node buildNeedsBranch() {
        return new BehaviorTree.Selector("BasicNeeds")
            // Tired - want to rest
            .addChild(new BehaviorTree.Sequence("Tired")
                .addChild(new BehaviorTree.LambdaCondition("LowEnergy", bb ->
                    PetNeeds.get().energy < 30 && !bb.getBool("isBeingPetted", false)))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.LambdaAction("WantToSleep", bb -> {
                        currentBehavior = "Tired";
                        if (Math.random() < 0.3f) {
                            showEmoji("*yawn*");
                            if (Math.random() < 0.2f) {
                                setTask(GooseTasks.GooseTask.Sleeping);
                            }
                        }
                        return BehaviorTree.Status.SUCCESS;
                    }), 10f)))
            // Hungry - seek food
            .addChild(new BehaviorTree.Sequence("Hungry")
                .addChild(new BehaviorTree.LambdaCondition("Hungry", bb -> PetNeeds.get().hunger > 60))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.LambdaAction("WantFood", bb -> {
                        currentBehavior = "Hungry";
                        String[] hungryEmojis = {"hungry...", "food?", "bread?", "*stomach growl*"};
                        showEmoji(hungryEmojis[(int)(Math.random() * hungryEmojis.length)]);
                        return BehaviorTree.Status.SUCCESS;
                    }), 15f)))
            // Lonely - want attention
            .addChild(new BehaviorTree.Sequence("Lonely")
                .addChild(new BehaviorTree.LambdaCondition("Lonely", bb -> {
                    float timeSinceInteraction = bb.getFloat("timeSinceInteraction", 0f);
                    return timeSinceInteraction > 60f && PetNeeds.get().happiness < 50;
                }))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.LambdaAction("SeekAttention", bb -> {
                        currentBehavior = "Lonely";
                        String[] lonelyEmojis = {"hello?", "pet me?", "*poke*", "notice me!"};
                        showEmoji(lonelyEmojis[(int)(Math.random() * lonelyEmojis.length)]);
                        seekAttention();
                        return BehaviorTree.Status.SUCCESS;
                    }), 20f)));
    }

    /**
     * Personality-driven behaviors.
     */
    private BehaviorTree.Node buildPersonalityBranch() {
        return new BehaviorTree.Selector("Personality")
            // Playful goose
            .addChild(new BehaviorTree.Sequence("PlayfulBehavior")
                .addChild(new BehaviorTree.LambdaCondition("IsPlayful", bb ->
                    PetPersonality.get().playfulness > 50 && PetNeeds.get().energy > 40))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.RandomSelector("PlayActions")
                        .addChild(new BehaviorTree.LambdaAction("Zoomies", bb -> {
                            currentBehavior = "Zoomies";
                            showEmoji("ZOOM!");
                            triggerEvent(GooseAI.RandomEvent.ZOOMIES);
                            return BehaviorTree.Status.SUCCESS;
                        }))
                        .addChild(new BehaviorTree.LambdaAction("Dance", bb -> {
                            currentBehavior = "Dancing";
                            showEmoji("~dance~");
                            triggerEvent(GooseAI.RandomEvent.DANCE);
                            return BehaviorTree.Status.SUCCESS;
                        }))
                        .addChild(new BehaviorTree.LambdaAction("Spin", bb -> {
                            currentBehavior = "Spinning";
                            showEmoji("wheee!");
                            triggerEvent(GooseAI.RandomEvent.SPIN);
                            return BehaviorTree.Status.SUCCESS;
                        })), 8f)))
            // Mischievous goose
            .addChild(new BehaviorTree.Sequence("MischievousBehavior")
                .addChild(new BehaviorTree.LambdaCondition("IsMischievous", bb ->
                    PetPersonality.get().mischief > 50 && PetNeeds.get().energy > 30))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.RandomSelector("MischiefActions")
                        .addChild(new BehaviorTree.LambdaAction("Plotting", bb -> {
                            currentBehavior = "Plotting";
                            showEmoji(">:)");
                            return BehaviorTree.Status.SUCCESS;
                        }))
                        .addChild(new BehaviorTree.LambdaAction("TrollNotification", bb -> {
                            currentBehavior = "Trolling";
                            showEmoji("hehehe");
                            triggerEvent(GooseAI.RandomEvent.TROLL_NOTIFICATION);
                            return BehaviorTree.Status.SUCCESS;
                        }))
                        .addChild(new BehaviorTree.LambdaAction("FightReflection", bb -> {
                            currentBehavior = "FightingReflection";
                            showEmoji("FIGHT!");
                            triggerEvent(GooseAI.RandomEvent.FIGHT_REFLECTION);
                            return BehaviorTree.Status.SUCCESS;
                        })), 12f)))
            // Affectionate goose
            .addChild(new BehaviorTree.Sequence("AffectionateBehavior")
                .addChild(new BehaviorTree.LambdaCondition("IsAffectionate", bb ->
                    PetPersonality.get().affection > 50))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.LambdaAction("ShowLove", bb -> {
                        currentBehavior = "ShowingLove";
                        String[] loveEmojis = {"<3", "love u!", "*nuzzle*", "~happy~"};
                        showEmoji(loveEmojis[(int)(Math.random() * loveEmojis.length)]);
                        return BehaviorTree.Status.SUCCESS;
                    }), 15f)))
            // Brave goose - explore
            .addChild(new BehaviorTree.Sequence("BraveBehavior")
                .addChild(new BehaviorTree.LambdaCondition("IsBrave", bb ->
                    PetPersonality.get().bravery > 50 && PetNeeds.get().energy > 50))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.LambdaAction("Explore", bb -> {
                        currentBehavior = "Exploring";
                        showEmoji("!");
                        exploreRandomCorner();
                        return BehaviorTree.Status.SUCCESS;
                    }), 20f)));
    }

    /**
     * Idle behaviors when nothing else to do.
     */
    private BehaviorTree.Node buildIdleBranch() {
        return new BehaviorTree.Selector("IdleBehaviors")
            // Time-based behaviors
            .addChild(buildTimeBasedBehaviors())
            // Random idle actions
            .addChild(new BehaviorTree.Cooldown(
                new BehaviorTree.RandomSelector("RandomIdle")
                    .addChild(new BehaviorTree.LambdaAction("LookAround", bb -> {
                        currentBehavior = "LookingAround";
                        showEmoji("?");
                        triggerEvent(GooseAI.RandomEvent.LOOK_AROUND);
                        return BehaviorTree.Status.SUCCESS;
                    }))
                    .addChild(new BehaviorTree.LambdaAction("Preen", bb -> {
                        currentBehavior = "Preening";
                        showEmoji("*preen*");
                        return BehaviorTree.Status.SUCCESS;
                    }))
                    .addChild(new BehaviorTree.LambdaAction("Peck", bb -> {
                        currentBehavior = "Pecking";
                        showEmoji("*peck*");
                        return BehaviorTree.Status.SUCCESS;
                    }))
                    .addChild(new BehaviorTree.LambdaAction("Stretch", bb -> {
                        currentBehavior = "Stretching";
                        showEmoji("stretch~");
                        triggerEvent(GooseAI.RandomEvent.STRETCH);
                        return BehaviorTree.Status.SUCCESS;
                    }))
                    .addChild(new BehaviorTree.LambdaAction("RandomThought", bb -> {
                        currentBehavior = "Thinking";
                        String[] thoughts = {"...", "hmm", "~", ":)", "*waddle*"};
                        showEmoji(thoughts[(int)(Math.random() * thoughts.length)]);
                        return BehaviorTree.Status.SUCCESS;
                    }))
                    .addChild(new BehaviorTree.LambdaAction("Honk", bb -> {
                        currentBehavior = "Honking";
                        showEmoji("HONK!");
                        Sound.honk();
                        return BehaviorTree.Status.SUCCESS;
                    })), 5f))
            // Default wander
            .addChild(new BehaviorTree.LambdaAction("Wander", bb -> {
                currentBehavior = "Wandering";
                return BehaviorTree.Status.SUCCESS;
            }));
    }

    /**
     * Time-based behaviors (morning, night, etc).
     */
    private BehaviorTree.Node buildTimeBasedBehaviors() {
        return new BehaviorTree.Selector("TimeBased")
            // Morning energy
            .addChild(new BehaviorTree.Sequence("Morning")
                .addChild(new BehaviorTree.LambdaCondition("IsMorning", bb -> {
                    int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
                    return hour >= 6 && hour < 9;
                }))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.LambdaAction("MorningGreeting", bb -> {
                        currentBehavior = "MorningRoutine";
                        String[] morning = {"morning!", "*yawn*", "coffee?", "good day!"};
                        showEmoji(morning[(int)(Math.random() * morning.length)]);
                        return BehaviorTree.Status.SUCCESS;
                    }), 30f)))
            // Lunch time
            .addChild(new BehaviorTree.Sequence("LunchTime")
                .addChild(new BehaviorTree.LambdaCondition("IsLunchTime", bb -> {
                    int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
                    return hour >= 12 && hour < 14;
                }))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.LambdaAction("LunchCravings", bb -> {
                        currentBehavior = "WantingLunch";
                        String[] lunch = {"lunch?", "snack time!", "food time?"};
                        showEmoji(lunch[(int)(Math.random() * lunch.length)]);
                        return BehaviorTree.Status.SUCCESS;
                    }), 30f)))
            // Evening sleepiness
            .addChild(new BehaviorTree.Sequence("Evening")
                .addChild(new BehaviorTree.LambdaCondition("IsEvening", bb -> {
                    int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
                    return hour >= 21 || hour < 6;
                }))
                .addChild(new BehaviorTree.Cooldown(
                    new BehaviorTree.LambdaAction("NightSleepiness", bb -> {
                        currentBehavior = "GettingSleepy";
                        String[] night = {"sleepy...", "*yawn*", "zzz?", "bedtime?"};
                        showEmoji(night[(int)(Math.random() * night.length)]);
                        if (Math.random() < 0.1f && PetNeeds.get().energy < 50) {
                            setTask(GooseTasks.GooseTask.Sleeping);
                        }
                        return BehaviorTree.Status.SUCCESS;
                    }), 20f)));
    }

    // ============== UPDATE ==============

    /**
     * Update the behavior tree each frame.
     */
    public void update(float deltaTime) {
        if (callback == null) return;

        // Update blackboard with current state
        updateBlackboard(deltaTime);

        // Tick the tree
        tree.tick();
    }

    private void updateBlackboard(float deltaTime) {
        GooseTouchHandler touch = callback.getTouchHandler();
        GoosePhysics physics = callback.getPhysics();

        // Time
        blackboard.set("time", Time.time);
        blackboard.set("deltaTime", deltaTime);

        // Interaction state
        boolean isBeingPetted = touch != null && touch.isBeingPetted();
        if (isBeingPetted) {
            petDuration += deltaTime;
        } else {
            petDuration = 0f;
            lastPetEmojiStage = NO_PET_STAGE;
        }
        blackboard.set("petDuration", petDuration);
        blackboard.set("isBeingPetted", isBeingPetted);
        blackboard.set("isBeingDragged", touch != null && touch.isBeingDragged());

        // Get time since last interaction from memory (if available)
        // This would need to be passed from GooseAI

        // Needs
        blackboard.set("hunger", PetNeeds.get().hunger);
        blackboard.set("energy", PetNeeds.get().energy);
        blackboard.set("happiness", PetNeeds.get().happiness);

        // Personality
        blackboard.set("playfulness", PetPersonality.get().playfulness);
        blackboard.set("affection", PetPersonality.get().affection);
        blackboard.set("bravery", PetPersonality.get().bravery);
        blackboard.set("mischief", PetPersonality.get().mischief);

        // Position
        if (physics != null) {
            blackboard.set("posX", physics.getPosition().x);
            blackboard.set("posY", physics.getPosition().y);
            blackboard.set("isMoving", physics.isMoving());
        }

        // Screen bounds
        blackboard.set("screenWidth", callback.getScreenWidth());
        blackboard.set("screenHeight", callback.getScreenHeight());
    }

    public void setTimeSinceInteraction(float time) {
        blackboard.set("timeSinceInteraction", time);
    }

    // ============== HELPER METHODS ==============

    private void showEmoji(String emoji) {
        if (callback != null && callback.getTouchHandler() != null) {
            callback.getTouchHandler().showEmoji(emoji);
        }
    }

    private void setTask(GooseTasks.GooseTask task) {
        // This needs to communicate back to GooseAI
        blackboard.set("requestedTask", task);
    }

    private void triggerEvent(GooseAI.RandomEvent event) {
        blackboard.set("requestedEvent", event);
    }

    private void seekAttention() {
        if (callback == null) return;
        // Move towards center of screen
        int centerX = callback.getScreenWidth() / 2;
        int centerY = callback.getScreenHeight() / 2;
        if (callback.getPhysics() != null) {
            callback.getPhysics().setTargetPos(new Vec2(centerX, centerY));
        }
    }

    private void exploreRandomCorner() {
        if (callback == null) return;
        int sw = callback.getScreenWidth();
        int sh = callback.getScreenHeight();

        // Pick a random corner
        float[][] corners = {
            {100, 100}, {sw - 100, 100},
            {100, sh - 100}, {sw - 100, sh - 100}
        };
        int idx = (int)(Math.random() * corners.length);

        if (callback.getPhysics() != null) {
            callback.getPhysics().setTargetPos(new Vec2(corners[idx][0], corners[idx][1]));
        }
    }

    // ============== GETTERS ==============

    public String getCurrentBehavior() {
        return currentBehavior;
    }

    public GooseTasks.GooseTask getRequestedTask() {
        Object task = blackboard.get("requestedTask");
        blackboard.remove("requestedTask");
        return task instanceof GooseTasks.GooseTask ? (GooseTasks.GooseTask) task : null;
    }

    public GooseAI.RandomEvent getRequestedEvent() {
        Object event = blackboard.get("requestedEvent");
        blackboard.remove("requestedEvent");
        return event instanceof GooseAI.RandomEvent ? (GooseAI.RandomEvent) event : null;
    }

    public BehaviorTree.Blackboard getBlackboard() {
        return blackboard;
    }
}
