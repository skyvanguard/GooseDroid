package com.skyvanguard.goozy.brain.backend;

import com.skyvanguard.goozy.brain.LlmCallback;
import com.skyvanguard.goozy.brain.LlmException;
import com.skyvanguard.goozy.brain.LlmRequest;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

public class OpenAiCompatBackendTest {
    private static final long TIMEOUT_SECONDS = 10;

    private MockWebServer server;
    private String baseUrl;
    private String lastRequestBody;
    private String lastAuthHeader;

    /** Junta el resultado de una generación para poder afirmarlo desde el test. */
    private static final class Recorder implements LlmCallback {
        final CountDownLatch finished = new CountDownLatch(1);
        final List<String> tokens = new ArrayList<>();
        volatile String text;
        volatile LlmException error;

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

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
            assertTrue("la generación no terminó", finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        // Dirección literal: el backend solo habla sin cifrar con la red local
        baseUrl = "http://127.0.0.1:" + server.getPort() + "/v1";
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private void respondWith(int status, String contentType, String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", contentType)
                .setBody(body));
    }

    /** Lee el pedido que recibió el servidor. */
    private void captureRequest() throws InterruptedException {
        RecordedRequest recorded = server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull("el servidor no recibió ningún pedido", recorded);
        assertEquals("/v1/chat/completions", recorded.getPath());
        lastAuthHeader = recorded.getHeader("Authorization");
        lastRequestBody = recorded.getBody().readUtf8();
    }

    private static LlmRequest request() {
        return LlmRequest.builder().systemPrompt("sos un ganso").userPrompt("hola").build();
    }

    private static String chunk(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}\n\n";
    }

    @Test
    public void streaming_concatenatesTokensInOrder() throws Exception {
        respondWith(200, "text/event-stream",
                "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}\n\n"
                        + chunk("{\\\"say\\\":") + chunk(" \\\"honk\\\"}")
                        + "data: [DONE]\n\n");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "").generate(request(), recorder);
        recorder.await();

