package com.cyberalgo;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MongoManagerConfigurationTest {
    @Test
    void productionEnvironmentUriIsTheOnlyAuthority() {
        String configured = "mongodb://db.example.invalid:27017";
        assertEquals(configured, MongoManager.resolveUri(MongoManager.DEFAULT_URI, Map.of(
                "APP_ENV", "production", "MONGODB_URI", configured,
                "MONGO_URI", "mongodb://other.example.invalid:27017",
                "MONGO_URL", MongoManager.DEFAULT_URI)));
    }

    @Test
    void explicitLocalUriIsNotExpandedIntoDockerCandidates() {
        assertEquals(MongoManager.DEFAULT_URI, MongoManager.resolveUri(MongoManager.DEFAULT_URI, Map.of()));
    }

    @Test
    void localhostDefaultIsOnlyAllowedOutsideProduction() {
        assertEquals(MongoManager.DEFAULT_URI, MongoManager.resolveUri(null, Map.of()));
        for (Map<String, String> env : java.util.List.of(
                Map.of("APP_ENV", "production"), Map.of("RENDER", "true"),
                Map.of("RENDER_SERVICE_ID", "service"))) {
            assertThrows(DatabaseUnavailableException.class, () -> MongoManager.resolveUri(null, env));
        }
        for (String host : java.util.List.of("localhost", "127.0.0.1", "[::1]", "0.0.0.0")) {
            assertThrows(DatabaseUnavailableException.class, () -> MongoManager.resolveUri(null,
                    Map.of("APP_ENV", "production", "MONGODB_URI", "mongodb://" + host + ":27017")));
        }
    }

    @Test
    void blankExplicitUriFailsInsteadOfUsingLocalhost() {
        assertThrows(DatabaseUnavailableException.class, () -> MongoManager.resolveUri(null,
                Map.of("MONGODB_URI", " ")));
    }

    @Test
    void databaseEnvironmentWinsAndOnlyMissingNameDefaults() {
        assertEquals("configured_db", MongoManager.resolveDatabase("ignored", Map.of("MONGODB_DATABASE_NAME", "configured_db")));
        assertEquals("cyber_algo_arena", MongoManager.resolveDatabase(null, Map.of()));
        assertThrows(DatabaseUnavailableException.class,
                () -> MongoManager.resolveDatabase(null, Map.of("MONGODB_DATABASE_NAME", "")));
    }

    @Test
    void failedExplicitUriNeverFallsBackAndLogsDoNotContainCredentials() {
        String username = "sentinel-user";
        String password = "sentinel-password";
        String uri = "mongodb://" + username + ":" + password + "@127.0.0.1:1/?directConnection=true";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream stdout = System.out, stderr = System.err;
        try (PrintStream capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            System.setErr(capture);
            assertTimeout(Duration.ofSeconds(8), () -> {
                try (MongoManager manager = new MongoManager(MongoManager.DEFAULT_URI, "test", Map.of("MONGODB_URI", uri))) {
                    assertFalse(manager.isConnected());
                    assertFalse(manager.ping());
                    MongoRepository repo = new MongoRepository(manager, false);
                    DatabaseUnavailableException failure = assertThrows(DatabaseUnavailableException.class, repo::getAllUsers);
                    assertNull(failure.getCause());
                }
            });
            DatabaseUnavailableException invalid = assertThrows(DatabaseUnavailableException.class,
                    () -> new MongoManager("not-a-uri-" + password, "test", Map.of()));
            invalid.printStackTrace(capture);
        } finally {
            System.setOut(stdout);
            System.setErr(stderr);
        }
        String output = bytes.toString(StandardCharsets.UTF_8);
        assertFalse(output.contains(username));
        assertFalse(output.contains(password));
        assertFalse(output.contains(uri));
        assertFalse(output.contains("fallback mode"));
        assertFalse(output.contains("Connected to persistent MongoDB"));
    }
}
