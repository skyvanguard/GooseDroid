package com.skyvanguard.goozy;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class PetNeedsTest {

    private static final float ONE_HOUR = 3600f;

    private PetNeeds needs;

    @Before
    public void setUp() {
        PetState state = new PetState();
        PetState.setInstance(state);
        needs = state.needs;
    }

    // ============== INITIAL STATE ==============

    @Test
    public void initialState_hasDefaultValues() {
        assertEquals(50f, needs.hunger, 0.01f);
        assertEquals(100f, needs.energy, 0.01f);
        assertEquals(75f, needs.happiness, 0.01f);
    }

    @Test
    public void initialMood_isNeutral() {
        // happiness=75 is < 80 threshold for HAPPY, so NEUTRAL
        assertEquals(PetNeeds.MoodState.NEUTRAL, needs.getMoodState());
    }

    // ============== DECAY RATES ==============

    @Test
    public void update_hungerIncreases() {
        float before = needs.hunger;
        needs.update(10f);
        assertTrue(needs.hunger > before);
    }

    @Test
    public void update_energyDecreases() {
        float before = needs.energy;
        needs.update(10f);
        assertTrue(needs.energy < before);
    }

    @Test
    public void update_happinessDecreases() {
        float before = needs.happiness;
        needs.update(10f);
        assertTrue(needs.happiness < before);
    }

    @Test
    public void update_hungerCapsAt100() {
        needs.hunger = 99f;
        needs.update(1000f);
        assertEquals(100f, needs.hunger, 0.01f);
    }

    @Test
    public void update_energyFloorsAt0() {
        needs.energy = 1f;
        needs.update(1000f);
        assertEquals(0f, needs.energy, 0.01f);
    }

    @Test
    public void update_happinessFloorsAt0() {
        needs.happiness = 1f;
        needs.update(1000f);
        assertEquals(0f, needs.happiness, 0.01f);
    }

    // ============== CROSS EFFECTS ==============

    @Test
    public void update_highHungerReducesHappinessFaster() {
        needs.hunger = 90f;
        needs.happiness = 50f;
        float happinessBefore = needs.happiness;

        needs.update(ONE_HOUR);

        // Happiness decays by base rate + cross-effect
        float expectedDecay = (PetNeeds.HAPPINESS_RATE + PetNeeds.NEGLECT_HAPPINESS_RATE) * ONE_HOUR;
        assertEquals(expectedDecay, happinessBefore - needs.happiness, 0.05f);
    }

    @Test
    public void update_lowEnergyReducesHappinessFaster() {
        needs.energy = 10f;
        needs.happiness = 50f;
        float happinessBefore = needs.happiness;

        needs.update(ONE_HOUR);

        float expectedDecay = (PetNeeds.HAPPINESS_RATE + PetNeeds.NEGLECT_HAPPINESS_RATE) * ONE_HOUR;
        assertEquals(expectedDecay, happinessBefore - needs.happiness, 0.05f);
    }

    // ============== HIGIENE Y SALUD ==============

    @Test
    public void hygiene_takesADayToRunOut() {
        needs.update(12 * ONE_HOUR);
        assertEquals(50f, needs.hygiene, 0.5f);
    }

    @Test
    public void soilAndClean() {
        needs.soil(PetNeeds.MUD_SOIL_AMOUNT);
        assertEquals(100f - PetNeeds.MUD_SOIL_AMOUNT, needs.hygiene, 0.01f);

        needs.happiness = 50f;
        needs.clean();
        assertEquals(100f, needs.hygiene, 0.01f);
        assertTrue(needs.happiness > 50f);
    }

    @Test
    public void health_dropsWhenNeglectedAndRecoversWhenCaredFor() {
        needs.hunger = 95f;
        needs.update(ONE_HOUR);
        float afterNeglect = needs.health;
        assertTrue(afterNeglect < 100f);

        needs.hunger = 20f;
        needs.energy = 90f;
        needs.happiness = 80f;
        needs.update(ONE_HOUR);
        assertTrue(needs.health > afterNeglect);
    }

    @Test
    public void sickness_isTheMostImportantMood() {
        needs.health = PetNeeds.SICK_THRESHOLD - 1f;
        needs.hunger = 95f;

        assertTrue(needs.isSick());
        assertEquals(PetNeeds.MoodState.SICK, needs.getMoodState());
        assertTrue(needs.needsUrgentAttention());

        needs.heal();
        assertFalse(needs.isSick());
    }

    @Test
    public void dirtyMood_whenHygieneIsLow() {
        needs.hygiene = 20f;
        assertEquals(PetNeeds.MoodState.DIRTY, needs.getMoodState());
    }

    @Test
    public void offline_hygieneHasAFloorAndHealthDoesNotChange() {
        needs.health = 70f;
        needs.applyOfflineTime(48 * ONE_HOUR);

        assertEquals(PetNeeds.OFFLINE_MIN_HYGIENE, needs.hygiene, 0.01f);
        assertEquals(70f, needs.health, 0.01f);
    }

    @Test
    public void loadCare_clampsValues() {
        needs.loadCare(150f, -20f);
        assertEquals(100f, needs.hygiene, 0.01f);
        assertEquals(0f, needs.health, 0.01f);
    }

    // ============== ESCALA DE TAMAGOTCHI ==============

    @Test
    public void update_tenMinutesBarelyChangesNeeds() {
        needs.update(10 * 60f);
        assertEquals(50f, needs.hunger, 3f);
        assertEquals(100f, needs.energy, 3f);
        assertEquals(75f, needs.happiness, 3f);
        assertFalse(needs.needsUrgentAttention());
    }

    @Test
    public void update_hungerTakesEightHoursFromEmptyToFull() {
        needs.hunger = 0f;
        needs.update(8 * ONE_HOUR);
        assertEquals(100f, needs.hunger, 0.5f);
    }

    @Test
    public void offline_wholeNightLeavesPetAliveAndRested() {
        needs.energy = 30f;
        needs.applyOfflineTime(10 * ONE_HOUR);
        assertEquals(PetNeeds.OFFLINE_MAX_HUNGER, needs.hunger, 0.01f);
        assertEquals(PetNeeds.OFFLINE_MIN_HAPPINESS, needs.happiness, 0.01f);
        assertEquals(100f, needs.energy, 0.01f);
    }

    @Test
    public void offline_doesNotImproveValuesAlreadyBeyondTheFloor() {
        needs.hunger = 95f;
        needs.happiness = 10f;
        needs.applyOfflineTime(ONE_HOUR);
        assertEquals(95f, needs.hunger, 0.01f);
        assertEquals(10f, needs.happiness, 0.01f);
    }

    @Test
    public void offline_negativeElapsedIsIgnored() {
        needs.applyOfflineTime(-500f);
        assertEquals(50f, needs.hunger, 0.01f);
        assertEquals(100f, needs.energy, 0.01f);
        assertEquals(75f, needs.happiness, 0.01f);
    }

    // ============== ACTIONS ==============

    @Test
    public void feed_reducesHunger() {
        needs.hunger = 80f;
        needs.feed();
        assertEquals(40f, needs.hunger, 0.01f);
    }

    @Test
    public void feed_hungerFloorsAt0() {
        needs.hunger = 20f;
        needs.feed();
        assertEquals(0f, needs.hunger, 0.01f);
    }

    @Test
    public void feed_increasesHappiness() {
        needs.happiness = 50f;
        needs.feed();
        assertEquals(60f, needs.happiness, 0.01f);
    }

    @Test
    public void pet_increasesHappiness() {
        needs.happiness = 50f;
        needs.pet();
        assertEquals(65f, needs.happiness, 0.01f);
    }

    @Test
    public void pet_happinessCapsAt100() {
        needs.happiness = 95f;
        needs.pet();
        assertEquals(100f, needs.happiness, 0.01f);
    }

    @Test
    public void sleep_increasesEnergy() {
        needs.energy = 50f;
        needs.sleep();
        assertEquals(80f, needs.energy, 0.01f);
    }

    @Test
    public void sleep_energyCapsAt100() {
        needs.energy = 90f;
        needs.sleep();
        assertEquals(100f, needs.energy, 0.01f);
    }

    @Test
    public void play_increasesHappiness() {
        needs.happiness = 50f;
        float before = needs.happiness;
        needs.play();
        assertTrue(needs.happiness > before);
    }

    @Test
    public void play_decreasesEnergy() {
        needs.energy = 50f;
        float before = needs.energy;
        needs.play();
        assertTrue(needs.energy < before);
    }

    @Test
    public void play_increasesHunger() {
        needs.hunger = 50f;
        float before = needs.hunger;
        needs.play();
        assertTrue(needs.hunger > before);
    }

    // ============== MOOD STATES ==============

    @Test
    public void getMoodState_hungryWhenHungerHigh() {
        needs.hunger = 85f;
        needs.energy = 50f;
        needs.happiness = 50f;
        assertEquals(PetNeeds.MoodState.HUNGRY, needs.getMoodState());
    }

    @Test
    public void getMoodState_tiredWhenEnergyLow() {
        needs.hunger = 50f;
        needs.energy = 15f;
        needs.happiness = 50f;
        assertEquals(PetNeeds.MoodState.TIRED, needs.getMoodState());
    }

    @Test
    public void getMoodState_sadWhenHappinessLow() {
        needs.hunger = 50f;
        needs.energy = 50f;
        needs.happiness = 20f;
        assertEquals(PetNeeds.MoodState.SAD, needs.getMoodState());
    }

    @Test
    public void getMoodState_happyWhenHappinessHigh() {
        needs.hunger = 50f;
        needs.energy = 50f;
        needs.happiness = 90f;
        assertEquals(PetNeeds.MoodState.HAPPY, needs.getMoodState());
    }

    @Test
    public void getMoodState_neutralWhenModerate() {
        needs.hunger = 50f;
        needs.energy = 50f;
        needs.happiness = 50f;
        assertEquals(PetNeeds.MoodState.NEUTRAL, needs.getMoodState());
    }

    @Test
    public void getMoodState_hungryTakesPriorityOverTired() {
        needs.hunger = 85f;
        needs.energy = 15f;
        needs.happiness = 50f;
        assertEquals(PetNeeds.MoodState.HUNGRY, needs.getMoodState());
    }

    // ============== URGENT ATTENTION ==============

    @Test
    public void needsUrgentAttention_trueWhenHungerCritical() {
        needs.hunger = 95f;
        needs.energy = 50f;
        needs.happiness = 50f;
        assertTrue(needs.needsUrgentAttention());
    }

    @Test
    public void needsUrgentAttention_trueWhenEnergyCritical() {
        needs.hunger = 50f;
        needs.energy = 5f;
        needs.happiness = 50f;
        assertTrue(needs.needsUrgentAttention());
    }

    @Test
    public void needsUrgentAttention_falseWhenHealthy() {
        needs.hunger = 30f;
        needs.energy = 80f;
        needs.happiness = 70f;
        assertFalse(needs.needsUrgentAttention());
    }

    // ============== WELLBEING ==============

    @Test
    public void getOverallWellbeing_perfectWhenAllGood() {
        needs.hunger = 0f;
        needs.energy = 100f;
        needs.happiness = 100f;
        assertEquals(100f, needs.getOverallWellbeing(), 0.01f);
    }

    @Test
    public void getOverallWellbeing_zeroWhenAllBad() {
        needs.hunger = 100f;
        needs.energy = 0f;
        needs.happiness = 0f;
        assertEquals(0f, needs.getOverallWellbeing(), 0.01f);
    }

    @Test
    public void isHealthy_trueWhenGoodState() {
        needs.hunger = 30f;
        needs.energy = 80f;
        needs.happiness = 70f;
        assertTrue(needs.isHealthy());
    }

    @Test
    public void isHealthy_falseWhenHungry() {
        needs.hunger = 60f;
        needs.energy = 80f;
        needs.happiness = 70f;
        assertFalse(needs.isHealthy());
    }

    // ============== RESET ==============

    @Test
    public void reset_restoresDefaults() {
        needs.hunger = 100f;
        needs.energy = 0f;
        needs.happiness = 0f;
        needs.reset();
        assertEquals(50f, needs.hunger, 0.01f);
        assertEquals(100f, needs.energy, 0.01f);
        assertEquals(75f, needs.happiness, 0.01f);
    }

    // ============== LOAD STATE ==============

    @Test
    public void loadState_clampsValues() {
        needs.loadState(150f, -50f, 200f, System.currentTimeMillis());
        assertEquals(100f, needs.hunger, 0.01f);
        assertEquals(0f, needs.energy, 0.01f);
        assertEquals(100f, needs.happiness, 0.01f);
    }

    // ============== STATIC GET ==============

    @Test
    public void get_returnsSameInstanceFromPetState() {
        assertSame(needs, PetNeeds.get());
    }
}
