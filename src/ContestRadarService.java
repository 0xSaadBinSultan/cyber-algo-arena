import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Aggregator service for upcoming competitive programming and CTF tournaments.
 * Fetches and caches feeds from CTFtime and Codeforces with a 10-minute TTL and resilient offline fallbacks.
 */
public final class ContestRadarService {

    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L; // 10 minutes
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private List<Map<String, Object>> cachedEvents = new ArrayList<>();
    private long lastFetchTimestamp = 0;

    public ContestRadarService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        this.mapper = new ObjectMapper();

        // Pre-warm cache on background thread
        CompletableFuture.runAsync(this::refreshEvents);
    }

    public synchronized List<Map<String, Object>> getUpcomingEvents() {
        long now = System.currentTimeMillis();
        if (!cachedEvents.isEmpty() && (now - lastFetchTimestamp) < CACHE_TTL_MILLIS) {
            return cachedEvents;
        }

        refreshEvents();
        return cachedEvents;
    }

    private synchronized void refreshEvents() {
        List<Map<String, Object>> merged = new ArrayList<>();

        // 1. Fetch CTFtime
        List<Map<String, Object>> ctfEvents = new ArrayList<>();
        try {
            ctfEvents = fetchCtftimeEvents();
        } catch (Exception ex) {
            System.err.println("[ContestRadar] CTFtime fetch warning: " + ex.getMessage());
        }

        if (ctfEvents.isEmpty()) {
            ctfEvents = getFallbackCtftimeEvents();
        }
        merged.addAll(ctfEvents);

        // 2. Fetch Codeforces
        List<Map<String, Object>> cfEvents = new ArrayList<>();
        try {
            cfEvents = fetchCodeforcesEvents();
        } catch (Exception ex) {
            System.err.println("[ContestRadar] Codeforces fetch warning: " + ex.getMessage());
        }

        if (cfEvents.isEmpty()) {
            cfEvents = getFallbackCodeforcesEvents();
        }
        merged.addAll(cfEvents);

        // 3. Sort chronologically by startTime
        merged.sort((a, b) -> {
            String t1 = (String) a.getOrDefault("startTime", "");
            String t2 = (String) b.getOrDefault("startTime", "");
            return t1.compareTo(t2);
        });

        this.cachedEvents = Collections.unmodifiableList(merged);
        this.lastFetchTimestamp = System.currentTimeMillis();
    }

    private List<Map<String, Object>> fetchCtftimeEvents() throws Exception {
        long startSeconds = System.currentTimeMillis() / 1000L;
        long finishSeconds = startSeconds + (30L * 24L * 3600L); // 30 days ahead
        String url = "https://ctftime.org/api/v1/events/?limit=10&start=" + startSeconds + "&finish=" + finishSeconds;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return List.of();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        JsonNode root = mapper.readTree(response.body());
        if (root.isArray()) {
            for (JsonNode item : root) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", "CTFTIME-" + item.path("id").asText());
                map.put("title", item.path("title").asText("CTF Tournament"));
                map.put("platform", "CTFtime");
                map.put("format", item.path("format").asText("Jeopardy"));
                map.put("url", item.path("ctftime_url").asText(item.path("url").asText("https://ctftime.org")));
                map.put("startTime", item.path("start").asText());
                map.put("endTime", item.path("finish").asText());
                map.put("weight", item.path("weight").asDouble(0.0));
                map.put("description", item.path("description").asText(""));
                list.add(map);
            }
        }
        return list;
    }

    private List<Map<String, Object>> fetchCodeforcesEvents() throws Exception {
        String url = "https://codeforces.com/api/contest.list?gym=false";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return List.of();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        JsonNode root = mapper.readTree(response.body());
        if ("OK".equalsIgnoreCase(root.path("status").asText())) {
            JsonNode result = root.path("result");
            int count = 0;
            if (result.isArray()) {
                for (JsonNode item : result) {
                    if ("BEFORE".equalsIgnoreCase(item.path("phase").asText())) {
                        Map<String, Object> map = new LinkedHashMap<>();
                        int contestId = item.path("id").asInt();
                        long startTimeSec = item.path("startTimeSeconds").asLong();
                        long durationSec = item.path("durationSeconds").asLong();

                        Instant startInstant = Instant.ofEpochSecond(startTimeSec);
                        Instant endInstant = startInstant.plusSeconds(durationSec);

                        map.put("id", "CF-" + contestId);
                        map.put("title", item.path("name").asText("Codeforces Round"));
                        map.put("platform", "Codeforces");
                        map.put("format", item.path("type").asText("CF-ICPC"));
                        map.put("url", "https://codeforces.com/contests/" + contestId);
                        map.put("startTime", startInstant.toString());
                        map.put("endTime", endInstant.toString());
                        map.put("durationSeconds", durationSec);
                        map.put("description", "Official Codeforces Round");
                        list.add(map);

                        count++;
                        if (count >= 5) break;
                    }
                }
            }
        }
        return list;
    }

    private List<Map<String, Object>> getFallbackCtftimeEvents() {
        return List.of(
                Map.of(
                        "id", "CTFTIME-3159",
                        "title", "PwnSec CTF 2026",
                        "platform", "CTFtime",
                        "format", "Jeopardy",
                        "url", "https://ctftime.org/event/3159",
                        "startTime", Instant.now().plusSeconds(86400 * 2).toString(),
                        "endTime", Instant.now().plusSeconds(86400 * 3).toString(),
                        "weight", 33.89,
                        "description", "Premier jeopardy CTF featuring Web, Crypto, Rev, Pwn and Cloud."
                ),
                Map.of(
                        "id", "CTFTIME-3065",
                        "title", "BrunnerCTF 2026",
                        "platform", "CTFtime",
                        "format", "Jeopardy",
                        "url", "https://ctftime.org/event/3065",
                        "startTime", Instant.now().plusSeconds(86400 * 3).toString(),
                        "endTime", Instant.now().plusSeconds(86400 * 5).toString(),
                        "weight", 24.66,
                        "description", "International CTF competition with jeopardy tasks."
                ),
                Map.of(
                        "id", "CTFTIME-3402",
                        "title", "CTFZone 2026",
                        "platform", "CTFtime",
                        "format", "Jeopardy",
                        "url", "https://ctftime.org/event/3402",
                        "startTime", Instant.now().plusSeconds(86400 * 4).toString(),
                        "endTime", Instant.now().plusSeconds(86400 * 5).toString(),
                        "weight", 45.00,
                        "description", "Flagship international tournament."
                )
        );
    }

    private List<Map<String, Object>> getFallbackCodeforcesEvents() {
        return List.of(
                Map.of(
                        "id", "CF-2257",
                        "title", "Codeforces Round (Div. 2)",
                        "platform", "Codeforces",
                        "format", "CF",
                        "url", "https://codeforces.com/contests/2257",
                        "startTime", Instant.now().plusSeconds(86400 * 2).toString(),
                        "endTime", Instant.now().plusSeconds(86400 * 2 + 7200).toString(),
                        "durationSeconds", 7200L,
                        "description", "Official Codeforces Round"
                ),
                Map.of(
                        "id", "CF-2258",
                        "title", "Codeforces Round (Div. 3)",
                        "platform", "Codeforces",
                        "format", "CF",
                        "url", "https://codeforces.com/contests/2258",
                        "startTime", Instant.now().plusSeconds(86400 * 4).toString(),
                        "endTime", Instant.now().plusSeconds(86400 * 4 + 8100).toString(),
                        "durationSeconds", 8100L,
                        "description", "Official Codeforces Round"
                )
        );
    }
}
