package com.skyvanguard.goozy;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PetPersonalityTest {

    private PetPersonality personality;

    @Before
    public void setUp() {
        PetState state = new PetState();
        PetState.setInstance(state);
        personality = state.personality;
    }

    // ============== INITIAL STATE ==============

    @Test
    public void initialState_hasDefaultTraits() {
        assertEquals(0f, personality.playfulness, 0.01f);
        assertEquals(0f, personality.affection, 0.01f);
        assertEquals(0f, personality.bravery, 0.01f);
        assertEquals(50f, personality.mischief, 0.01f);
    }

    @Test
    public void initialState_levelIsOne() {
        assertEquals(1, personality.getLevel());
    }

    // ============== TRAIT EVOLUTION ==============

    @Test
    public void onPetted_increasesAffection() {
        float before = personality.affection;
        personality.onPetted();
        assertTrue(personality.affection > before);
    }

    @Test
    public void onPetted_decreasesMischief() {
        float before = personality.mischief;
        personality.onPetted();
        assertTrue(personality.mischief < before);
    }

    @Test
    public void onPetted_incrementsTotalPets() {
        assertEquals(0, personality.getTotalPets());
        personality.onPetted();
        assertEquals(1, personality.getTotalPets());
    }

    @Test
    public void onPlayed_increasesPlayfulness() {
        float before = personality.playfulness;
        personality.onPlayed();
        assertTrue(personality.playfulness > before);
    }

    @Test
    public void onPlayed_increasesBravery() {
        float before = personality.bravery;
        personality.onPlayed();
        assertTrue(personality.bravery > before);
    }

    @Test
    public void onPlayed_incrementsTotalPlays() {
        assertEquals(0, personality.getTotalPlays());
        personality.onPlayed();
        assertEquals(1, personality.getTotalPlays());
    }

    @Test
    public void onFed_increasesAffection() {
        float before = personality.affection;
        personality.onFed();
        assertTrue(personality.affection > before);
    }

    @Test
    public void onFed_incrementsTotalFeeds() {
        assertEquals(0, personality.getTotalFeeds());
        personality.onFed();
        assertEquals(1, personality.getTotalFeeds());
    }

    @Test
    public void onScolded_decreasesMischief() {
        float before = personality.mischief;
        personality.onScolded();
        assertTrue(personality.mischief < before);
    }

    @Test
    public void onScolded_decreasesAffection() {
        float before = personality.affection;
        personality.onScolded();
        assertTrue(personality.affection < before);
    }

    // ============== TRAIT CLAMPING ==============

    @Test
    public void traits_clampAtPositive100() {
        for (int i = 0; i < 200; i++) {
            personality.onPetted();
        }
        assertTrue(personality.affection <= 100f);
        assertTrue(personality.playfulness <= 100f);
    }

    @Test
    public void traits_clampAtNegative100() {
        for (int i = 0; i < 200; i++) {
            personality.onScolded();
        }
        assertTrue(personality.affection >= -100f);
        assertTrue(personality.mischief >= -100f);
        assertTrue(personality.bravery >= -100f);
    }

    // ============== IGNORED ==============

    @Test
    public void onIgnored_reducesAffection() {
        personality.affection = 50f;
        personality.onIgnored(5f);
        assertTrue(personality.affection < 50f);
    }

    @Test
    public void onIgnored_reducesPlayfulness() {
        personality.playfulness = 50f;
        personality.onIgnored(5f);
        assertTrue(personality.playfulness < 50f);
    }

    // ============== PERSONALITY TYPE ==============

    @Test
    public void getPersonalityType_mischievousWhenHighMischief() {
        personality.mischief = 80f;
        personality.playfulness = 10f;
        personality.affection = 10f;
        personality.bravery = 10f;
        assertEquals("Mischievous", personality.getPersonalityType());
    }

    @Test
    public void getPersonalityType_playfulWhenHighPlayfulness() {
        personality.playfulness = 80f;
        personality.mischief = 10f;
        personality.affection = 10f;
        personality.bravery = 10f;
        assertEquals("Playful", personality.getPersonalityType());
    }

    @Test
    public void getPersonalityType_affectionateWhenHighAffection() {
        personality.affection = 80f;
        personality.playfulness = 10f;
        personality.mischief = 10f;
        personality.bravery = 10f;
        assertEquals("Affectionate", personality.getPersonalityType());
    }

    @Test
    public void getPersonalityType_braveWhenHighBravery() {
        personality.bravery = 80f;
        personality.playfulness = 10f;
        personality.affection = 10f;
        personality.mischief = 10f;
        assertEquals("Brave", personality.getPersonalityType());
    }

    @Test
    public void getPersonalityType_timidWhenNegativeBravery() {
        personality.bravery = -80f;
        personality.playfulness = 10f;
        personality.affection = 10f;
        personality.mischief = 10f;
        assertEquals("Timid", personality.getPersonalityType());
    }

    // ============== LEVEL CALCULATION ==============

    @Test
    public void getLevel_level1WithFewInteractions() {
        assertEquals(1, personality.getLevel());
    }

    @Test
    public void getLevel_level2With10PlusInteractions() {
        for (int i = 0; i < 10; i++) {
            personality.onPetted();
        }
        assertEquals(2, personality.getLevel());
    }

    @Test
    public void getLevel_level3With50PlusInteractions() {
        for (int i = 0; i < 50; i++) {
            personality.onFed();
        }
        assertEquals(3, personality.getLevel());
    }

    // ============== BEHAVIOR MULTIPLIERS ==============

    @Test
    public void getWanderDurationMultiplier_baseIs1() {
        personality.playfulness = 0f;
        assertEquals(1f, personality.getWanderDurationMultiplier(), 0.01f);
    }

    @Test
    public void getWanderDurationMultiplier_higherWithPlayfulness() {
        personality.playfulness = 100f;
        assertTrue(personality.getWanderDurationMultiplier() > 1f);
    }

    @Test
    public void getSpeedMultiplier_baseIs1() {
        personality.bravery = 0f;
        personality.playfulness = 0f;
        assertEquals(1f, personality.getSpeedMultiplier(), 0.01f);
    }

    @Test
    public void getReactionSpeed_higherWithAffection() {
        personality.affection = 100f;
        assertTrue(personality.getReactionSpeed() > 1f);
    }

    // ============== TITLE ==============

    @Test
    public void getTitle_includesLevelPrefixAndType() {
        String title = personality.getTitle();
        assertTrue(title.startsWith("Baby"));
        assertTrue(title.endsWith("Goose"));
    }

    // ============== RESET ==============

    @Test
    public void reset_restoresDefaults() {
        personality.playfulness = 80f;
        personality.affection = 80f;
        personality.bravery = 80f;
        personality.mischief = -50f;
        personality.onPetted(); // increment counters
        personality.reset();

        assertEquals(0f, personality.playfulness, 0.01f);
        assertEquals(0f, personality.affection, 0.01f);
        assertEquals(0f, personality.bravery, 0.01f);
        assertEquals(50f, personality.mischief, 0.01f);
        assertEquals(0, personality.getTotalPets());
    }

    // ============== LOAD STATE ==============

    @Test
    public void loadState_setsTraitsAndCounters() {
        personality.loadState(50f, 60f, 70f, 80f, 10, 20, 30, System.currentTimeMillis());

        assertEquals(50f, personality.playfulness, 0.01f);
        assertEquals(60f, personality.affection, 0.01f);
        assertEquals(70f, personality.bravery, 0.01f);
        assertEquals(80f, personality.mischief, 0.01f);
        assertEquals(10, personality.getTotalPets());
        assertEquals(20, personality.getTotalPlays());
        assertEquals(30, personality.getTotalFeeds());
    }

    @Test
    public void loadState_clampsTraits() {
        personality.loadState(200f, -200f, 150f, -150f, 0, 0, 0, System.currentTimeMillis());

        assertEquals(100f, personality.playfulness, 0.01f);
        assertEquals(-100f, personality.affection, 0.01f);
        assertEquals(100f, personality.bravery, 0.01f);
        assertEquals(-100f, personality.mischief, 0.01f);
    }

    // ============== STATIC GET ==============

    @Test
    public void get_returnsSameInstanceFromPetState() {
        assertSame(personality, PetPersonality.get());
    }
}