        assertNull(recorder.error);
        assertEquals("{\"say\": \"honk\"}", recorder.text);
        assertEquals(2, recorder.tokens.size());
    }

    @Test
    public void request_hasModelMessagesAndJsonFormat() throws Exception {
        respondWith(200, "text/event-stream", chunk("ok") + "data: [DONE]\n\n");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl + "/", "qwen3", "secreto").generate(request(), recorder);
        recorder.await();
        captureRequest();

        JSONObject body = new JSONObject(lastRequestBody);
        assertEquals("qwen3", body.getString("model"));
        assertTrue(body.getBoolean("stream"));
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"));
        assertEquals("hola", body.getJSONArray("messages").getJSONObject(1).getString("content"));
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"));
        assertEquals("none", body.getString("reasoning_effort"));
        assertEquals("Bearer secreto", lastAuthHeader);
    }

    @Test
    public void requestWithSchema_asksForStrictJson() throws Exception {
        respondWith(200, "text/event-stream", chunk("ok") + "data: [DONE]\n\n");
        Recorder recorder = new Recorder();
        LlmRequest withSchema = LlmRequest.builder().userPrompt("hola")
                .jsonSchema("{\"type\":\"object\"}").build();

        new OpenAiCompatBackend(baseUrl, "qwen3", "").generate(withSchema, recorder);
        recorder.await();
        captureRequest();

        JSONObject format = new JSONObject(lastRequestBody).getJSONObject("response_format");
        assertEquals("json_schema", format.getString("type"));
        assertTrue(format.getJSONObject("json_schema").getBoolean("strict"));
        assertEquals("object",
                format.getJSONObject("json_schema").getJSONObject("schema").getString("type"));
    }

    @Test
    public void serverRejectingStrictMode_isRetriedWithBasicFormatAndRemembered() throws Exception {
        respondWith(400, "application/json", "{\"error\":\"unknown field\"}");
        respondWith(200, "text/event-stream", chunk("uno") + "data: [DONE]\n\n");
        respondWith(200, "text/event-stream", chunk("dos") + "data: [DONE]\n\n");
        OpenAiCompatBackend backend = new OpenAiCompatBackend(baseUrl, "viejo", "");
        LlmRequest withSchema = LlmRequest.builder().userPrompt("hola")
                .jsonSchema("{\"type\":\"object\"}").build();

        Recorder first = new Recorder();
        backend.generate(withSchema, first);
        first.await();
        assertNull(first.error);
        assertEquals("uno", first.text);

        captureRequest();
        assertTrue(new JSONObject(lastRequestBody).has("reasoning_effort"));
        captureRequest();
        JSONObject retry = new JSONObject(lastRequestBody);
        assertFalse(retry.has("reasoning_effort"));
        assertEquals("json_object", retry.getJSONObject("response_format").getString("type"));

        Recorder second = new Recorder();
        backend.generate(withSchema, second);
        second.await();
        assertEquals("dos", second.text);
        captureRequest();
        assertFalse("el segundo pedido ya va en formato básico",
                new JSONObject(lastRequestBody).has("reasoning_effort"));
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void request_withoutApiKey_sendsNoAuthorizationHeader() throws Exception {
        respondWith(200, "text/event-stream", chunk("ok") + "data: [DONE]\n\n");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "").generate(request(), recorder);
        recorder.await();
        captureRequest();

        assertNull(lastAuthHeader);
    }

    @Test
    public void nonStreamingResponse_isAlsoUnderstood() throws Exception {
        respondWith(200, "application/json",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"cuac\"}}]}");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "").generate(request(), recorder);
        recorder.await();

        assertNull(recorder.error);
        assertEquals("cuac", recorder.text);
    }

    @Test
    public void unauthorized_isReportedAsAuthError() throws Exception {
        respondWith(401, "application/json", "{\"error\":\"bad key\"}");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "mala").generate(request(), recorder);
        recorder.await();

        assertNotNull(recorder.error);
        assertEquals(LlmException.Kind.AUTH, recorder.error.getKind());
        assertFalse(recorder.error.isRetryable());
    }

    @Test
    public void tooManyRequests_isRetryable() throws Exception {
        respondWith(429, "application/json", "{}");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "").generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.RATE_LIMITED, recorder.error.getKind());
        assertTrue(recorder.error.isRetryable());
    }

    @Test
    public void serverError_isBadResponse() throws Exception {
        respondWith(500, "text/plain", "se rompió");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "").generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.BAD_RESPONSE, recorder.error.getKind());
        assertTrue(recorder.error.getMessage().contains("500"));
    }

    @Test
    public void emptyStream_isBadResponse() throws Exception {
        respondWith(200, "text/event-stream", "data: [DONE]\n\n");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "").generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.BAD_RESPONSE, recorder.error.getKind());
    }

    @Test
    public void redirect_isNotFollowed() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302)
                .setHeader("Location", "http://8.8.8.8/v1/chat/completions"));
        respondWith(200, "text/event-stream", chunk("no debe llegar") + "data: [DONE]\n\n");
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "secreto").generate(request(), recorder);
        recorder.await();

        assertNotNull(recorder.error);
        assertEquals(LlmException.Kind.BAD_RESPONSE, recorder.error.getKind());
        assertTrue(recorder.error.getMessage().contains("302"));
        assertEquals("un solo pedido: la redirección no se siguió", 1, server.getRequestCount());
    }

    @Test
    public void hugeStreamingLine_isRejectedInsteadOfExhaustingMemory() throws Exception {
        StringBuilder huge = new StringBuilder("data: ");
        for (int i = 0; i < SseReader.MAX_LINE_CHARS + 10; i++) huge.append('x');
        respondWith(200, "text/event-stream", huge.toString());
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend(baseUrl, "qwen3", "").generate(request(), recorder);
        recorder.await();

        assertNotNull(recorder.error);
        assertEquals(LlmException.Kind.NETWORK, recorder.error.getKind());
    }

    @Test
    public void unreachableServer_isNetworkError() throws Exception {
        int port = server.getPort();
        server.shutdown();
        Recorder recorder = new Recorder();

        new OpenAiCompatBackend("http://127.0.0.1:" + port + "/v1", "qwen3", "")
                .generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.NETWORK, recorder.error.getKind());
        assertTrue(recorder.error.isRetryable());
    }

    @Test
    public void missingConfiguration_isUnavailableWithoutTouchingTheNetwork() throws Exception {
        Recorder recorder = new Recorder();
        OpenAiCompatBackend backend = new OpenAiCompatBackend("", "qwen3", "");

        assertFalse(backend.isAvailable());
        backend.generate(request(), recorder);
        recorder.await();

        assertEquals(LlmException.Kind.UNAVAILABLE, recorder.error.getKind());
    }

    @Test
    public void plainHttp_isOnlyAllowedTowardsTheLocalNetwork() {
        assertTrue(new OpenAiCompatBackend("http://192.168.1.20:11434/v1", "m", "").isAvailable());
        assertFalse(new OpenAiCompatBackend("http://api.example.com/v1", "m", "").isAvailable());
        assertFalse(new OpenAiCompatBackend("http://8.8.8.8/v1", "m", "").isAvailable());
    }

    @Test
    public void isAvailable_requiresHttpSchemeAndModel() {
        assertFalse(new OpenAiCompatBackend("ftp://host/v1", "m", "").isAvailable());
        assertFalse(new OpenAiCompatBackend("https://host/v1", " ", "").isAvailable());
        assertTrue(new OpenAiCompatBackend("https://host/v1", "m", "").isAvailable());
    }

    @Test
    public void normalizeBaseUrl_acceptsCommonVariants() {
        assertEquals("http://h:1/v1", OpenAiCompatBackend.normalizeBaseUrl(" http://h:1/v1/ "));
        assertEquals("http://h:1/v1",
                OpenAiCompatBackend.normalizeBaseUrl("http://h:1/v1/chat/completions"));
        assertEquals("", OpenAiCompatBackend.normalizeBaseUrl(null));
    }
}
