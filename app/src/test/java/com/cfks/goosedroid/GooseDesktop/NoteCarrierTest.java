package com.cfks.goosedroid.GooseDesktop;

import com.cfks.goosedroid.core.Vec2;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class NoteCarrierTest {
    private static final float WORLD_WIDTH = 400f;
    private static final float WORLD_HEIGHT = 800f;
    private static final float HALF_WIDTH = 50f;
    private static final float HALF_HEIGHT = 40f;

    private NoteCarrier carrier;

    @Before
    public void setUp() {
        carrier = new NoteCarrier();
    }

    @Test
    public void fullCycle_waitCarryPlaceFade() {
        carrier.prepare("HONK");
        assertEquals(NoteCarrier.State.WAITING, carrier.getState());
        assertEquals(0f, carrier.getAlpha(), 0.001f);

        carrier.pickUp(-1f, 0f, new Vec2(100f, 300f));
        assertEquals(NoteCarrier.State.CARRIED, carrier.getState());
        assertEquals(100f - NoteCarrier.CARRY_DISTANCE, carrier.getCenterX(), 0.001f);
        assertEquals(1f, carrier.getAlpha(), 0.001f);

        carrier.update(0.1f, new Vec2(200f, 300f));
        assertEquals(200f - NoteCarrier.CARRY_DISTANCE, carrier.getCenterX(), 0.001f);

        carrier.place(WORLD_WIDTH, WORLD_HEIGHT, HALF_WIDTH, HALF_HEIGHT);
        assertEquals(NoteCarrier.State.PLACED, carrier.getState());

        carrier.update(carrier.getLifetime() - 1f, new Vec2(0f, 0f));
        float fading = carrier.getAlpha();
        assertTrue(fading > 0f && fading < 1f);

        carrier.update(2f, new Vec2(0f, 0f));
        assertEquals(NoteCarrier.State.NONE, carrier.getState());
        assertEquals(0f, carrier.getAlpha(), 0.001f);
    }

    @Test
    public void lifetime_growsWithTheTextUpToAMaximum() {
        assertEquals(NoteCarrier.MIN_LIFETIME_SECONDS + 4 * NoteCarrier.SECONDS_PER_CHAR,
                NoteCarrier.lifetimeFor("HONK"), 0.001f);
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 1000; i++) longText.append('x');
        assertEquals(NoteCarrier.MAX_LIFETIME_SECONDS,
                NoteCarrier.lifetimeFor(longText.toString()), 0.001f);
    }

    @Test
    public void place_keepsTheWholeNoteInsideTheWorld() {
        carrier.prepare("HONK");
        carrier.pickUp(-1f, 0f, new Vec2(30f, 5f));

        carrier.place(WORLD_WIDTH, WORLD_HEIGHT, HALF_WIDTH, HALF_HEIGHT);

        assertEquals(HALF_WIDTH, carrier.getCenterX(), 0.001f);
        assertEquals(HALF_HEIGHT, carrier.getCenterY(), 0.001f);
    }

    @Test
    public void offerText_onlyReplacesWhileTheNoteIsNotVisible() {
        carrier.prepare("respaldo");
        assertTrue(carrier.offerText("  escrita por el modelo  "));
        assertEquals("escrita por el modelo", carrier.getText());

        carrier.pickUp(1f, 0f, new Vec2(300f, 300f));
        assertFalse(carrier.offerText("llegó tarde"));
        assertEquals("escrita por el modelo", carrier.getText());
    }

    @Test
    public void offerText_ignoresBlankText() {
        carrier.prepare("respaldo");
        assertFalse(carrier.offerText("   "));
        assertEquals("respaldo", carrier.getText());
    }

    @Test
    public void interruptedBeforePickUp_leavesNothing() {
        carrier.prepare("HONK");

        carrier.place(WORLD_WIDTH, WORLD_HEIGHT, HALF_WIDTH, HALF_HEIGHT);

        assertEquals(NoteCarrier.State.NONE, carrier.getState());
    }

    @Test
    public void pickUpWithoutText_isIgnored() {
        carrier.prepare("");
        carrier.pickUp(1f, 0f, new Vec2(0f, 0f));

        assertEquals(NoteCarrier.State.NONE, carrier.getState());
    }

    @Test
    public void pickUpWithoutPrepare_isIgnored() {
        carrier.pickUp(1f, 0f, new Vec2(0f, 0f));

        assertEquals(NoteCarrier.State.NONE, carrier.getState());
    }
}
