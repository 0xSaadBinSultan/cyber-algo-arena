package com.cyberalgo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class GeminiServiceTest {
    HttpServer server;
    final AtomicInteger calls = new AtomicInteger();
    final ObjectMapper json = new ObjectMapper();
    @AfterEach void close() { if (server != null) server.stop(0); }

    GeminiService service(int failures, int status, AtomicReference<String> requestBody) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/models/gemini-3.8-flash:generateContent", e -> {
            requestBody.set(new String(e.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            int code = calls.incrementAndGet() <= failures ? status : 200;
            String response = code == 200 ? "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"Private reasoning\"},{\"text\":\"Inspect the encoding alphabet.\"}]}}]}" : "{}";
            byte[] bytes = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            e.sendResponseHeaders(code, bytes.length);e.getResponseBody().write(bytes);e.close();
        });
        server.start();
        return new GeminiService("test-key", "gemini-3.8-flash", HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1).build(), json,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/models/"), Duration.ZERO);
    }

    @Test void transientOverloadRetriesAndReturnsOnlyFinalTextWithCurrentConfig() throws Exception {
        var body = new AtomicReference<String>();
        GeminiService gemini = service(1, 503, body);
        assertEquals("Inspect the encoding alphabet.", gemini.generate("Tutor", "Hint"));
        assertEquals(2, calls.get());
        var config = json.readTree(body.get()).path("generationConfig");
        assertFalse(config.has("temperature"));
        assertEquals("low", config.path("thinkingConfig").path("thinkingLevel").asText());
        assertEquals(4096, config.path("maxOutputTokens").asInt());
    }

    @Test void rejectedCredentialIsNotRetried() throws Exception {
        GeminiService gemini = service(5, 403, new AtomicReference<>());
        assertThrows(IllegalStateException.class, () -> gemini.generate("Tutor", "Hint"));
        assertEquals(1, calls.get());
    }

    @Test void persistentOutageReturnsLabeledGuideAndPausesProviderRequests() throws Exception {
        AiTutorService tutor = new AiTutorService(service(10, 503, new AtomicReference<>()));
        var challenge = PracticeCatalog.securityLabs().get(0);
        var reply = tutor.reply(challenge, "HINT", 2, "", "", "");
        assertEquals("study_guide", reply.source()); assertEquals("", reply.model());
        assertTrue(reply.notice().contains("unavailable"));
        assertTrue(reply.message().contains("Investigation strategy"));
        assertFalse(reply.message().contains(challenge.getFlagHash()));
        assertFalse(reply.message().contains("flag{"));
        tutor.reply(challenge, "EXPLAIN", 1, "", "", "");
        assertEquals(2, calls.get());
    }

    @Test void successfulResponseIsLabeledGemini() throws Exception {
        AiTutorService tutor = new AiTutorService(service(0, 503, new AtomicReference<>()));
        var reply = tutor.reply(PracticeCatalog.securityLabs().get(0), "HINT", 1, "", "", "");
        assertEquals("gemini", reply.source()); assertEquals("gemini-3.8-flash", reply.model());
        assertEquals("", reply.notice());
    }

    @Test void missingKeyOffersCategoryGuidesWithoutPretendingToReviewCode() {
        var gemini = new GeminiService("", "gemini-3.8-flash", HttpClient.newHttpClient(), json);
        var tutor = new AiTutorService(gemini);
        for (CTFChallenge challenge : PracticeCatalog.securityLabs()) {
            var reply = tutor.reply(challenge, "EXPLAIN", 1, "", "", "");
            assertEquals("study_guide", reply.source());
            assertTrue(reply.message().contains("Core concept"));
            assertFalse(reply.message().contains("flag{"));
            assertFalse(reply.message().contains(challenge.getFlagHash()));
        }
        var review = tutor.reply(PracticeCatalog.securityLabs().get(0), "CODE_REVIEW", 1, "", "code", "");
        assertTrue(review.message().contains("does not analyze your code"));
        assertThrows(IllegalArgumentException.class, () -> tutor.reply(PracticeCatalog.securityLabs().get(0), "BAD_MODE", 1, "", "", ""));
    }
}
