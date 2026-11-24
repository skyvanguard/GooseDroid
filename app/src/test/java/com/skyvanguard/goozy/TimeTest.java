package com.skyvanguard.goozy;

import com.skyvanguard.goozy.core.Time;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TimeTest {

    private static final long START_NANOS = 5_000_000_000L;
    private static final long FRAME_NANOS = 16_000_000L;
    private static final float FRAME_SECONDS = 0.016f;
    private static final float DELTA_TOLERANCE = 1e-6f;
    private static final double TIME_TOLERANCE = 1e-6;

    @Before
    public void setUp() {
        Time.reset();
    }

    @After
    public void tearDown() {
        Time.reset();
    }

    // ============== RESET ==============

    @Test
    public void reset_restoresDefaults() {
        Time.tick(START_NANOS);
        Time.tick(START_NANOS + FRAME_NANOS);

        Time.reset();

        assertEquals(0.0, Time.time, 0.0);
        assertEquals(Time.DEFAULT_DELTA_SECONDS, Time.deltaTime, 0f);
    }

    @Test
    public void reset_nextTickIsTreatedAsFirst() {
        Time.tick(START_NANOS);
        Time.reset();

        // Sin reset, este salto de 10 s quedaria topado en MAX_DELTA_SECONDS
        Time.tick(START_NANOS + 10_000_000_000L);

        assertEquals(Time.DEFAULT_DELTA_SECONDS, Time.deltaTime, 0f);
        assertEquals(Time.DEFAULT_DELTA_SECONDS, Time.time, TIME_TOLERANCE);
    }

    // ============== TICK ==============

    @Test
    public void tick_firstTickUsesDefaultDelta() {
        Time.tick(START_NANOS);

        assertEquals(Time.DEFAULT_DELTA_SECONDS, Time.deltaTime, 0f);
        assertEquals(Time.DEFAULT_DELTA_SECONDS, Time.time, TIME_TOLERANCE);
    }

    @Test
    public void tick_normalDeltaIsMeasuredAndAccumulated() {
        Time.tick(START_NANOS);
        double timeAfterFirst = Time.time;

        Time.tick(START_NANOS + FRAME_NANOS);

        assertEquals(FRAME_SECONDS, Time.deltaTime, DELTA_TOLERANCE);
        assertEquals(timeAfterFirst + FRAME_SECONDS, Time.time, TIME_TOLERANCE);

        Time.tick(START_NANOS + 2 * FRAME_NANOS);

        assertEquals(FRAME_SECONDS, Time.deltaTime, DELTA_TOLERANCE);
        assertEquals(timeAfterFirst + 2 * FRAME_SECONDS, Time.time, TIME_TOLERANCE);
    }

    @Test
    public void tick_largeJumpIsCappedAtMaxDelta() {
        Time.tick(START_NANOS);
        double timeAfterFirst = Time.time;

        Time.tick(START_NANOS + 10_000_000_000L); // 10 s

        assertEquals(Time.MAX_DELTA_SECONDS, Time.deltaTime, 0f);
        assertEquals(timeAfterFirst + Time.MAX_DELTA_SECONDS, Time.time, TIME_TOLERANCE);
    }

    @Test
    public void tick_clockGoingBackwardsGivesZeroDelta() {
        Time.tick(START_NANOS);
        double timeAfterFirst = Time.time;

        Time.tick(START_NANOS - FRAME_NANOS);

        assertEquals(0f, Time.deltaTime, 0f);
        assertEquals(timeAfterFirst, Time.time, 0.0);
    }

    @Test
    public void tick_keepsFramePrecisionAfterManyHours() {
        Time.tick(START_NANOS);
        Time.time = 100000.0;

        Time.tick(START_NANOS + FRAME_NANOS);

        assertEquals(FRAME_SECONDS, Time.time - 100000.0, TIME_TOLERANCE);
    }

    @Test
    public void timeF_returnsTimeAsFloat() {
        Time.time = 12.5;

        assertEquals(12.5f, Time.timeF(), 0f);
    }
}
