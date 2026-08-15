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

        // Try connecting across candidates with retry loops for container boot synchronization
        int maxRetries = 8;
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
                    System.out.println("[MongoManager] Connected to persistent MongoDB: " + candidate + " (DB: " + effectiveDbName + ")");
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
            System.err.println("[MongoManager] Warning: No active MongoDB server reached on candidate endpoints: " + candidateUris);
            System.err.println("[MongoManager] Operating in resilient in-memory fallback mode.");
            this.client = null;
            this.database = null;
            this.connected = false;
            this.activeUri = null;
        }
    }

    private static List<String> buildCandidateUris(String explicitUri) {
        List<String> list = new ArrayList<>();
        if (explicitUri != null && !explicitUri.isBlank()) {
            list.add(explicitUri.trim());
        }
        String envUri = System.getenv("MONGODB_URI");
        if (envUri != null && !envUri.isBlank() && !list.contains(envUri.trim())) {
            list.add(envUri.trim());
        }

        String[] defaults = {
                "mongodb://mongodb:27017",
                "mongodb://arena-mongodb:27017",
                "mongodb://localhost:27017",
                "mongodb://127.0.0.1:27017",
                "mongodb://172.17.0.1:27017",
                "mongodb://host.docker.internal:27017"
        };
        for (String d : defaults) {
            if (!list.contains(d)) {
                list.add(d);
            }
        }
        return list;
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

            getSubmissionsCollection().createIndex(
                    Indexes.compoundIndex(
                            Indexes.ascending("contestId"),
                            Indexes.ascending("challengeId"),
                            Indexes.ascending("teamId")));
        } catch (Exception ex) {
            System.err.println("[MongoManager] Index initialization warning: " + ex.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
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
