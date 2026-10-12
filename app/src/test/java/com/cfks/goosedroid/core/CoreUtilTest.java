package com.cfks.goosedroid.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class CoreUtilTest {
    private static final float EPSILON = 1e-4f;

    @Test
    public void shuffleBag_dealsEveryIndexOncePerRound() {
        ShuffleBag bag = new ShuffleBag(7, new Random(42));
        for (int round = 0; round < 3; round++) {
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 7; i++) seen.add(bag.next());
            assertEquals(7, seen.size());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shuffleBag_rejectsEmptySize() {
        new ShuffleBag(0);
    }

    @Test
    public void lerpAngle_takesShortestPath() {
        assertEquals(370f, MathUtil.lerpAngle(350f, 10f, 1f), EPSILON);
        assertEquals(355f, MathUtil.lerpAngle(350f, 0f, 0.5f), EPSILON);
        assertEquals(-10f, MathUtil.lerpAngle(10f, -30f, 0.5f), EPSILON);
    }

    @Test
    public void easeInOut_isClampedAndSymmetric() {
        assertEquals(0f, MathUtil.easeInOut(-1f), EPSILON);
        assertEquals(1f, MathUtil.easeInOut(2f), EPSILON);
        assertEquals(0.5f, MathUtil.easeInOut(0.5f), EPSILON);
        assertEquals(1f - MathUtil.easeInOut(0.2f), MathUtil.easeInOut(0.8f), EPSILON);
    }

    @Test
    public void footprint_fadesAfterVisibleTime() {
        Footprint print = new Footprint();
        assertEquals(0f, print.visibility(5.0), EPSILON);

        print.stampedAt = 10.0;
        assertEquals(1f, print.visibility(10.0 + Footprint.VISIBLE_SECONDS), EPSILON);
        float midFade = print.visibility(10.0 + Footprint.VISIBLE_SECONDS + Footprint.FADE_SECONDS / 2);
        assertTrue(midFade > 0.4f && midFade < 0.6f);
        assertEquals(0f, print.visibility(10.0 + Footprint.VISIBLE_SECONDS + Footprint.FADE_SECONDS), EPSILON);
    }

    @Test
    public void vec2_normalizeOfZeroIsZero() {
        Vec2 result = Vec2.normalize(new Vec2(0f, 0f));
        assertEquals(0f, result.x, EPSILON);
        assertEquals(0f, result.y, EPSILON);
    }
}
