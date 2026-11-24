package com.skyvanguard.goozy.engine.body;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.skyvanguard.goozy.engine.body.BodyState.Pose;
import com.skyvanguard.goozy.engine.body.BodyState.Zone;

import org.junit.Test;

public class GooseModelTest {
    private static final float DT = 1f / 60f;

    private static GooseModel standingAt(float x, float y, float heading) {
        GooseModel model = new GooseModel();
        for (int i = 0; i < 120; i++) model.update(DT, x, y, heading, 0f, 0f, 0f, false);
        return model;
    }

    @Test
    public void everyHeadingProducesFiniteGeometryInsideBounds() {
        for (int heading = 0; heading < 360; heading += 15) {
            GooseModel m = standingAt(500f, 500f, heading);
            assertTrue(m.ballCount > 0);
            for (int i = 0; i < m.ballCount; i++) {
                assertFalse(Float.isNaN(m.ballX[i]) || Float.isNaN(m.ballY[i]));
                assertTrue(m.ballX[i] - m.ballR[i] >= m.minX - 0.01f);
                assertTrue(m.ballY[i] + m.ballR[i] <= m.maxY + 0.01f);
            }
            assertTrue("la cabeza va arriba del suelo", m.headY < 500f);
        }
    }

    @Test
    public void headIsAlwaysAboveTheBody() {
        GooseModel m = standingAt(300f, 300f, 0f);
        float bodyTop = Float.MAX_VALUE;
        for (int i = 0; i < m.ballCount; i++) {
            if (m.ballGroup[i] == GooseModel.GROUP_BODY) bodyTop = Math.min(bodyTop, m.ballY[i]);
        }
        assertTrue(m.headY < bodyTop);
    }

    @Test
    public void beakPointsWhereTheGooseFaces() {
        GooseModel right = standingAt(300f, 300f, 0f);
        assertTrue(right.beakTipX > right.beakBaseX);
        GooseModel left = standingAt(300f, 300f, 180f);
        assertTrue(left.beakTipX < left.beakBaseX);
    }

    @Test
    public void walkingAwayHidesTheEyes() {
        GooseModel away = new GooseModel();
        float y = 800f;
        for (int i = 0; i < 120; i++) {
            y -= 100f * DT;
            away.update(DT, 300f, y, 270f, 0f, 0f, 0f, false);
        }
        assertEquals(0f, Math.max(away.eyeSize[0], away.eyeSize[1]), 0.001f);
        GooseModel toward = standingAt(300f, 300f, 90f);
        assertTrue(toward.eyeSize[0] > 0f && toward.eyeSize[1] > 0f);
    }

    @Test
    public void sideViewShowsOnlyTheNearEye() {
        GooseModel m = standingAt(300f, 300f, 0f);
        boolean left = m.eyeSize[0] > 0f;
        boolean right = m.eyeSize[1] > 0f;
        assertTrue(left ^ right);
    }

    @Test
    public void walkingLeavesStepsAndStandingDoesNot() {
        GooseModel m = new GooseModel();
        float[] steps = new float[4];
        int total = 0;
        float x = 100f;
        for (int i = 0; i < 180; i++) {
            x += 80f * DT;
            m.update(DT, x, 400f, 0f, 0f, 0f, 0f, false);
            total += m.pollSteps(steps);
        }
        assertTrue("caminar 3 s deja pisadas: " + total, total >= 4);

        GooseModel still = standingAt(100f, 400f, 0f);
        assertEquals(0, still.pollSteps(steps));
    }

    @Test
    public void zonesMatchTheDrawnParts() {
        GooseModel m = standingAt(400f, 400f, 0f);
        assertEquals(Zone.HEAD, m.zoneAt(m.headX, m.headY));
        assertEquals(Zone.BEAK, m.zoneAt(m.beakTipX, m.beakTipY));
        assertEquals(Zone.NONE, m.zoneAt(m.maxX + 200f, m.maxY + 200f));
        Zone body = m.zoneAt(m.ballX[1], m.ballY[1]);
        assertTrue(body == Zone.BODY || body == Zone.BELLY
                || body == Zone.WING_LEFT || body == Zone.WING_RIGHT);
    }

    @Test
    public void sleepingTucksTheHeadOntoTheBack() {
        GooseModel awake = standingAt(300f, 300f, 0f);
        GooseModel asleep = new GooseModel();
        asleep.pose = Pose.SLEEPING;
        for (int i = 0; i < 240; i++) asleep.update(DT, 300f, 300f, 0f, 0f, 0f, 0f, false);
        assertTrue(asleep.headY > awake.headY);
        assertTrue("mira hacia atrás", asleep.beakTipX < asleep.beakBaseX);
        assertEquals(1f, asleep.blink, 0.001f);
    }

    @Test
    public void eggHasNoBodyBallsAndIsTouchable() {
        GooseModel egg = new GooseModel();
        egg.species = Species.EGG;
        egg.update(DT, 200f, 200f, 0f, 0f, 0f, 0f, false);
        assertEquals(0, egg.ballCount);
        assertEquals(Zone.BODY, egg.zoneAt(egg.eggX, egg.eggY));
    }

    @Test
    public void beingCarriedNeitherWalksNorLeavesSteps() {
        GooseModel m = new GooseModel();
        float[] steps = new float[4];
        int total = 0;
        float x = 100f;
        for (int i = 0; i < 180; i++) {
            x += 80f * DT;
            m.update(DT, x, 400f, 0f, 0f, 0f, 0f, true);
            total += m.pollSteps(steps);
        }
        assertEquals(0, total);
    }

    @Test
    public void eyesOpenRightAfterWakingUp() {
        GooseModel m = new GooseModel();
        m.pose = Pose.SLEEPING;
        for (int i = 0; i < 240; i++) m.update(DT, 300f, 300f, 0f, 0f, 0f, 0f, false);
        assertEquals(1f, m.blink, 0.001f);
        m.pose = Pose.NORMAL;
        m.update(DT, 300f, 300f, 0f, 0f, 0f, 0f, false);
        assertTrue("no debería seguir con los ojos cerrados", m.blink < 1f);
    }

    @Test
    public void sideViewShowsOnlyTheNearWing() {
        GooseModel m = standingAt(300f, 300f, 0f);
        boolean left = m.wingVisibility[0] > 0.5f;
        boolean right = m.wingVisibility[1] > 0.5f;
        assertTrue(left ^ right);
    }

    @Test
    public void approachIsFrameRateIndependent() {
        float fast = 0f, slow = 0f;
        for (int i = 0; i < 120; i++) fast = GooseModel.approach(fast, 1f, 1f / 120f, 0.2f);
        for (int i = 0; i < 30; i++) slow = GooseModel.approach(slow, 1f, 1f / 30f, 0.2f);
        assertEquals(fast, slow, 0.001f);
    }
}
