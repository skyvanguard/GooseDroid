package com.skyvanguard.goozy.engine;

import org.junit.Test;

import static org.junit.Assert.*;

public class NightRoutineTest {

    @Test
    public void isNight_coversLateEveningAndEarlyMorning() {
        assertTrue(NightRoutine.isNight(23));
        assertTrue(NightRoutine.isNight(0));
        assertTrue(NightRoutine.isNight(6));
        assertFalse(NightRoutine.isNight(7));
        assertFalse(NightRoutine.isNight(15));
        assertFalse(NightRoutine.isNight(22));
    }

    @Test
    public void shouldSleep_onlyAtNightWhenIdleAndNotFullOfEnergy() {
        assertTrue(NightRoutine.shouldSleep(1, 50f, true, false));
        assertFalse("de día no", NightRoutine.shouldSleep(14, 50f, true, false));
        assertFalse("con energía de sobra no", NightRoutine.shouldSleep(1, 95f, true, false));
        assertFalse("si está haciendo otra cosa no", NightRoutine.shouldSleep(1, 50f, false, false));
        assertFalse("si el humano lo toca no", NightRoutine.shouldSleep(1, 50f, true, true));
    }
}
