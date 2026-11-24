package com.skyvanguard.goozy.brain;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class GooseBrainTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** Backend de prueba: responde lo que se le configure, o queda esperando. */
    private static final class FakeBackend implements LlmBackend {
        final String id;
        String response;
        LlmException error;
        boolean isAvailable = true;
        boolean isHolding = false;
        int generateCount = 0;
        int cancelCount = 0;
        int releaseCount = 0;
        LlmRequest lastRequest;
        LlmCallback heldCallback;

        FakeBackend(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return isAvailable;
        }

        @Override
        public void generate(LlmRequest request, LlmCallback callback) {
            generateCount++;
            lastRequest = request;
            if (isHolding) {
                heldCallback = callback;
            } else if (error != null) {
                callback.onError(error);
            } else {
                callback.onDone(response);
            }
        }

        @Override
        public void cancel() {
            cancelCount++;
        }

        @Override
        public void release() {
            releaseCount++;
        }
    }

    private static final class Recorder implements GooseBrain.Listener {
        final List<BrainIntent> intents = new ArrayList<>();
        final List<String> backendIds = new ArrayList<>();
        final List<LlmException> errors = new ArrayList<>();

        @Override
        public void onIntent(BrainIntent intent, BrainTrigger trigger, String backendId) {
            intents.add(intent);
            backendIds.add(backendId);
        }

        @Override
        public void onBackendError(LlmException error, String backendId) {
            errors.add(error);
        }
    }

    private FakeBackend primary;
    private FakeBackend fallback;
    private BrainMemory memory;
    private Recorder recorder;
    private GooseBrain brain;
    private long nowMs;

    private static final PetSnapshot PET = PetSnapshot.builder().petName("Pancho").build();
    private static final BrainTrigger IDLE = BrainTrigger.of(BrainTrigger.Kind.IDLE_THOUGHT);
    private static final BrainTrigger PETTED = BrainTrigger.of(BrainTrigger.Kind.PETTED);

    @Before
    public void setUp() {
        primary = new FakeBackend("primary");
        primary.response = "{\"say\": \"hola desde el modelo\", \"action\": \"DANCE\"}";
        fallback = new FakeBackend("fallback");
        fallback.response = "honk";
        memory = new BrainMemory(new File(folder.getRoot(), "memory.json"));
        recorder = new Recorder();
        nowMs = 1_000_000L;

        brain = new GooseBrain(primary, fallback, new PromptBuilder("español"), memory,
                Runnable::run, () -> nowMs);
        brain.setListener(recorder);
    }

    @Test
    public void think_deliversTheParsedIntentFromThePrimary() {
        assertTrue(brain.think(PET, IDLE));

        assertEquals(1, recorder.intents.size());
        assertEquals("hola desde el modelo", recorder.intents.get(0).say);
        assertEquals(BrainAction.DANCE, recorder.intents.get(0).action);
        assertEquals("primary", recorder.backendIds.get(0));
        assertEquals(0, fallback.generateCount);
        assertFalse(brain.isThinking());
    }

    @Test
    public void primaryError_fallsBackSoTheGooseIsNeverMute() {
        primary.error = new LlmException(LlmException.Kind.NETWORK, "sin red");

        brain.think(PET, IDLE);

        assertEquals(1, recorder.intents.size());
        assertEquals("honk", recorder.intents.get(0).say);
        assertEquals("fallback", recorder.backendIds.get(0));
        assertEquals(1, recorder.errors.size());
        assertEquals(LlmException.Kind.NETWORK, recorder.errors.get(0).getKind());
    }

    @Test
    public void unparseableResponse_fallsBack() {
        primary.response = "{\"say\": \"roto";

        brain.think(PET, IDLE);

        assertEquals("fallback", recorder.backendIds.get(0));
        assertEquals(LlmException.Kind.BAD_RESPONSE, recorder.errors.get(0).getKind());
    }

    @Test
    public void copiedToneExample_losesTheSpeechButKeepsTheAction() {
        primary.response = "{\"say\": \"¿Eso que tenés es pan? Compartí.\", "
                + "\"mood\": \"HUNGRY\", \"action\": \"HONK\"}";

        brain.think(PET, PETTED);

        BrainIntent intent = recorder.intents.get(0);
        assertFalse(intent.hasSpeech());
        assertEquals(BrainAction.HONK, intent.action);
        assertEquals("primary", recorder.backendIds.get(0));
    }

    @Test
    public void copiedToneExampleWithNothingElse_fallsBack() {
        primary.response = "{\"say\": \"Todos duermen. Momento perfecto para el caos.\"}";

        brain.think(PET, PETTED);

        assertEquals("fallback", recorder.backendIds.get(0));
    }

    @Test
    public void unavailablePrimary_usesFallbackDirectly() {
        primary.isAvailable = false;

        brain.think(PET, IDLE);

        assertEquals(0, primary.generateCount);
        assertEquals("fallback", recorder.backendIds.get(0));
        assertTrue(recorder.errors.isEmpty());
        assertEquals("fallback", brain.getActiveBackendId());
    }

    @Test
    public void nullPrimary_usesFallback() {
        brain.setPrimary(null);

        brain.think(PET, IDLE);

        assertEquals("fallback", recorder.backendIds.get(0));
    }

    @Test
    public void fallbackFailure_deliversNothingAndFreesTheBrain() {
        primary.error = new LlmException(LlmException.Kind.NETWORK, "sin red");
        fallback.error = new LlmException(LlmException.Kind.BAD_RESPONSE, "nada");

        brain.think(PET, IDLE);

        assertTrue(recorder.intents.isEmpty());
        assertFalse(brain.isThinking());
    }

    @Test
    public void spontaneousThoughts_respectTheMinimumInterval() {
        assertTrue(brain.think(PET, IDLE));
        nowMs += GooseBrain.DEFAULT_MIN_INTERVAL_MS - 1;
        assertFalse(brain.think(PET, IDLE));
        nowMs += 1;
        assertTrue(brain.think(PET, IDLE));

        assertEquals(2, primary.generateCount);
    }

    @Test
    public void userInitiatedTriggers_bypassTheInterval() {
        assertTrue(brain.think(PET, IDLE));
        assertTrue(brain.think(PET, PETTED));
        assertTrue(brain.think(PET, new BrainTrigger(BrainTrigger.Kind.CHAT, "hola")));

        assertEquals(3, primary.generateCount);
    }

    @Test
    public void userInitiatedTriggers_doNotDelayTheNextSpontaneousThought() {
        assertTrue(brain.think(PET, PETTED));
        assertTrue(brain.think(PET, IDLE));
    }

    @Test
    public void whileThinking_newRequestsAreDropped() {
        primary.isHolding = true;

        assertTrue(brain.think(PET, PETTED));
        assertTrue(brain.isThinking());
        assertFalse(brain.think(PET, PETTED));

        primary.heldCallback.onDone("{\"say\": \"listo\"}");

        assertFalse(brain.isThinking());
        assertEquals(1, recorder.intents.size());
        assertEquals(1, primary.generateCount);
    }

    @Test
    public void cancel_discardsTheLateAnswer() {
        primary.isHolding = true;
        brain.think(PET, PETTED);

        brain.cancel();
        primary.heldCallback.onDone("{\"say\": \"tarde\"}");

        assertTrue(recorder.intents.isEmpty());
        assertEquals(1, primary.cancelCount);
        assertFalse(brain.isThinking());
        assertTrue(brain.think(PET, PETTED));
    }

    @Test
    public void cancelledError_doesNotTriggerTheFallback() {
        primary.error = new LlmException(LlmException.Kind.CANCELLED, "cancelado");

        brain.think(PET, PETTED);

        assertEquals(0, fallback.generateCount);
        assertTrue(recorder.intents.isEmpty());
        assertTrue(recorder.errors.isEmpty());
    }

    @Test
    public void repeatedFailures_restThePrimaryForAWhile() {
        primary.error = new LlmException(LlmException.Kind.NETWORK, "sin red");
        for (int i = 0; i < GooseBrain.FAILURES_BEFORE_BACKOFF; i++) {
            brain.think(PET, PETTED);
        }
        assertEquals(GooseBrain.FAILURES_BEFORE_BACKOFF, primary.generateCount);

        brain.think(PET, PETTED);
        assertEquals("durante el descanso no se llama al principal",
                GooseBrain.FAILURES_BEFORE_BACKOFF, primary.generateCount);

        nowMs += GooseBrain.BACKOFF_MS;
        primary.error = null;
        brain.think(PET, PETTED);
        assertEquals(GooseBrain.FAILURES_BEFORE_BACKOFF + 1, primary.generateCount);
        assertEquals("primary", recorder.backendIds.get(recorder.backendIds.size() - 1));
    }

    @Test
    public void rememberField_isStoredWhenTheHumanSaidSomething() {
        primary.response = "{\"say\": \"ok\", \"remember\": \"Mi humano se llama Pedro\"}";

        brain.think(PET, new BrainTrigger(BrainTrigger.Kind.CHAT, "me llamo Pedro"));

        assertEquals(1, memory.getFacts().size());
        assertEquals("Mi humano se llama Pedro", memory.getFacts().get(0));
        assertTrue(recorder.intents.get(0).hasMemory());
    }

    @Test
    public void rememberField_isDroppedOutsideOfChat() {
        primary.response = "{\"say\": \"ok\", \"remember\": \"hoy es miércoles\"}";

        brain.think(PET, PETTED);
        brain.think(PET, IDLE);

        assertTrue(memory.getFacts().isEmpty());
        assertFalse(recorder.intents.get(0).hasMemory());
    }

    @Test
    public void memories_areSentInTheNextPrompt() {
        memory.remember("Le gusta el mate");

        brain.think(PET, PETTED);

        assertTrue(primary.lastRequest.userPrompt.contains("Le gusta el mate"));
    }

    @Test
    public void diary_allowsLongerTextThanAThought() {
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 25; i++) longText.append("palabra ");
        String text = longText.toString().trim();
        primary.response = "{\"say\": \"" + text + "\"}";

        brain.think(PET, BrainTrigger.of(BrainTrigger.Kind.DIARY));

        assertEquals(text, recorder.intents.get(0).say);
    }

    @Test
    public void setPrimary_releasesThePreviousBackend() {
        FakeBackend replacement = new FakeBackend("replacement");
        replacement.response = "{\"say\": \"nuevo\"}";

        brain.setPrimary(replacement);
        brain.think(PET, PETTED);

        assertEquals(1, primary.releaseCount);
        assertEquals("replacement", recorder.backendIds.get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void fallbackIsMandatory() {
        new GooseBrain(primary, null, new PromptBuilder("español"), memory,
                Runnable::run, () -> nowMs);
    }
}
