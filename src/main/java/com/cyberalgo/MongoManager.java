package com.cyberalgo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MongoDB connection and lifecycle manager for Cyber-Algo Arena.
 * Features persistent startup retries, automatic container/host discovery,
 * and reliable schema index initialization.
 */
public final class MongoManager implements AutoCloseable {

    public static final String DEFAULT_URI = "mongodb://localhost:27017";
    public static final String DEFAULT_DB_NAME = "cyber_algo_arena";

    private final MongoClient client;
    private final MongoDatabase database;
    private final boolean connected;
    private final String activeUri;

    public MongoManager(String uri, String dbName) {
        String effectiveDbName = (dbName != null && !dbName.isBlank()) ? dbName : DEFAULT_DB_NAME;
        List<String> candidateUris = buildCandidateUris(uri);

        MongoClient selectedClient = null;
        MongoDatabase selectedDb = null;
        boolean isConnected = false;
        String establishedUri = null;

        // Keep startup bounded. Explicit remote configuration gets a few retries;
        // local discovery gets only a short window so cloud cold starts are not delayed.
        int maxRetries = candidateUris.size() == 1 ? 3 : 2;
        for (int attempt = 1; attempt <= maxRetries && !isConnected; attempt++) {
            for (String candidate : candidateUris) {
                try {
                    MongoClientSettings settings = MongoClientSettings.builder()
                            .applyConnectionString(new ConnectionString(candidate))
                            .applyToClusterSettings(builder ->
                                    builder.serverSelectionTimeout(2500, TimeUnit.MILLISECONDS))
                            .applyToSocketSettings(builder ->
                                    builder.connectTimeout(2500, TimeUnit.MILLISECONDS)
                                           .readTimeout(5000, TimeUnit.MILLISECONDS))
                            .build();

                    MongoClient testClient = MongoClients.create(settings);
                    MongoDatabase testDb = testClient.getDatabase(effectiveDbName);
                    testDb.runCommand(new Document("ping", 1));

                    // Ping successful!
                    selectedClient = testClient;
                    selectedDb = testDb;
                    isConnected = true;
                    establishedUri = candidate;
                    System.out.println("[MongoManager] Connected to persistent MongoDB (DB: " + effectiveDbName + ")");
                    break;
                } catch (Exception ignored) {
                    // Try next candidate
                }
            }

            if (!isConnected && attempt < maxRetries) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {}
            }
        }

        if (isConnected) {
            this.client = selectedClient;
            this.database = selectedDb;
            this.connected = true;
            this.activeUri = establishedUri;
            initIndexes();
        } else {
            System.err.println("[MongoManager] Warning: No active MongoDB server reached across " + candidateUris.size() + " configured/discovered endpoint(s).");
            System.err.println("[MongoManager] Operating in resilient in-memory fallback mode.");
            this.client = null;
            this.database = null;
            this.connected = false;
            this.activeUri = null;
        }
    }

    private static List<String> buildCandidateUris(String explicitUri) {
        List<String> configured = new ArrayList<>();
        String[] envNames = {"MONGODB_URI", "MONGO_URI", "MONGO_URL", "MONGODB_URL"};
        for (String envName : envNames) {
            String value = System.getenv(envName);
            if (value != null && !value.isBlank() && !configured.contains(value.trim())) {
                configured.add(value.trim());
            }
        }

        // In cloud hosting, an environment variable is authoritative. Never waste
        // startup time probing Docker/localhost endpoints after it fails.
        if (!configured.isEmpty()) {
            return configured;
        }

        if (explicitUri != null && !explicitUri.isBlank()
                && !DEFAULT_URI.equals(explicitUri.trim())) {
            return List.of(explicitUri.trim());
        }

        // Local/container development discovery only.
        List<String> local = new ArrayList<>();
        local.add(DEFAULT_URI);
        local.add("mongodb://mongodb:27017");
        return local;
    }

    private void initIndexes() {
        if (!connected || database == null) return;
        try {
            getUsersCollection().createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
            getUsersCollection().createIndex(Indexes.ascending("email"));

            getTeamsCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));
            getTeamsCollection().createIndex(Indexes.ascending("teamName"), new IndexOptions().unique(true));

            getChallengesCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));
            getContestsCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));

            getParticipationsCollection().createIndex(
                    Indexes.compoundIndex(Indexes.ascending("contestId"), Indexes.ascending("userId")),
                    new IndexOptions().unique(true));

            try {
                getSubmissionsCollection().dropIndex("contestId_1_challengeId_1_teamId_1");
            } catch (Exception ignored) {
                // Index did not exist or was already migrated.
            }
            getSubmissionsCollection().createIndex(
                    Indexes.ascending("id"), new IndexOptions().unique(true));
            getSubmissionsCollection().createIndex(
                    Indexes.compoundIndex(
                            Indexes.ascending("contestId"),
                            Indexes.ascending("challengeId"),
                            Indexes.ascending("userId")));
            getSubmissionsCollection().createIndex(Indexes.ascending("teamId"));
        } catch (Exception ex) {
            System.err.println("[MongoManager] Index initialization warning: " + ex.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean ping() {
        if (!connected || database == null) {
            return false;
        }
        try {
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public String getActiveUri() {
        return activeUri;
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getUsersCollection() {
        return database != null ? database.getCollection("users") : null;
    }

    public MongoCollection<Document> getTeamsCollection() {
        return database != null ? database.getCollection("teams") : null;
    }

    public MongoCollection<Document> getChallengesCollection() {
        return database != null ? database.getCollection("challenges") : null;
    }

    public MongoCollection<Document> getContestsCollection() {
        return database != null ? database.getCollection("contests") : null;
    }

    public MongoCollection<Document> getParticipationsCollection() {
        return database != null ? database.getCollection("contest_participations") : null;
    }

    public MongoCollection<Document> getSubmissionsCollection() {
        return database != null ? database.getCollection("submissions") : null;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
            System.out.println("[MongoManager] MongoDB client closed.");
        }
    }
}
