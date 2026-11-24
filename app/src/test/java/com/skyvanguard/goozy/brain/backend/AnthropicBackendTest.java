package com.skyvanguard.goozy.brain.backend;

import com.skyvanguard.goozy.brain.LlmCallback;
import com.skyvanguard.goozy.brain.LlmException;
import com.skyvanguard.goozy.brain.LlmRequest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

/**
 * Prueba el backend de Claude contra un servidor falso: verifica la forma del
 * pedido y cómo se interpretan las respuestas, sin llamar a la API real.
 */
public class AnthropicBackendTest {
    private static final long TIMEOUT_SECONDS = 20;
    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"say\":{\"type\":\"string\"}},"
                    + "\"required\":[\"say\"],\"additionalProperties\":false}";

    private MockWebServer server;
    private String baseUrl;

    private static final class Recorder implements LlmCallback {
        final CountDownLatch finished = new CountDownLatch(1);
        volatile String text;
        volatile LlmException error;

        @Override
        public void onDone(String fullText) {
            text = fullText;
            finished.countDown();
        }

        @Override
        public void onError(LlmException e) {
            error = e;
            finished.countDown();
        }

        void await() throws InterruptedException {
            assertTrue("la generación no terminó",
                    finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getPort();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private static String message(String stopReason, String text) {
        return "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"model\":\"claude-opus-5\",\"content\":[{\"type\":\"text\",\"text\":"
                + JSONObject.quote(text) + "}],\"stop_reason\":\"" + stopReason + "\","
                + "\"stop_sequence\":null,\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";
    }

    private void respond(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status)
                .setHeader("Content-Type", "application/json").setBody(body));
    }

    private static LlmRequest request() {
        return LlmRequest.builder().systemPrompt("sos un ganso").userPrompt("hola")
                .maxTokens(160).jsonSchema(SCHEMA).build();
    }

    @Test
    public void request_hasModelSystemSchemaEffortAndFallbacks() throws Exception {
        respond(200, message("end_turn", "{\"say\":\"honk\"}"));
        Recorder recorder = new Recorder();

        new AnthropicBackend("claude-opus-5", "clave", baseUrl).generate(request(), recorder);
        recorder.await();

        RecordedRequest recorded = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertTrue(recorded.getPath().startsWith("/v1/messages"));
        assertEquals("clave", recorded.getHeader("x-api-key"));
        assertTrue(recorded.getHeader("anthropic-beta").contains("server-side-fallback-2026-07-01"));

        JSONObject body = new JSONObject(recorded.getBody().readUtf8());
        assertEquals("claude-opus-5", body.getString("model"));
        assertEquals("sos un ganso", body.getString("system"));
        assertTrue("el pensamiento necesita margen", body.getLong("max_tokens") >= 4096);
        assertFalse("Opus 5 rechaza temperature", body.has("temperature"));
        assertEquals("default", body.getString("fallbacks"));
        JSONObject outputConfig = body.getJSONObject("output_config");
        assertEquals("low", outputConfig.getString("effort"));
        JSONObject format = outputConfig.getJSONObject("format");
        assertEquals("json_schema", format.getString("type"));
        assertEquals("object", format.getJSONObject("schema").getString("type"));
        assertFalse(format.getJSONObject("schema").getBoolean("additionalProperties"));

        assertNull(recorder.error);
        assertEquals("{\"say\":\"honk\"}", recorder.text);
    }

    @Test
    public void emptyModel_usesTheDefault() throws Exception {
        respond(200, message("end_turn", "{\"say\":\"honk\"}"));
        Recorder recorder = new Recorder();

        new AnthropicBackend("  ", "clave", baseUrl).generate(request(), recorder);
        recorder.await();

        JSONObject body = new JSONObject(server.takeRequest().getBody().readUtf8());
        assertEquals(AnthropicBackend.DEFAULT_MODEL, body.getString("model"));
    }

    @Test
    public void refusal_isReportedAsRefused() throws Exception {
        respond(200, message("refusal", ""));
        Recorder recorder = new Recorder();

        new AnthropicBackend("claude-opus-5", "clave", baseUrl).generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.REFUSED, recorder.error.getKind());
    }

    @Test
    public void unauthorized_isAuthError() throws Exception {
        respond(401, "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\","
                + "\"message\":\"invalid x-api-key\"}}");
        Recorder recorder = new Recorder();

        new AnthropicBackend("claude-opus-5", "mala", baseUrl).generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.AUTH, recorder.error.getKind());
        assertFalse(recorder.error.isRetryable());
    }

    @Test
    public void badRequest_isBadResponseWithTheServerMessage() throws Exception {
        respond(400, "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\","
                + "\"message\":\"model: not found\"}}");
        Recorder recorder = new Recorder();

        new AnthropicBackend("modelo-inventado", "clave", baseUrl).generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.BAD_RESPONSE, recorder.error.getKind());
    }

    @Test
    public void missingKey_isUnavailableWithoutNetwork() throws Exception {
        Recorder recorder = new Recorder();
        AnthropicBackend backend = new AnthropicBackend("claude-opus-5", "", baseUrl);

        assertFalse(backend.isAvailable());
        backend.generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.UNAVAILABLE, recorder.error.getKind());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void unreachableServer_isNetworkError() throws Exception {
        int port = server.getPort();
        server.shutdown();
        Recorder recorder = new Recorder();

        new AnthropicBackend("claude-opus-5", "clave", "http://127.0.0.1:" + port)
                .generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.NETWORK, recorder.error.getKind());
    }
}
