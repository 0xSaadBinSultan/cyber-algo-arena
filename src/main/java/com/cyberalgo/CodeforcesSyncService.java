package com.cyberalgo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Service to synchronize real competitive programming problems from Codeforces API directly into MongoDB.
 */
public final class CodeforcesSyncService {

    private static final String PROBLEMSET_API = "https://codeforces.com/api/problemset.problems";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public CodeforcesSyncService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.mapper = new ObjectMapper();
    }

    public SyncResult sync(ContestEngine engine, int count, int minRating, int maxRating) {
        Objects.requireNonNull(engine, "engine must not be null");
        int targetCount = Math.max(1, Math.min(count, 50));
        int effectiveMin = Math.max(800, minRating);
        int effectiveMax = Math.max(effectiveMin, maxRating);

        List<Map<String, Object>> syncedProblems = new ArrayList<>();

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(PROBLEMSET_API))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonNode root = mapper.readTree(resp.body());
                if ("OK".equalsIgnoreCase(root.path("status").asText())) {
                    JsonNode problemsNode = root.path("result").path("problems");
                    if (problemsNode.isArray()) {
                        int added = 0;
                        for (JsonNode prob : problemsNode) {
                            if (added >= targetCount) break;

                            int rating = prob.path("rating").asInt(1000);
                            if (rating < effectiveMin || rating > effectiveMax) {
                                continue;
                            }

                            int contestId = prob.path("contestId").asInt(0);
                            String index = prob.path("index").asText("A");
                            String name = prob.path("name").asText("Codeforces Problem");

                            if (contestId <= 0 || index.isBlank()) continue;

                            String problemId = "CF-" + contestId + index;
                            int points = Math.max(100, (rating / 10));
                            Challenge.Difficulty difficulty = rating <= 1000
                                    ? Challenge.Difficulty.EASY
                                    : (rating <= 1500 ? Challenge.Difficulty.MEDIUM : Challenge.Difficulty.HARD);

                            // Setup testcase directory
                            Path testcaseDir = Path.of("contest_data", "testcases", problemId);


                            CPProblem cpProblem = new CPProblem(
                                    problemId,
                                    name + " (" + rating + ")",
                                    points,
                                    difficulty,
                                    2000L,
                                    256,
                                    testcaseDir);
                            cpProblem.setDescription("Codeforces Problem " + contestId + index + " — Rating: " + rating + ". External practice reference. Read the full statement and submit on Codeforces; official hidden tests are not imported.");

                            engine.addChallenge(cpProblem);

                            Map<String, Object> info = new LinkedHashMap<>();
                            info.put("id", problemId);
                            info.put("title", name);
                            info.put("rating", rating);
                            info.put("points", points);
                            info.put("difficulty", difficulty.name());
                            syncedProblems.add(info);
                            added++;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("[CodeforcesSyncService] API fetch warning: " + ex.getMessage());
        }

        System.out.println("[CodeforcesSyncService] Synchronized " + syncedProblems.size() + " CP problems into MongoDB.");
        return new SyncResult(syncedProblems.size(), syncedProblems);
    }

    public record SyncResult(int syncedCount, List<Map<String, Object>> problems) {}
}
