import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB connection and lifecycle manager for Cyber-Algo Arena.
 * Manages singleton MongoClient, database access, and schema index initialization.
 */
public final class MongoManager implements AutoCloseable {

    public static final String DEFAULT_URI = "mongodb://localhost:27017";
    public static final String DEFAULT_DB_NAME = "cyber_algo_arena";

    private final MongoClient client;
    private final MongoDatabase database;

    public MongoManager(String uri, String dbName) {
        String effectiveUri = (uri != null && !uri.isBlank()) ? uri : DEFAULT_URI;
        String effectiveDbName = (dbName != null && !dbName.isBlank()) ? dbName : DEFAULT_DB_NAME;

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(effectiveUri))
                .applyToClusterSettings(builder ->
                        builder.serverSelectionTimeout(5000, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(builder ->
                        builder.connectTimeout(5000, TimeUnit.MILLISECONDS)
                               .readTimeout(10000, TimeUnit.MILLISECONDS))
                .build();

        this.client = MongoClients.create(settings);
        this.database = client.getDatabase(effectiveDbName);
        
        try {
            // Verify connection
            this.database.runCommand(new Document("ping", 1));
            System.out.println("[MongoManager] Connected to MongoDB: " + effectiveDbName + " (" + effectiveUri + ")");
            initIndexes();
        } catch (Exception ex) {
            System.err.println("[MongoManager] MongoDB ping warning: Could not reach " + effectiveUri + " (" + ex.getMessage() + ")");
        }
    }

    private void initIndexes() {
        try {
            // users: unique on username, index on email
            getUsersCollection().createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
            getUsersCollection().createIndex(Indexes.ascending("email"));

            // teams: unique on id and teamName
            getTeamsCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));
            getTeamsCollection().createIndex(Indexes.ascending("teamName"), new IndexOptions().unique(true));

            // challenges: unique on id
            getChallengesCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));

            // contests: unique on id
            getContestsCollection().createIndex(Indexes.ascending("id"), new IndexOptions().unique(true));

            // contest_participations: unique compound on (contestId, userId)
            // Enforces: 1 player cannot join multiple teams in the same contest
            getParticipationsCollection().createIndex(
                    Indexes.compoundIndex(Indexes.ascending("contestId"), Indexes.ascending("userId")),
                    new IndexOptions().unique(true));

            // submissions: compound query index
            getSubmissionsCollection().createIndex(
                    Indexes.compoundIndex(
                            Indexes.ascending("contestId"),
                            Indexes.ascending("challengeId"),
                            Indexes.ascending("teamId")));
        } catch (Exception ex) {
            System.err.println("[MongoManager] Index initialization warning: " + ex.getMessage());
        }
    }

    public MongoDatabase getDatabase() {
        return database;
    }

    public MongoCollection<Document> getUsersCollection() {
        return database.getCollection("users");
    }

    public MongoCollection<Document> getTeamsCollection() {
        return database.getCollection("teams");
    }

    public MongoCollection<Document> getChallengesCollection() {
        return database.getCollection("challenges");
    }

    public MongoCollection<Document> getContestsCollection() {
        return database.getCollection("contests");
    }

    public MongoCollection<Document> getParticipationsCollection() {
        return database.getCollection("contest_participations");
    }

    public MongoCollection<Document> getSubmissionsCollection() {
        return database.getCollection("submissions");
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
            System.out.println("[MongoManager] MongoDB client closed.");
        }
    }
}
