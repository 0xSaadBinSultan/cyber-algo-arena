package com.cyberalgo;

import com.mongodb.client.model.Filters;
import com.mongodb.client.ClientSession;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.TransactionOptions;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import org.bson.conversions.Bson;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;


/** MongoDB is the sole source of truth. Failed writes are never acknowledged as saved. */
public final class MongoRepository {
    private final MongoManager mongoManager;
    private final ThreadLocal<ClientSession> session = new ThreadLocal<>();
    private boolean initialized;

    /** Atomically persist a solve and all affected scores on replica sets (required in production). */
    <T> T atomic(Supplier<T> operation) {
        requireDatabase();
        if (!mongoManager.supportsTransactions()) return operation.get(); // standalone local development only
        try (ClientSession active = mongoManager.startSession()) {
            session.set(active);
            // Bounded explicit retry; never replay remote judging or retry for minutes.
            for (int attempt = 0; attempt < 3; attempt++) {
                active.startTransaction(TransactionOptions.builder().readConcern(ReadConcern.SNAPSHOT)
                        .writeConcern(WriteConcern.MAJORITY).maxCommitTime(3L, TimeUnit.SECONDS).build());
                try {
                    T result = operation.get();
                    active.commitTransaction();
                    return result;
                } catch (RuntimeException failure) {
                    if (active.hasActiveTransaction()) {
                        try { active.abortTransaction(); } catch (Exception ignored) { }
                    }
                    // A competing solve is retried against fresh persisted state. Errors remain sanitized.
                    if (!(failure instanceof DatabaseUnavailableException) || attempt == 2) throw failure;
                }
            }
            throw new DatabaseUnavailableException();
        } catch (DuplicateSubmissionException failure) {
            throw failure;
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        } finally {
            session.remove();
        }
    }

    private void replace(MongoCollection<Document> collection, Bson filter, Document doc, ReplaceOptions options) {
        if (session.get() == null) collection.replaceOne(filter, doc, options);
        else collection.replaceOne(session.get(), filter, doc, options);
    }

    private FindIterable<Document> find(MongoCollection<Document> collection, Bson filter) {
        return session.get() == null ? collection.find(filter) : collection.find(session.get(), filter);
    }

    private FindIterable<Document> find(MongoCollection<Document> collection) {
        return find(collection, new Document());
    }

    public MongoRepository(MongoManager mongoManager) {
        this(mongoManager, true);
    }

    MongoRepository(MongoManager mongoManager, boolean initialize) {
        this.mongoManager = Objects.requireNonNull(mongoManager, "mongoManager must not be null");
        if (initialize) initialize();
    }

    public synchronized void initialize() {
        if (initialized) return;
        requireDatabase();
        bootstrapAdminAccount();
        seedDefaultChallengesIfEmpty();
        migrateLegacyTestCases();
        initialized = true;
    }

    // ═══════════════════════════════════════════════════════════
    // USERS
    // ═══════════════════════════════════════════════════════════

