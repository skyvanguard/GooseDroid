package com.skyvanguard.goozy;

import com.skyvanguard.goozy.engine.GoosePhrases;
import com.skyvanguard.goozy.engine.GoosePhrases.Language;

import org.junit.Test;

import static org.junit.Assert.*;

/** Traducción de las expresiones cortas de la burbuja del ganso (showEmoji). */
public class GoosePhrasesExpressionsTest {

    // La burbuja es chica: las traducciones no pueden ser mucho más largas que el original
    private static final int MAX_EXPRESSION_LENGTH = 20;

    private static final String UNKNOWN_TEXT = "Hola, soy Pancho y tengo hambre";

    // Caritas, símbolos y onomatopeyas que se muestran igual en los dos idiomas
    private static final String[] UNTRANSLATED = {
            ":)", ":(", ":D", "<3", "<3<3<3", ">:)", ">:(", "?", "!", "!!!", "?!", "...", "~",
            "ZZZ", "zzz", "T_T", "X_X", "@_@", "^_^", "LA LA LA", "HONK", "jaja", "100%", ""};

    @Test
    public void spanish_translatesKnownExpressions() {
        assertEquals("*se acicala*", GoosePhrases.localizeExpression("*preen*", Language.SPANISH));
        assertEquals("estiro~", GoosePhrases.localizeExpression("stretch~", Language.SPANISH));
        assertEquals("*bosteza*", GoosePhrases.localizeExpression("*yawn*", Language.SPANISH));
        assertEquals("¿pan?", GoosePhrases.localizeExpression("bread?", Language.SPANISH));
        assertEquals("¡haceme caso!", GoosePhrases.localizeExpression("notice me!", Language.SPANISH));
        assertEquals("¡te quiero!", GoosePhrases.localizeExpression("love u!", Language.SPANISH));
        assertEquals("¡PELEA!", GoosePhrases.localizeExpression("FIGHT!", Language.SPANISH));
        assertEquals("¡QUÉ PLACER!", GoosePhrases.localizeExpression("BLISS!", Language.SPANISH));
    }

    @Test
    public void english_returnsTheOriginalText() {
        for (String expression : GoosePhrases.expressionKeys()) {
            assertSame(expression, GoosePhrases.localizeExpression(expression, Language.ENGLISH));
        }
    }

    @Test
    public void unknownText_isUnchangedInBothLanguages() {
        for (Language language : Language.values()) {
            assertEquals(UNKNOWN_TEXT, GoosePhrases.localizeExpression(UNKNOWN_TEXT, language));
            for (String text : UNTRANSLATED) {
                assertEquals(text + " (" + language + ")", text,
                        GoosePhrases.localizeExpression(text, language));
            }
        }
    }

    @Test
    public void matching_isExact() {
        // Mayúsculas distintas, espacios o texto alrededor no son la misma expresión
        assertEquals("Bread?", GoosePhrases.localizeExpression("Bread?", Language.SPANISH));
        assertEquals(" bread?", GoosePhrases.localizeExpression(" bread?", Language.SPANISH));
        assertEquals("bread? please", GoosePhrases.localizeExpression("bread? please", Language.SPANISH));
    }

    @Test
    public void translations_areShortNotEmptyAndDifferent() {
        assertFalse(GoosePhrases.expressionKeys().isEmpty());
        for (String expression : GoosePhrases.expressionKeys()) {
            String translated = GoosePhrases.localizeExpression(expression, Language.SPANISH);
            String where = "'" + expression + "' -> '" + translated + "'";
            assertNotNull(where, translated);
            assertFalse("Traducción vacía: " + where, translated.trim().isEmpty());
            assertTrue("Más de " + MAX_EXPRESSION_LENGTH + " caracteres: " + where,
                    translated.length() <= MAX_EXPRESSION_LENGTH);
            assertNotEquals("Traducción igual al original: " + where, expression, translated);
        }
    }

    @Test
    public void spanishTemplatePhrases_areNotTranslatedAgain() {
        // Los pensamientos de GooseLLM ya llegan en español a showEmoji: no se tocan
        for (String key : GoosePhrases.keys(Language.SPANISH)) {
            for (String phrase : GoosePhrases.get(key, Language.SPANISH)) {
                assertEquals(key, phrase, GoosePhrases.localizeExpression(phrase, Language.SPANISH));
            }
        }
    }

    @Test
    public void null_isHandled() {
        assertNull(GoosePhrases.localizeExpression(null, Language.SPANISH));
        assertNull(GoosePhrases.localizeExpression(null, Language.ENGLISH));
        assertNull(GoosePhrases.localizeExpression(null, null));
        assertEquals("bread?", GoosePhrases.localizeExpression("bread?", null));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void expressionKeys_isUnmodifiable() {
        GoosePhrases.expressionKeys().add("pisado");
    }
}
