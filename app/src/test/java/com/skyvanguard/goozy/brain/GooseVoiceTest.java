package com.skyvanguard.goozy.brain;

import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.*;

public class GooseVoiceTest {

    @Test
    public void toSpeakable_removesStageDirectionsEmojiAndFaces() {
        assertEquals("¡Hola! qué tal",
                GooseVoice.toSpeakable("¡Hola! *se estira* qué tal :D"));
        assertEquals("te quiero", GooseVoice.toSpeakable("te quiero <3"));
        String goose = new String(Character.toChars(0x1FABF));
        assertEquals("pan", GooseVoice.toSpeakable(goose + " pan " + goose));
    }

    @Test
    public void toSpeakable_withNothingToSay_isEmpty() {
        assertEquals("", GooseVoice.toSpeakable("*camina*"));
        assertEquals("", GooseVoice.toSpeakable(":) <3 ..."));
        assertEquals("", GooseVoice.toSpeakable(null));
    }

    @Test
    public void toSpeakable_keepsNormalPunctuation() {
        assertEquals("¿Eso es pan? ¡Compartí!", GooseVoice.toSpeakable("¿Eso es pan? ¡Compartí!"));
    }

    @Test
    public void localeFor_picksTheLanguage() {
        assertEquals("es", GooseVoice.localeFor("español").getLanguage());
        assertEquals("en", GooseVoice.localeFor("English").getLanguage());
        assertEquals("pt", GooseVoice.localeFor("português").getLanguage());
        assertEquals("es", GooseVoice.localeFor(null).getLanguage());
        assertEquals(Locale.ENGLISH, GooseVoice.localeFor("en"));
    }
}
