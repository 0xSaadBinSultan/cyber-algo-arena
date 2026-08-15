import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Aggregator service for upcoming competitive programming and CTF tournaments.
 * Fetches and caches feeds from CTFtime and Codeforces with a 10-minute TTL.
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
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
    }

    public synchronized List<Map<String, Object>> getUpcomingEvents() {
        long now = System.currentTimeMillis();
        if (!cachedEvents.isEmpty() && (now - lastFetchTimestamp) < CACHE_TTL_MILLIS) {
            return cachedEvents;
        }

        List<Map<String, Object>> merged = new ArrayList<>();

        // Fetch CTFtime
        try {
            merged.addAll(fetchCtftimeEvents());
        } catch (Exception ex) {
            System.err.println("[ContestRadar] CTFtime fetch warning: " + ex.getMessage());
        }

        // Fetch Codeforces
        try {
            merged.addAll(fetchCodeforcesEvents());
        } catch (Exception ex) {
            System.err.println("[ContestRadar] Codeforces fetch warning: " + ex.getMessage());
        }

        // Sort chronologically by startTime
        merged.sort((a, b) -> {
            String t1 = (String) a.getOrDefault("startTime", "");
            String t2 = (String) b.getOrDefault("startTime", "");
            return t1.compareTo(t2);
        });

        if (!merged.isEmpty()) {
            this.cachedEvents = Collections.unmodifiableList(merged);
            this.lastFetchTimestamp = now;
        }

        return cachedEvents;
    }

    private List<Map<String, Object>> fetchCtftimeEvents() throws Exception {
        long startSeconds = System.currentTimeMillis() / 1000L;
        String url = "https://ctftime.org/api/v1/events/?limit=5&start=" + startSeconds;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(5))
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
                .timeout(Duration.ofSeconds(5))
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
}
