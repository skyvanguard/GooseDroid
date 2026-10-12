package com.skyvanguard.goozy.brain;

import org.junit.Test;

import static org.junit.Assert.*;

public class IntentParserTest {

    @Test
    public void parse_cleanJson_readsAllFields() {
        BrainIntent intent = IntentParser.parse(
                "{\"say\": \"¡Quiero pan!\", \"mood\": \"HUNGRY\", \"action\": \"HONK\", "
                        + "\"remember\": \"A mi humano le gusta el mate\"}");

        assertNotNull(intent);
        assertEquals("¡Quiero pan!", intent.say);
        assertEquals(BrainMood.HUNGRY, intent.mood);
        assertEquals(BrainAction.HONK, intent.action);
        assertEquals("A mi humano le gusta el mate", intent.remember);
    }

    @Test
    public void parse_jsonSurroundedByProse_isExtracted() {
        BrainIntent intent = IntentParser.parse(
                "Claro, acá va:\n{\"say\": \"honk\", \"mood\": \"happy\", \"action\": \"dance\"}\n¡Listo!");

        assertNotNull(intent);
        assertEquals("honk", intent.say);
        assertEquals(BrainMood.HAPPY, intent.mood);
        assertEquals(BrainAction.DANCE, intent.action);
    }

    @Test
    public void parse_jsonInsideCodeFence_isExtracted() {
        BrainIntent intent = IntentParser.parse("```json\n{\"say\": \"hola\"}\n```");

        assertNotNull(intent);
        assertEquals("hola", intent.say);
    }

    @Test
    public void parse_reasoningBlock_isIgnored() {
        BrainIntent intent = IntentParser.parse(
                "<think>el usuario {quiere} algo</think>{\"say\": \"cuac\"}");

        assertNotNull(intent);
        assertEquals("cuac", intent.say);
    }

    @Test
    public void parse_unclosedReasoningBlock_dropsTheRest() {
        assertNull(IntentParser.parse("<think>sigo pensando y nunca termino"));
    }

    @Test
    public void parse_bracesInsideStrings_doNotBreakExtraction() {
        BrainIntent intent = IntentParser.parse("{\"say\": \"mirá esto: } raro {\", \"mood\": \"CURIOUS\"}");

        assertNotNull(intent);
        assertEquals("mirá esto: } raro {", intent.say);
        assertEquals(BrainMood.CURIOUS, intent.mood);
    }

    @Test
    public void parse_escapedQuotesInsideStrings_areHandled() {
        BrainIntent intent = IntentParser.parse("{\"say\": \"dijo \\\"honk\\\" y se fue\"}");

        assertNotNull(intent);
        assertEquals("dijo \"honk\" y se fue", intent.say);
    }

    @Test
    public void parse_unknownActionAndMood_fallBackToDefaults() {
        BrainIntent intent = IntentParser.parse(
                "{\"say\": \"hola\", \"mood\": \"furibundo\", \"action\": \"DELETE_ALL_FILES\"}");

        assertNotNull(intent);
        assertEquals(BrainMood.NEUTRAL, intent.mood);
        assertEquals(BrainAction.NONE, intent.action);
    }

    @Test
    public void parse_actionWithSpacesOrLowercase_isNormalized() {
        assertEquals(BrainAction.PLAY_DEAD,
                IntentParser.parse("{\"action\": \"play dead\"}").action);
        assertEquals(BrainAction.LOOK_AROUND,
                IntentParser.parse("{\"action\": \"look-around\"}").action);
    }

    @Test
    public void parse_actionWithSmallTypos_isRecovered() {
        assertEquals(BrainAction.LOOK_AROUND,
                IntentParser.parse("{\"action\": \"LOOK_ARROUND\"}").action);
        assertEquals(BrainAction.LOOK_AROUND,
                IntentParser.parse("{\"action\": \"LOOKAROUND\"}").action);
        assertEquals(BrainAction.ZOOMIES,
                IntentParser.parse("{\"action\": \"ZOOMIE\"}").action);
    }

    @Test
    public void parse_inventedActions_areNotForcedIntoARealOne() {
        assertEquals(BrainAction.NONE,
                IntentParser.parse("{\"say\": \"x\", \"action\": \"WHISK\"}").action);
        assertEquals(BrainAction.NONE,
                IntentParser.parse("{\"say\": \"x\", \"action\": \"¡ZARPAO!\"}").action);
        assertEquals(BrainAction.NONE,
                IntentParser.parse("{\"say\": \"x\", \"action\": \"NAPA\"}").action);
        assertEquals(BrainAction.NONE,
                IntentParser.parse("{\"say\": \"x\", \"action\": \"EXPLODE\"}").action);
    }