    public void saveUser(User user) {
        try {
            requireDatabase();
            Document doc = userToDoc(user);
            replace(mongoManager.getUsersCollection(),
                    Filters.eq("id", user.getId()),
                    doc,
                    new ReplaceOptions().upsert(true));
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public Optional<User> getUserById(String id) {
        try {
            requireDatabase();
            Document doc = find(mongoManager.getUsersCollection(), Filters.eq("id", id)).first();
            if (doc != null) return Optional.of(docToUser(doc));
            return Optional.empty();
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public Optional<User> getUserByUsername(String username) {
        try {
            requireDatabase();
            Document doc = find(mongoManager.getUsersCollection(), Filters.eq("username", username)).first();
            if (doc != null) return Optional.of(docToUser(doc));
            return Optional.empty();
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public List<User> getAllUsers() {
        try {
            requireDatabase();
            List<User> list = new ArrayList<>();
            for (Document doc : find(mongoManager.getUsersCollection())) {
                list.add(docToUser(doc));
            }
            return list;
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    @SuppressWarnings("unchecked")
    private Document userToDoc(User u) {
        return new Document("id", u.getId())
                .append("username", u.getUsername())
                .append("email", u.getEmail())
                .append("passwordHash", u.getPasswordHash())
                .append("role", u.getRole().name())
                .append("teamId", u.getTeamId())
                .append("createdAt", u.getCreatedAt().toString())
                .append("personalScore", u.getPersonalScore())
                .append("solvesCount", u.getSolvesCount())
                .append("ctfScore", u.getCtfScore())
                .append("ctfSolvesCount", u.getCtfSolvesCount())
                .append("cpScore", u.getCpScore())
                .append("cpSolvesCount", u.getCpSolvesCount())
                .append("categoryBreakdown", new Document((Map<String, Object>) (Map<?, ?>) u.getCategoryBreakdown()))
                .append("solvedChallengeIds", new ArrayList<>(u.getSolvedChallengeIds()));
    }

    private User docToUser(Document doc) {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        Document catDoc = doc.get("categoryBreakdown", Document.class);
        if (catDoc != null) {
            for (String key : catDoc.keySet()) {
                breakdown.put(key, catDoc.getInteger(key, 0));
            }
        }
        List<String> solves = doc.getList("solvedChallengeIds", String.class, List.of());
        Instant createdAt = parseInstant(doc.getString("createdAt"));

        return new User(
                doc.getString("id"),
                doc.getString("username"),
                doc.getString("email"),
                doc.getString("passwordHash"),
                User.Role.fromToken(doc.getString("role")),
                doc.getString("teamId"),
                createdAt,
                doc.getInteger("personalScore", 0),
                doc.getInteger("solvesCount", 0),
                doc.getInteger("ctfScore", 0),
                doc.getInteger("ctfSolvesCount", 0),
                doc.getInteger("cpScore", 0),
                doc.getInteger("cpSolvesCount", 0),
                breakdown,
                solves);
    }

    // ═══════════════════════════════════════════════════════════
    // TEAMS
    // ═══════════════════════════════════════════════════════════

    public void saveTeam(Team team) {
        try {
            requireDatabase();
            Document doc = teamToDoc(team);
            replace(mongoManager.getTeamsCollection(),
                    Filters.eq("id", team.getId()),
                    doc,
                    new ReplaceOptions().upsert(true));
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public Optional<Team> getTeamById(String id) {
        try {
            requireDatabase();
            Document doc = find(mongoManager.getTeamsCollection(), Filters.eq("id", id)).first();
            if (doc != null) return Optional.of(docToTeam(doc));
            return Optional.empty();
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public Optional<Team> getTeamByName(String name) {
        try {
            requireDatabase();
            Document doc = find(mongoManager.getTeamsCollection(), Filters.eq("teamName", name)).first();
            if (doc != null) return Optional.of(docToTeam(doc));
            return Optional.empty();
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public List<Team> getAllTeams() {
        try {
            requireDatabase();
            List<Team> list = new ArrayList<>();
            for (Document doc : find(mongoManager.getTeamsCollection())) {
                list.add(docToTeam(doc));
            }
            return list;
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    private Document teamToDoc(Team t) {
        return new Document("id", t.getId())
                .append("teamName", t.getTeamName())
                .append("teamPasswordHash", t.getTeamPasswordHash())
                .append("captainUserId", t.getCaptainUserId())
                .append("memberUserIds", new ArrayList<>(t.getMemberUserIds()))
                .append("totalScore", t.getTotalScore())
                .append("lastSolveTime", t.getLastSolveTime() != null ? t.getLastSolveTime().toString() : null)
                .append("createdAt", t.getCreatedAt().toString());
    }

    private Team docToTeam(Document doc) {
        List<String> members = doc.getList("memberUserIds", String.class, List.of());
        Instant lastSolve = parseInstant(doc.getString("lastSolveTime"));
        Instant createdAt = parseInstant(doc.getString("createdAt"));

        return new Team(
                doc.getString("id"),
                doc.getString("teamName"),
                doc.getString("teamPasswordHash"),
                doc.getString("captainUserId"),
                members,
                doc.getInteger("totalScore", 0),
                lastSolve,
                createdAt);
    }

    // ═══════════════════════════════════════════════════════════
    // CHALLENGES
    // ═══════════════════════════════════════════════════════════

    public void saveChallenge(Challenge challenge) {
        try {
            requireDatabase();
            Document doc = challengeToDoc(challenge);
            replace(mongoManager.getChallengesCollection(),
                    Filters.eq("id", challenge.getId()),
                    doc,
                    new ReplaceOptions().upsert(true));
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public Optional<Challenge> getChallengeById(String id) {
        try {
            requireDatabase();
            Document doc = find(mongoManager.getChallengesCollection(), Filters.eq("id", id)).first();
            if (doc != null) return Optional.of(docToChallenge(doc));
            return Optional.empty();
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public List<Challenge> getAllChallenges() {
        try {
            requireDatabase();
            List<Challenge> list = new ArrayList<>();
            for (Document doc : find(mongoManager.getChallengesCollection())) {
                list.add(docToChallenge(doc));
            }
            return list;
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public boolean deleteChallenge(String id) {
        try {
            requireDatabase();
            return mongoManager.getChallengesCollection().deleteOne(Filters.eq("id", id)).getDeletedCount() > 0;
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    private Document challengeToDoc(Challenge c) {
        Document doc = new Document("id", c.getId())
                .append("type", c.getType())
                .append("title", c.getTitle())
                .append("basePoints", c.getBasePoints())
                .append("difficulty", c.getDifficulty().name())
                .append("hintCost", c.getHintCost())
                .append("description", c.getDescription())
                .append("solveCount", c.getSolveCount())
                .append("decayLimit", c.getDecayLimit())
                .append("minimumPoints", c.getMinimumPoints())
                .append("firstBloodTeamId", c.getFirstBloodTeamId())
                .append("firstBloodUserId", c.getFirstBloodUserId());

        if (c instanceof CTFChallenge ctf) {
            doc.append("category", ctf.getCategoryName())
               .append("flagHash", ctf.getFlagHash())
               .append("attachmentFileName", ctf.getAttachmentFileName());
        } else if (c instanceof CPProblem cp) {
            List<Document> cases = new ArrayList<>();
            for (CPTestCase tc : cp.getJudgeTestCases()) {
                cases.add(new Document("input", tc.input())
                        .append("expectedOutput", tc.expectedOutput())
                        .append("hidden", tc.hidden()));
            }
            doc.append("timeLimitMs", cp.getTimeLimitMillis())
               .append("memoryLimitMb", cp.getMemoryLimitMb())
               .append("testcaseDir", cp.getTestcaseDirectory().toString())
               .append("testCases", cases);
        }
        return doc;
    }

    private Challenge docToChallenge(Document doc) {
        String type = doc.getString("type");
        String id = doc.getString("id");
        String title = doc.getString("title");
        int basePoints = doc.getInteger("basePoints", 100);
        Challenge.Difficulty difficulty = Challenge.Difficulty.fromToken(doc.getString("difficulty"));

        if ("CTF".equalsIgnoreCase(type)) {
            CTFChallenge ctf = new CTFChallenge(
                    id,
                    title,
                    basePoints,
                    difficulty,
                    doc.getString("category"),
                    doc.getString("flagHash"),
                    doc.getInteger("hintCost", 0),
                    doc.getString("attachmentFileName"));
            ctf.setDescription(doc.getString("description"));
            ctf.setSolveCount(doc.getInteger("solveCount", 0));
            ctf.setDecayLimit(doc.getInteger("decayLimit", 100));
            ctf.setMinimumPoints(doc.getInteger("minimumPoints", 50));
            ctf.setFirstBlood(doc.getString("firstBloodTeamId"), doc.getString("firstBloodUserId"));
            return ctf;
        } else {
            List<CPTestCase> testCases = new ArrayList<>();
            List<Document> storedCases = doc.getList("testCases", Document.class, List.of());
            for (Document tc : storedCases) {
                testCases.add(new CPTestCase(
                        tc.getString("input"),
                        tc.getString("expectedOutput"),
                        tc.getBoolean("hidden", true)));
            }
            CPProblem cp = new CPProblem(
                    id,
                    title,
                    basePoints,
                    difficulty,
                    doc.get("timeLimitMs") instanceof Number n ? n.longValue() : 1000L,
                    doc.getInteger("memoryLimitMb", 256),
                    Path.of(doc.getString("testcaseDir") != null ? doc.getString("testcaseDir") : "contest_data/testcases/" + id),
                    testCases);
            cp.setDescription(doc.getString("description"));
            cp.setSolveCount(doc.getInteger("solveCount", 0));
            cp.setDecayLimit(doc.getInteger("decayLimit", 100));
            cp.setMinimumPoints(doc.getInteger("minimumPoints", 50));
            cp.setFirstBlood(doc.getString("firstBloodTeamId"), doc.getString("firstBloodUserId"));
            return cp;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CONTESTS & PARTICIPATION
    // ═══════════════════════════════════════════════════════════

    public void saveContest(Contest contest) {
        try {
            requireDatabase();
            Document doc = new Document("id", contest.getId())
                    .append("title", contest.getTitle())
                    .append("description", contest.getDescription())
                    .append("startTime", contest.getStartTime().toString())
                    .append("endTime", contest.getEndTime().toString())
                    .append("isRunning", contest.isRunning())
                    .append("scoreboardFrozen", contest.isScoreboardFrozen())
                    .append("freezeTimestamp", contest.getFreezeTimestamp())
                    .append("registeredTeamIds", new ArrayList<>(contest.getRegisteredTeamIds()));

            replace(mongoManager.getContestsCollection(),
                    Filters.eq("id", contest.getId()),
                    doc,
                    new ReplaceOptions().upsert(true));
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public Optional<Contest> getContestById(String id) {
        try {
            requireDatabase();
            Document doc = find(mongoManager.getContestsCollection(), Filters.eq("id", id)).first();
            if (doc != null) {
                Contest c = new Contest(
                        doc.getString("id"),
                        doc.getString("title"),
                        doc.getString("description"),
                        parseInstant(doc.getString("startTime")),
                        parseInstant(doc.getString("endTime")),
                        doc.getBoolean("isRunning", true),
                        doc.getList("registeredTeamIds", String.class, List.of()));
                c.toggleFreeze(doc.getBoolean("scoreboardFrozen", false));
                c.setFreezeTimestamp(doc.getLong("freezeTimestamp") != null ? doc.getLong("freezeTimestamp") : 0L);
                return Optional.of(c);
            }
            return Optional.empty();
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public List<Contest> getAllContests() {
        try {
            requireDatabase();
            List<Contest> list = new ArrayList<>();
            for (Document doc : find(mongoManager.getContestsCollection())) {
                Contest c = new Contest(
                        doc.getString("id"),
                        doc.getString("title"),
                        doc.getString("description"),
                        parseInstant(doc.getString("startTime")),
                        parseInstant(doc.getString("endTime")),
                        doc.getBoolean("isRunning", true),
                        doc.getList("registeredTeamIds", String.class, List.of()));
                c.toggleFreeze(doc.getBoolean("scoreboardFrozen", false));
                c.setFreezeTimestamp(doc.getLong("freezeTimestamp") != null ? doc.getLong("freezeTimestamp") : 0L);
                list.add(c);
            }
            return list;
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public void recordParticipation(ContestParticipation participation) {
        try {
            requireDatabase();
            Document doc = new Document("contestId", participation.getContestId())
                    .append("teamId", participation.getTeamId())
                    .append("userId", participation.getUserId())
                    .append("joinedAt", participation.getJoinedAt().toString());

            replace(mongoManager.getParticipationsCollection(),
                    Filters.and(
                            Filters.eq("contestId", participation.getContestId()),
                            Filters.eq("userId", participation.getUserId())),
                    doc,
                    new ReplaceOptions().upsert(true));
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public Optional<ContestParticipation> getParticipation(String contestId, String userId) {
        try {
            requireDatabase();
            Document doc = find(mongoManager.getParticipationsCollection(),
                    Filters.and(Filters.eq("contestId", contestId), Filters.eq("userId", userId))).first();
            if (doc != null) {
                return Optional.of(new ContestParticipation(
                        doc.getString("contestId"),
                        doc.getString("teamId"),
                        doc.getString("userId"),
                        parseInstant(doc.getString("joinedAt"))));
            }
            return Optional.empty();
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public List<ContestParticipation> getParticipationsByUser(String userId) {
        try {
            requireDatabase();
            List<ContestParticipation> list = new ArrayList<>();
            for (Document doc : find(mongoManager.getParticipationsCollection(), Filters.eq("userId", userId))) {
                list.add(new ContestParticipation(
                        doc.getString("contestId"),
                        doc.getString("teamId"),
                        doc.getString("userId"),
                        parseInstant(doc.getString("joinedAt"))));
            }
            return list;
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SUBMISSIONS
    // ═══════════════════════════════════════════════════════════

    public void saveSubmission(Submission submission) {
        try {
            requireDatabase();
            Document doc = new Document("id", submission.getId())
                    .append("contestId", submission.getContestId())
                    .append("userId", submission.getUserId())
                    .append("teamId", submission.getTeamId())
                    .append("challengeId", submission.getChallengeId())
                    .append("payload", submission.getPayload())
                    .append("wrongAttempts", submission.getWrongAttempts())
                    .append("hintsUsed", submission.getHintsUsed())
                    .append("timestamp", submission.getTimestamp().toString())
                    .append("status", submission.getStatus().name())
                    .append("pointsAwarded", submission.getPointsAwarded())
                    .append("resultMessage", submission.getResultMessage())
                    .append("evaluatedAt", submission.getEvaluatedAt().toString());

            if (session.get() == null) mongoManager.getSubmissionsCollection().insertOne(doc);
            else mongoManager.getSubmissionsCollection().insertOne(session.get(), doc);
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public List<Submission> getAllSubmissions() {
        try {
            requireDatabase();
            List<Submission> list = new ArrayList<>();
            for (Document doc : find(mongoManager.getSubmissionsCollection())) {
                list.add(new Submission(
                        doc.getString("id"),
                        doc.getString("contestId"),
                        doc.getString("userId"),
                        doc.getString("teamId"),
                        doc.getString("challengeId"),
                        doc.getString("payload"),
                        doc.getInteger("wrongAttempts", 0),
                        doc.getInteger("hintsUsed", 0),
                        parseInstant(doc.getString("timestamp")),
                        SubmissionResult.Status.valueOf(doc.getString("status")),
                        doc.getInteger("pointsAwarded", 0),
                        doc.getString("resultMessage"),
                        parseInstant(doc.getString("evaluatedAt"))));
            }
            return list;
        } catch (Exception ignored) {
            throw new DatabaseUnavailableException();
        }
    }

    public boolean isDatabaseReady() {
        return mongoManager.ping();
    }

    // ═══════════════════════════════════════════════════════════
    // SEEDING
    // ═══════════════════════════════════════════════════════════

    private void bootstrapAdminAccount() {
        Optional<SecurityConfig.AdminBootstrap> configured = SecurityConfig.adminBootstrap();
        if (configured.isEmpty()) return;
        SecurityConfig.AdminBootstrap config = configured.get();
        Optional<User> existing = getUserByUsername(config.username());
        if (existing.isPresent()) {
            if (!existing.get().isAdmin()) throw new IllegalStateException("Administrator username is already registered.");
            return;
        }
        saveUser(new User("USER-ADMIN", config.username(), null,
                User.hashPassword(config.password()), User.Role.ADMIN, null));
        System.out.println("[MongoRepository] Bootstrapped administrator account.");
    }

    private void requireDatabase() {
        if (!mongoManager.isConnected() && !mongoManager.ping()) throw new DatabaseUnavailableException();
    }

    /** Persist legacy disk cases once, so subsequent containers do not need those files. */
    public void migrateLegacyTestCases() {
        for (Challenge challenge : getAllChallenges()) {
            if (challenge instanceof CPProblem cp && cp.getConfiguredTestCases().isEmpty()
                    && cp.hasJudgeTestCases()) saveChallenge(cp);
        }
    }

    private void seedDefaultChallengesIfEmpty() {
        if (getAllChallenges().isEmpty()) {
            CTFChallenge ctf1 = new CTFChallenge(
                    "CTF-01",
                    "Base64 Mystery",
                    100,
                    Challenge.Difficulty.EASY,
                    "CRYPTO",
                    "5e19ee72564b8c4bfb2209c349ab4099958e710b2ba18ff0caa4c1a9cbcc2508",
                    20,
                    "mystery.txt");
            CTFChallenge ctf2 = new CTFChallenge(
                    "CTF-02",
                    "Buffer Overflow Intro",
                    300,
                    Challenge.Difficulty.HARD,
                    "PWN",
                    "7e128ef8f5d457d54e1859fac34837e8548bfd0b344de0321ddfb05d24bd3479",
                    50,
                    "vuln_binary.elf");
            CPProblem cp1 = new CPProblem(
                    "CP-01",
                    "Array Inversion Count",
                    200,
                    Challenge.Difficulty.MEDIUM,
                    1000L,
                    256,
                    Path.of("contest_data/testcases/CP-01"));

            saveChallenge(ctf1);
            saveChallenge(ctf2);
            saveChallenge(cp1);
            System.out.println("[MongoRepository] Initialized CTF & CP challenge suite.");
        }
    }

    private static Instant parseInstant(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
