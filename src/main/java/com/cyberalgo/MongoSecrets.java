package com.cyberalgo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoCredential;
import java.util.Map;

/** Defense in depth for outbound AI requests; environment settings are never prompt context. */
final class MongoSecrets {
    private MongoSecrets() { }

    static String redact(String text, Map<String, String> env) {
        String safe = text == null ? "" : text;
        String uri = env.get("MONGODB_URI");
        if (uri != null && !uri.isBlank()) {
            safe = safe.replace(uri, "[REDACTED]");
            try {
                MongoCredential credentials = new ConnectionString(uri).getCredential();
                if (credentials != null) {
                    if (!credentials.getUserName().isEmpty()) safe = safe.replace(credentials.getUserName(), "[REDACTED]");
                    char[] password = credentials.getPassword();
                    if (password != null && password.length > 0) safe = safe.replace(new String(password), "[REDACTED]");
                }
            } catch (Exception ignored) { }
        }
        return safe.replaceAll("(?i)mongodb(?:\\+srv)?://[^\\s<>\\\"']+", "[REDACTED]");
    }
}
