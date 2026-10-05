package com.cyberalgo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Minimal server-side Gemini REST client.
 * API credentials are read from environment variables and are never exposed to the browser.
 */
public final class GeminiService {

    public static final String DEFAULT_MODEL = "gemini-3.8-flash";
    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public GeminiService() {
        this(
                System.getenv("GEMINI_API_KEY"),
                firstNonBlank(System.getenv("GEMINI_MODEL"), DEFAULT_MODEL),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build(),
                new ObjectMapper());
    }

    GeminiService(String apiKey, String model, HttpClient httpClient, ObjectMapper mapper) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = normalizeModel(model);
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String getModel() {
        return model;
    }

    public String generate(String systemInstruction, String prompt) {
        if (!isConfigured()) {
            throw new IllegalStateException("Gemini AI Tutor is not configured.");
        }

        ObjectNode payload = mapper.createObjectNode();

        ObjectNode system = mapper.createObjectNode();
        ArrayNode systemParts = mapper.createArrayNode();
        systemParts.add(mapper.createObjectNode().put("text", MongoSecrets.redact(systemInstruction, System.getenv())));
        system.set("parts", systemParts);
        payload.set("system_instruction", system);

        ArrayNode contents = mapper.createArrayNode();
        ObjectNode userContent = mapper.createObjectNode();
        userContent.put("role", "user");
        ArrayNode userParts = mapper.createArrayNode();
        userParts.add(mapper.createObjectNode().put("text", MongoSecrets.redact(prompt, System.getenv())));
        userContent.set("parts", userParts);
        contents.add(userContent);
        payload.set("contents", contents);

        ObjectNode generationConfig = mapper.createObjectNode();
        generationConfig.put("temperature", 0.35);
        generationConfig.put("maxOutputTokens", 800);
        payload.set("generationConfig", generationConfig);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(35))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to prepare Gemini request.", ex);
        }

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw apiError(response.statusCode());
            }

            JsonNode root = mapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new IllegalStateException("Gemini returned no tutor response.");
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            StringBuilder answer = new StringBuilder();
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    String text = part.path("text").asText("");
                    if (!text.isBlank()) {
                        if (!answer.isEmpty()) answer.append("\n");
                        answer.append(text);
                    }
                }
            }

            if (answer.isEmpty()) {
                throw new IllegalStateException("Gemini returned an empty tutor response.");
            }

            return answer.toString().trim();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini request was interrupted.", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("Gemini request failed.", ex);
        }
    }

    private static IllegalStateException apiError(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return new IllegalStateException("Gemini API credentials were rejected.");
        }
        if (statusCode == 404) {
            return new IllegalStateException("Configured Gemini model is unavailable.");
        }
        if (statusCode == 429) {
            return new IllegalStateException("Gemini API quota is temporarily exhausted.");
        }
        return new IllegalStateException("Gemini API request failed with status " + statusCode + ".");
    }

    private static String normalizeModel(String model) {
        String value = firstNonBlank(model, DEFAULT_MODEL);
        if (!value.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid GEMINI_MODEL value.");
        }
        return value;
    }

    private static String firstNonBlank(String first, String fallback) {
        return first != null && !first.isBlank() ? first.trim() : fallback;
    }
}
