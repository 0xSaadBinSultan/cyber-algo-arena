import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Direct MongoDB Data Access Layer for Cyber-Algo Arena.
 * Maps domain objects to/from MongoDB BSON Documents and executes queries.
 */
public final class MongoRepository {

    private final MongoManager mongoManager;

    public MongoRepository(MongoManager mongoManager) {
        this.mongoManager = Objects.requireNonNull(mongoManager, "mongoManager must not be null");
        seedDefaultAdminIfEmpty();
        seedDefaultChallengesIfEmpty();
    }

    // ═══════════════════════════════════════════════════════════
    // USERS
    // ═══════════════════════════════════════════════════════════

    public void saveUser(User user) {
        Document doc = userToDoc(user);
        mongoManager.getUsersCollection().replaceOne(
                Filters.eq("id", user.getId()),
                doc,
                new ReplaceOptions().upsert(true));
    }

    public Optional<User> getUserById(String id) {
        Document doc = mongoManager.getUsersCollection().find(Filters.eq("id", id)).first();
        return Optional.ofNullable(doc).map(this::docToUser);
    }

    public Optional<User> getUserByUsername(String username) {
        Document doc = mongoManager.getUsersCollection().find(Filters.eq("username", username)).first();
        return Optional.ofNullable(doc).map(this::docToUser);
    }

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        for (Document doc : mongoManager.getUsersCollection().find()) {
            list.add(docToUser(doc));
        }
        return list;
    }

    private Document userToDoc(User u) {
        Document doc = new Document("id", u.getId())
                .append("username", u.getUsername())
                .append("email", u.getEmail())
                .append("passwordHash", u.getPasswordHash())
                .append("role", u.getRole().name())
                .append("teamId", u.getTeamId())
                .append("createdAt", u.getCreatedAt().toString())
                .append("personalScore", u.getPersonalScore())
                .append("solvesCount", u.getSolvesCount())
                .append("categoryBreakdown", new Document((Map) u.getCategoryBreakdown()))
                .append("solvedChallengeIds", new ArrayList<>(u.getSolvedChallengeIds()));
        return doc;
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
                breakdown,
                solves);
    }

    // ═══════════════════════════════════════════════════════════
    // TEAMS
    // ═══════════════════════════════════════════════════════════

    public void saveTeam(Team team) {
        Document doc = teamToDoc(team);
        mongoManager.getTeamsCollection().replaceOne(
                Filters.eq("id", team.getId()),
                doc,
                new ReplaceOptions().upsert(true));
    }

    public Optional<Team> getTeamById(String id) {
        Document doc = mongoManager.getTeamsCollection().find(Filters.eq("id", id)).first();
        return Optional.ofNullable(doc).map(this::docToTeam);
    }

    public Optional<Team> getTeamByName(String name) {
        Document doc = mongoManager.getTeamsCollection().find(Filters.eq("teamName", name)).first();
        return Optional.ofNullable(doc).map(this::docToTeam);
    }

    public List<Team> getAllTeams() {
        List<Team> list = new ArrayList<>();
        for (Document doc : mongoManager.getTeamsCollection().find()) {
            list.add(docToTeam(doc));
        }
        return list;
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
        Document doc = challengeToDoc(challenge);
        mongoManager.getChallengesCollection().replaceOne(
                Filters.eq("id", challenge.getId()),
                doc,
                new ReplaceOptions().upsert(true));
    }

    public Optional<Challenge> getChallengeById(String id) {
        Document doc = mongoManager.getChallengesCollection().find(Filters.eq("id", id)).first();
        return Optional.ofNullable(doc).map(this::docToChallenge);
    }

    public List<Challenge> getAllChallenges() {
        List<Challenge> list = new ArrayList<>();
        for (Document doc : mongoManager.getChallengesCollection().find()) {
            list.add(docToChallenge(doc));
        }
        return list;
    }

    public boolean deleteChallenge(String id) {
        return mongoManager.getChallengesCollection().deleteOne(Filters.eq("id", id)).getDeletedCount() > 0;
    }

    private Document challengeToDoc(Challenge c) {
        Document doc = new Document("id", c.getId())
                .append("type", c.getType())
                .append("title", c.getTitle())
                .append("basePoints", c.getBasePoints())
                .append("difficulty", c.getDifficulty().name())
                .append("hintCost", c.getHintCost());

        if (c instanceof CTFChallenge ctf) {
            doc.append("category", ctf.getCategoryName())
               .append("flagHash", ctf.getFlagHash())
               .append("attachmentFileName", ctf.getAttachmentFileName());
        } else if (c instanceof CPProblem cp) {
            doc.append("timeLimitMs", cp.getTimeLimitMillis())
               .append("memoryLimitMb", cp.getMemoryLimitMb())
               .append("testcaseDir", cp.getTestcaseDirectory().toString());
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
            return new CTFChallenge(
                    id,
                    title,
                    basePoints,
                    difficulty,
                    doc.getString("category"),
                    doc.getString("flagHash"),
                    doc.getInteger("hintCost", 0),
                    doc.getString("attachmentFileName"));
        } else {
            return new CPProblem(
                    id,
                    title,
                    basePoints,
                    difficulty,
                    doc.getLong("timeLimitMs") != null ? doc.getLong("timeLimitMs") : 1000L,
                    doc.getInteger("memoryLimitMb", 256),
                    Path.of(doc.getString("testcaseDir") != null ? doc.getString("testcaseDir") : "contest_data/testcases/" + id));
        }
    }

    // ═══════════════════════════════════════════════════════════
    // CONTESTS & PARTICIPATION
    // ═══════════════════════════════════════════════════════════

    public void saveContest(Contest contest) {
        Document doc = new Document("id", contest.getId())
                .append("title", contest.getTitle())
                .append("description", contest.getDescription())
                .append("startTime", contest.getStartTime().toString())
                .append("endTime", contest.getEndTime().toString())
                .append("isRunning", contest.isRunning())
                .append("registeredTeamIds", new ArrayList<>(contest.getRegisteredTeamIds()));

        mongoManager.getContestsCollection().replaceOne(
                Filters.eq("id", contest.getId()),
                doc,
                new ReplaceOptions().upsert(true));
    }

    public Optional<Contest> getContestById(String id) {
        Document doc = mongoManager.getContestsCollection().find(Filters.eq("id", id)).first();
        if (doc == null) return Optional.empty();

        return Optional.of(new Contest(
                doc.getString("id"),
                doc.getString("title"),
                doc.getString("description"),
                parseInstant(doc.getString("startTime")),
                parseInstant(doc.getString("endTime")),
                doc.getBoolean("isRunning", true),
                doc.getList("registeredTeamIds", String.class, List.of())));
    }

    public List<Contest> getAllContests() {
        List<Contest> list = new ArrayList<>();
        for (Document doc : mongoManager.getContestsCollection().find()) {
            list.add(new Contest(
                    doc.getString("id"),
                    doc.getString("title"),
                    doc.getString("description"),
                    parseInstant(doc.getString("startTime")),
                    parseInstant(doc.getString("endTime")),
                    doc.getBoolean("isRunning", true),
                    doc.getList("registeredTeamIds", String.class, List.of())));
        }
        return list;
    }

    public void recordParticipation(ContestParticipation participation) {
        Document doc = new Document("contestId", participation.getContestId())
                .append("teamId", participation.getTeamId())
                .append("userId", participation.getUserId())
                .append("joinedAt", participation.getJoinedAt().toString());

        mongoManager.getParticipationsCollection().replaceOne(
                Filters.and(
                        Filters.eq("contestId", participation.getContestId()),
                        Filters.eq("userId", participation.getUserId())),
                doc,
                new ReplaceOptions().upsert(true));
    }

    public Optional<ContestParticipation> getParticipation(String contestId, String userId) {
        Document doc = mongoManager.getParticipationsCollection().find(
                Filters.and(Filters.eq("contestId", contestId), Filters.eq("userId", userId))).first();
        if (doc == null) return Optional.empty();

        return Optional.of(new ContestParticipation(
                doc.getString("contestId"),
                doc.getString("teamId"),
                doc.getString("userId"),
                parseInstant(doc.getString("joinedAt"))));
    }

    public List<ContestParticipation> getParticipationsByUser(String userId) {
        List<ContestParticipation> list = new ArrayList<>();
        for (Document doc : mongoManager.getParticipationsCollection().find(Filters.eq("userId", userId))) {
            list.add(new ContestParticipation(
                    doc.getString("contestId"),
                    doc.getString("teamId"),
                    doc.getString("userId"),
                    parseInstant(doc.getString("joinedAt"))));
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════
    // SUBMISSIONS
    // ═══════════════════════════════════════════════════════════

    public void saveSubmission(Submission submission) {
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

        mongoManager.getSubmissionsCollection().replaceOne(
                Filters.eq("id", submission.getId()),
                doc,
                new ReplaceOptions().upsert(true));
    }

    public List<Submission> getAllSubmissions() {
        List<Submission> list = new ArrayList<>();
        for (Document doc : mongoManager.getSubmissionsCollection().find()) {
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
    }

    // ═══════════════════════════════════════════════════════════
    // SEEDING
    // ═══════════════════════════════════════════════════════════

    private void seedDefaultAdminIfEmpty() {
        if (mongoManager.getUsersCollection().countDocuments() == 0) {
            String adminHash = CTFChallenge.sha256Hex("admin123");
            User admin = new User("USER-ADMIN", "admin", "admin@cyberarena.local", adminHash, User.Role.ADMIN, null);
            saveUser(admin);
            System.out.println("[MongoRepository] Seeded default administrator: admin / admin123");
        }
    }

    private void seedDefaultChallengesIfEmpty() {
        if (mongoManager.getChallengesCollection().countDocuments() == 0) {
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
            System.out.println("[MongoRepository] Seeded initial CTF & CP challenges.");
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
