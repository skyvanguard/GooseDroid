package com.skyvanguard.goozy;

import com.skyvanguard.goozy.engine.GoosePhrases;
import com.skyvanguard.goozy.engine.GoosePhrases.Language;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

public class GoosePhrasesTest {

    private static final int MAX_PHRASE_LENGTH = 30;

    // Categorías que elige GooseLLM.generateLocalThought (las de evolución se agregan por etapa)
    private static final List<String> THOUGHT_CATEGORIES = Arrays.asList(
            "starving", "hungry", "peckish", "exhausted", "tired", "sleepy", "sad", "lonely",
            "dawn", "morning", "lunchtime", "afternoon", "evening", "night", "weekend",
            "newmonth", "holiday", "halloween",
            "playful", "mischievous", "affectionate", "brave",
            "veryhappy", "happy", "verysad", "verybored", "bored", "excited",
            "loved", "loyal", "bestfriends",
            "reaction_pet", "reaction_feed", "reaction_play",
            GoosePhrases.AMBIENT);

    private static final List<String> FIXED_KEYS = Arrays.asList(
            GoosePhrases.RESPONSE_PET_BEST_FRIEND, GoosePhrases.RESPONSE_PET_HIGH,
            GoosePhrases.RESPONSE_PET_MID, GoosePhrases.RESPONSE_PET_LOW,
            GoosePhrases.RESPONSE_FEED_STARVING, GoosePhrases.RESPONSE_FEED_HUNGRY,
            GoosePhrases.RESPONSE_FEED_FULL, GoosePhrases.RESPONSE_PLAY_HIGH,
            GoosePhrases.RESPONSE_PLAY_MID, GoosePhrases.RESPONSE_PLAY_TIRED,
            GoosePhrases.RESPONSE_DRAG, GoosePhrases.RESPONSE_GREET,
            GoosePhrases.RESPONSE_FAREWELL, GoosePhrases.RESPONSE_DEFAULT,
            GoosePhrases.FAVORITE_TIME);

    private static final List<String> MILESTONE_KEYS = Arrays.asList(
            GoosePhrases.MILESTONE_PETS_1000, GoosePhrases.MILESTONE_PETS_500,
            GoosePhrases.MILESTONE_PETS_100, GoosePhrases.MILESTONE_FEEDS_100,
            GoosePhrases.MILESTONE_PLAYS_100, GoosePhrases.MILESTONE_DAYS_365,
            GoosePhrases.MILESTONE_DAYS_100, GoosePhrases.MILESTONE_DAYS_30,
            GoosePhrases.MILESTONE_DAYS_7);

    private static List<String> allUsedKeys() {
        List<String> keys = new ArrayList<>(THOUGHT_CATEGORIES);
        for (GooseEvolution.Stage stage : GooseEvolution.Stage.values()) {
            keys.add("evolution_" + stage.name().toLowerCase());
        }
        keys.addAll(FIXED_KEYS);
        keys.addAll(MILESTONE_KEYS);
        return keys;
    }

    // ============== TABLAS ==============

    @Test
    public void everyUsedKey_hasPhrasesInBothLanguages() {
        for (Language language : Language.values()) {
            for (String key : allUsedKeys()) {
                String[] phrases = GoosePhrases.get(key, language);
                assertNotNull("Falta '" + key + "' en " + language, phrases);
                assertTrue("Sin frases para '" + key + "' en " + language, phrases.length > 0);
            }
        }
    }

    @Test
    public void bothLanguages_haveTheSameKeys() {
        assertEquals(GoosePhrases.keys(Language.ENGLISH), GoosePhrases.keys(Language.SPANISH));
        assertEquals(allUsedKeys().size(), GoosePhrases.keys(Language.SPANISH).size());
    }

    @Test
    public void noPhrase_isEmptyOrTooLong() {
        for (Language language : Language.values()) {
            for (String key : GoosePhrases.keys(language)) {
                for (String phrase : GoosePhrases.get(key, language)) {
                    String where = "'" + phrase + "' (" + key + ", " + language + ")";
                    assertNotNull("Frase nula en " + key, phrase);
                    assertFalse("Frase vacía en " + key + ", " + language, phrase.trim().isEmpty());
                    assertTrue("Frase de más de " + MAX_PHRASE_LENGTH + " caracteres: " + where,
                            phrase.length() <= MAX_PHRASE_LENGTH);
                }
            }
        }
    }

    @Test
    public void milestones_haveExactlyOnePhrase() {
        for (Language language : Language.values()) {
            for (String key : MILESTONE_KEYS) {
                assertEquals(key + " en " + language, 1, GoosePhrases.get(key, language).length);
            }
        }
    }

    @Test
    public void englishTable_keepsOriginalTexts() {
        assertEquals("HUNGRY!!!", GoosePhrases.get("starving", Language.ENGLISH)[0]);
        assertEquals("100 PETS!!! <3", GoosePhrases.get(GoosePhrases.MILESTONE_PETS_100, Language.ENGLISH)[0]);
        assertEquals(76, GoosePhrases.get(GoosePhrases.AMBIENT, Language.ENGLISH).length);
    }

    @Test
    public void unknownKey_returnsNull() {
        // GooseLLM usa el null para caer en el pensamiento suelto (p. ej. "reaction_sleep")
        assertNull(GoosePhrases.get("reaction_sleep", Language.SPANISH));
        assertNull(GoosePhrases.get("reaction_sleep", Language.ENGLISH));
    }

    @Test
    public void get_returnsDefensiveCopy() {
        String[] first = GoosePhrases.get("hungry", Language.SPANISH);
        String original = first[0];
        first[0] = "pisado";
        assertEquals(original, GoosePhrases.get("hungry", Language.SPANISH)[0]);
    }

    // ============== IDIOMA ==============

    @Test
    public void parseLanguage_spanishNames() {
        for (String name : new String[]{"español", "Español", "ESPAÑOL", "espanol", "Spanish",
                "spanish", "es", "ES", "es-PY", "  español  ", "Éspañol"}) {
            assertEquals(name, Language.SPANISH, GoosePhrases.parseLanguage(name));
        }
    }

    @Test
    public void parseLanguage_otherNames_areEnglish() {
        for (String name : new String[]{"english", "English", "en", "inglés", "français", "portugués"}) {
            assertEquals(name, Language.ENGLISH, GoosePhrases.parseLanguage(name));
        }
    }

    @Test
    public void parseLanguage_unset_usesSpanishDefault() {
        assertEquals(Language.SPANISH, GoosePhrases.DEFAULT_LANGUAGE);
        assertEquals(Language.SPANISH, GoosePhrases.parseLanguage(null));
        assertEquals(Language.SPANISH, GoosePhrases.parseLanguage(""));
        assertEquals(Language.SPANISH, GoosePhrases.parseLanguage("   "));
    }

    @Test
    public void parseLanguage_ignoresDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            // En turco "I".toLowerCase() no da "i"; no debe afectar la detección
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(Language.SPANISH, GoosePhrases.parseLanguage("SPANISH"));
            assertEquals(Language.ENGLISH, GoosePhrases.parseLanguage("ENGLISH"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
