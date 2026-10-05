package com.cyberalgo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** One authoritative MongoDB connection; never substitutes another storage backend. */
public final class MongoManager implements AutoCloseable {
    public static final String DEFAULT_URI = "mongodb://localhost:27017";
    public static final String DEFAULT_DB_NAME = "cyber_algo_arena";
    private final MongoClient client;
    private final MongoDatabase database;
    private final boolean production;
    private boolean transactionsSupported;
    private volatile boolean initialized;
    private volatile boolean closed;

    public MongoManager() {
        this(null, null);
    }

    public MongoManager(String uri, String dbName) {
        this(uri, dbName, System.getenv());
    }

    MongoManager(String uri, String dbName, Map<String, String> environment) {
        // Driver diagnostics include connection settings and server exception text.
        // Disable them before any driver logger is created, including DEBUG deployments.
        System.setProperty("org.slf4j.simpleLogger.log.org.mongodb.driver", "off");
        this.production = isProduction(environment);
        MongoClient candidate = null;
        try {
            String selectedUri = resolveUri(uri, environment);
            String selectedDatabase = resolveDatabase(dbName, environment);
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(selectedUri))
                    .timeout(5, TimeUnit.SECONDS)
                    .applyToClusterSettings(b -> b.serverSelectionTimeout(2, TimeUnit.SECONDS))
                    .applyToSocketSettings(b -> b.connectTimeout(2, TimeUnit.SECONDS)
                            .readTimeout(3, TimeUnit.SECONDS))
                    .writeConcern(WriteConcern.MAJORITY.withWTimeout(3, TimeUnit.SECONDS))
                    .retryReads(false).retryWrites(false)
                    .build();
            candidate = MongoClients.create(settings);
            this.client = candidate;
            this.database = client.getDatabase(selectedDatabase);
        } catch (Exception ignored) {
            if (candidate != null) candidate.close();
            // Never retain the original exception: even malformed URI errors can contain secrets.
            throw new DatabaseUnavailableException();
        }
        if (!ping()) {
            System.err.println("[MongoManager] Persistent MongoDB unavailable; database operations are disabled.");
        }
    }

    static String resolveUri(String explicitUri, Map<String, String> env) {
        String uri = env.containsKey("MONGODB_URI") ? env.get("MONGODB_URI") : explicitUri;
        if (uri == null) {
            if (isProduction(env)) throw new DatabaseUnavailableException();
            return DEFAULT_URI;
        }
        if (uri.isBlank()) throw new DatabaseUnavailableException();
        uri = uri.trim();
        try {
            ConnectionString parsed = new ConnectionString(uri);
            if (isProduction(env) && parsed.getHosts().stream().anyMatch(MongoManager::isLoopback)) {
                throw new DatabaseUnavailableException();
            }
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
        return uri;
    }

    static boolean isProduction(Map<String, String> env) {
        return "production".equalsIgnoreCase(env.get("APP_ENV"))
                || "production".equalsIgnoreCase(env.get("ENVIRONMENT"))
                || "true".equalsIgnoreCase(env.get("RENDER"))
                || env.containsKey("RENDER_SERVICE_ID");
    }

    private static boolean isLoopback(String host) {
        String h = host.toLowerCase(java.util.Locale.ROOT);
        return h.equals("localhost") || h.startsWith("localhost:") || h.startsWith("127.")
                || h.startsWith("[::1]") || h.startsWith("0.0.0.0") || h.startsWith("[::]")
                || h.startsWith("localhost.");
    }

    static String resolveDatabase(String explicitName, Map<String, String> env) {
        String name = env.containsKey("MONGODB_DATABASE_NAME") ? env.get("MONGODB_DATABASE_NAME") : explicitName;
        if (name == null) return DEFAULT_DB_NAME;
        if (name.isBlank()) throw new DatabaseUnavailableException();
        return name.trim();
    }

    private void initIndexes() {
        getUsersCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));
        getUsersCollection().createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
        getUsersCollection().createIndex(Indexes.ascending("email"));
        getTeamsCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));
        getTeamsCollection().createIndex(Indexes.ascending("teamName"), new IndexOptions().unique(true));
        getChallengesCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));
        getContestsCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));
        getParticipationsCollection().createIndex(
                Indexes.compoundIndex(Indexes.ascending("contestId"), Indexes.ascending("userId")),
                new IndexOptions().unique(true));

        getSubmissionsCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));

        // Older releases prevented retries with unique user/team + problem indexes.
        // Discover by key, rather than assuming a particular index name.
        for (Document index : getSubmissionsCollection().listIndexes()) {
            Document keys = index.get("key", Document.class);
            if (Boolean.TRUE.equals(index.getBoolean("unique")) && keys.containsKey("challengeId")
                    && (keys.containsKey("userId") || keys.containsKey("teamId"))) {
                getSubmissionsCollection().dropIndex(index.getString("name"));
            }
        }
        getSubmissionsCollection().createIndex(Indexes.compoundIndex(
                Indexes.ascending("contestId"), Indexes.ascending("challengeId"), Indexes.ascending("userId")));
        getSubmissionsCollection().createIndex(Indexes.ascending("teamId"));
    }

    public boolean isConnected() { return initialized && !closed; }

    public synchronized boolean ping() {
        if (closed) return false;
        try {
            database.runCommand(new Document("ping", 1));
            if (!initialized) {
                Document hello = database.runCommand(new Document("hello", 1));
                transactionsSupported = hello.containsKey("setName") || "isdbgrid".equals(hello.getString("msg"));
                if (production && !transactionsSupported) return false;
                initIndexes();
                initialized = true;
                System.out.println("[MongoManager] Connected to persistent MongoDB");
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    boolean supportsTransactions() { return transactionsSupported; }
    ClientSession startSession() { return client.startSession(); }

    public MongoDatabase getDatabase() {
        if (closed) throw new DatabaseUnavailableException();
        return database;
    }

    public MongoCollection<Document> getUsersCollection() { return getDatabase().getCollection("users"); }
    public MongoCollection<Document> getTeamsCollection() { return getDatabase().getCollection("teams"); }
    public MongoCollection<Document> getChallengesCollection() { return getDatabase().getCollection("challenges"); }
    public MongoCollection<Document> getContestsCollection() { return getDatabase().getCollection("contests"); }
    public MongoCollection<Document> getParticipationsCollection() { return getDatabase().getCollection("contest_participations"); }
    public MongoCollection<Document> getSubmissionsCollection() { return getDatabase().getCollection("submissions"); }

    @Override
    public synchronized void close() {
        closed = true;
        client.close();
    }
}
