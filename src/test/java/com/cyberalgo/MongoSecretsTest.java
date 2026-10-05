package com.cyberalgo;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MongoSecretsTest {
    @Test
    void outboundAiTextRemovesMongoUrisAndKnownCredentials() {
        String uri = "mongodb://sentinel-user:sentinel-password@db.example.invalid/arena";
        String safe = MongoSecrets.redact("Question " + uri + " sentinel-user sentinel-password", Map.of("MONGODB_URI", uri));
        assertFalse(safe.contains(uri));
        assertFalse(safe.contains("sentinel-user"));
        assertFalse(safe.contains("sentinel-password"));
        assertTrue(safe.startsWith("Question "));
        assertFalse(MongoSecrets.redact("mongodb+srv://someone:secret@example.invalid/db", Map.of()).contains("secret"));
    }
}