    @Test
    public void parse_nullRemember_countsAsEmpty() {
        BrainIntent intent = IntentParser.parse("{\"say\": \"hola\", \"remember\": null}");

        assertNotNull(intent);
        assertFalse(intent.hasMemory());
    }

    @Test
    public void parse_nonStringValues_countAsEmpty() {
        BrainIntent intent = IntentParser.parse("{\"say\": 42, \"action\": \"SPIN\"}");

        assertNotNull(intent);
        assertEquals("", intent.say);
        assertEquals(BrainAction.SPIN, intent.action);
    }

    @Test
    public void parse_plainSentence_becomesSpeech() {
        BrainIntent intent = IntentParser.parse("  \"Tengo sueño\"  ");

        assertNotNull(intent);
        assertEquals("Tengo sueño", intent.say);
        assertEquals(BrainAction.NONE, intent.action);
    }

    @Test
    public void parse_punctuationOnlySpeech_countsAsSilence() {
        BrainIntent intent = IntentParser.parse("{\"say\": \",\", \"action\": \"DANCE\"}");

        assertNotNull(intent);
        assertFalse(intent.hasSpeech());
        assertEquals(BrainAction.DANCE, intent.action);
        assertNull(IntentParser.parse("{\"say\": \"...\"}"));
        assertNull(IntentParser.parse("  ?!  "));
    }

    @Test
    public void parse_emojiOrShortSymbols_areKept() {
        assertEquals("<3", IntentParser.parse("{\"say\": \"<3\"}").say);
        String goose = new String(Character.toChars(0x1FABF));
        assertEquals(goose, IntentParser.parse("{\"say\": \"" + goose + "\"}").say);
    }

    @Test
    public void parse_unclosedSpeech_isRejected() {
        assertNull(IntentParser.parse("{\"say\": \"hola"));
    }

    @Test
    public void parse_truncatedJson_keepsTheCompleteFields() {
        String truncated = "{\"say\":\"¿Qué sabés de mí? ¡Yo veo todo!\",\n\"mood\":\n\"SAD\",\n"
                + "\"action\":\n\"LOOK_AROUND\",\n\"remember\":\n\"}";

        BrainIntent intent = IntentParser.parse(truncated);

        assertNotNull(intent);
        assertEquals("¿Qué sabés de mí? ¡Yo veo todo!", intent.say);
        assertEquals(BrainMood.SAD, intent.mood);
        assertEquals(BrainAction.LOOK_AROUND, intent.action);
        assertFalse("un recuerdo cortado no se guarda", intent.hasMemory());
    }

    @Test
    public void parse_leadingColonFromConstrainedDecoding_isRemoved() {
        assertEquals("¿Qué hago? Aquí, observando.",
                IntentParser.parse("{\"say\":\":¿Qué hago? Aquí, observando.\"}").say);
        assertEquals("Qué lata.", IntentParser.parse("{\"say\":\": Qué lata.\"}").say);
        assertEquals("¡Pan!", IntentParser.parse("{\"say\":\"¡Pan!\"}").say);
    }

    @Test
    public void findStringField_handlesEscapesAndMissingKeys() {
        assertEquals("dijo \"hola\"",
                IntentParser.findStringField("{\"say\": \"dijo \\\"hola\\\"\"", "say"));
        assertEquals("", IntentParser.findStringField("{\"mood\": \"SAD\"}", "say"));
        assertEquals("", IntentParser.findStringField("{\"say\": 42}", "say"));
    }

    @Test
    public void parse_emptyObject_isRejected() {
        assertNull(IntentParser.parse("{}"));
    }

    @Test
    public void parse_nullOrBlank_isRejected() {
        assertNull(IntentParser.parse(null));
        assertNull(IntentParser.parse("   \n "));
    }

    @Test
    public void parse_longSpeech_isTruncatedAtWordBoundary() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 40; i++) longText.append("palabra ");
        BrainIntent intent = IntentParser.parse("{\"say\": \"" + longText + "\"}");

        assertNotNull(intent);
        assertTrue(intent.say.length() <= BrainIntent.MAX_SAY_LENGTH);
        assertTrue(intent.say.endsWith("…"));
        assertFalse(intent.say.contains("  "));
    }

    @Test
    public void parse_customMaxLength_allowsLongerDiaryText() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 25; i++) longText.append("palabra ");
        String text = longText.toString().trim();

        BrainIntent intent = IntentParser.parse("{\"say\": \"" + text + "\"}", 300);

        assertNotNull(intent);
        assertEquals(text, intent.say);
    }

    @Test
    public void parse_multilineSpeech_isCollapsedToOneLine() {
        BrainIntent intent = IntentParser.parse("{\"say\": \"hola\\n\\n  mundo\"}");

        assertNotNull(intent);
        assertEquals("hola mundo", intent.say);
    }
}
