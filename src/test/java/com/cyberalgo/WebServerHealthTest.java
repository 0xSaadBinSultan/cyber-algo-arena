package com.cyberalgo;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebServerHealthTest {
    @Test
    void readinessReflectsPingAndLivenessSurvivesOutage() throws Exception {
        MongoManager manager = mock(MongoManager.class);
        MongoRepository repo = mock(MongoRepository.class);
        when(repo.isDatabaseReady()).thenAnswer(invocation -> manager.ping());
        ContestEngine engine = new ContestEngine(repo);
        try (WebServer server = new WebServer(engine, 0)) {
            when(manager.ping()).thenReturn(false);
            assertEquals(200, get(server, "/api/health/live").statusCode());
            assertEquals(503, get(server, "/api/health/ready").statusCode());
            assertTrue(get(server, "/api/health").body().contains("DOWN"));
            assertEquals(503, get(server, "/api/challenges").statusCode());
            when(manager.ping()).thenReturn(true);
            assertEquals(200, get(server, "/api/health/ready").statusCode());
            assertTrue(get(server, "/api/health/ready").body().contains("READY"));
            when(manager.ping()).thenReturn(false);
            assertEquals(503, get(server, "/api/health/ready").statusCode());
            assertEquals(200, get(server, "/api/health/live").statusCode());
        }
    }

    static HttpResponse<String> get(WebServer server, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
