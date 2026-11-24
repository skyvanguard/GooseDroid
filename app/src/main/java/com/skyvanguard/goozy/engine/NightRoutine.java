package com.skyvanguard.goozy.engine;

import com.skyvanguard.goozy.PetNeeds;

import java.util.Calendar;

/**
 * De noche el ganso se va a dormir solo: si está paseando, nadie lo toca y
 * no está lleno de energía. Así la energía se recupera por la noche como en
 * un tamagotchi, sin que haya que mandarlo a dormir.
 */
final class NightRoutine {
    static final int NIGHT_START_HOUR = 23;
    static final int NIGHT_END_HOUR = 7;
    /** Por encima de esto no tiene sueño, aunque sea de noche. */
    static final float MAX_ENERGY_TO_SLEEP = 80f;
    private static final float CHECK_INTERVAL_SECONDS = 20f;

    private static float timer = 0f;

    private NightRoutine() {
    }

    static void reset() {
        timer = 0f;
    }

    static void update(float deltaTime) {
        timer += deltaTime;
        if (timer < CHECK_INTERVAL_SECONDS) return;
        timer = 0f;

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean isWandering = GooseTasks.GooseTask.Wander.name()
                .equals(TheGoose.getCurrentTaskName());
        if (shouldSleep(hour, PetNeeds.get().energy, isWandering, TheGoose.isBusyWithHuman())) {
            TheGoose.requestTask(GooseTasks.GooseTask.Sleeping);
        }
    }

    static boolean isNight(int hour) {
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }

    static boolean shouldSleep(int hour, float energy, boolean isWandering, boolean isBusy) {
        return isNight(hour) && isWandering && !isBusy && energy < MAX_ENERGY_TO_SLEEP;
    }
}
