import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct MongoDB Data Access Layer with resilient in-memory fallback.
 * Maps domain models to BSON Documents when connected, or operates cleanly in memory when offline.
 */
public final class MongoRepository {

    private final MongoManager mongoManager;

    // In-memory cache & offline fallback stores
    private final Map<String, User> memUsers = new ConcurrentHashMap<>();
    private final Map<String, Team> memTeams = new ConcurrentHashMap<>();
    private final Map<String, Challenge> memChallenges = new ConcurrentHashMap<>();
    private final Map<String, Contest> memContests = new ConcurrentHashMap<>();
    private final Map<String, ContestParticipation> memParticipations = new ConcurrentHashMap<>();
    private final List<Submission> memSubmissions = Collections.synchronizedList(new ArrayList<>());

    public MongoRepository(MongoManager mongoManager) {
        this.mongoManager = Objects.requireNonNull(mongoManager, "mongoManager must not be null");
        seedDefaultAdminIfEmpty();
        seedDefaultChallengesIfEmpty();
    }

    // ═══════════════════════════════════════════════════════════
    // USERS
    // ═══════════════════════════════════════════════════════════

    public void saveUser(User user) {
        memUsers.put(user.getId(), user);
        if (mongoManager.isConnected() && mongoManager.getUsersCollection() != null) {
            try {
                Document doc = userToDoc(user);
                mongoManager.getUsersCollection().replaceOne(
                        Filters.eq("id", user.getId()),
                        doc,
                        new ReplaceOptions().upsert(true));
            } catch (Exception ex) {
                System.err.println("[MongoRepository] Write error for user " + user.getId() + ": " + ex.getMessage());
            }
        }
    }

    public Optional<User> getUserById(String id) {
        if (mongoManager.isConnected() && mongoManager.getUsersCollection() != null) {
            try {
                Document doc = mongoManager.getUsersCollection().find(Filters.eq("id", id)).first();
                if (doc != null) return Optional.of(docToUser(doc));
            } catch (Exception ignored) {}
        }
        return Optional.ofNullable(memUsers.get(id));
    }

    public Optional<User> getUserByUsername(String username) {
        if (mongoManager.isConnected() && mongoManager.getUsersCollection() != null) {
            try {
                Document doc = mongoManager.getUsersCollection().find(Filters.eq("username", username)).first();
                if (doc != null) return Optional.of(docToUser(doc));
            } catch (Exception ignored) {}
        }
        for (User u : memUsers.values()) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                return Optional.of(u);
            }
        }
        return Optional.empty();
    }

    public List<User> getAllUsers() {
        if (mongoManager.isConnected() && mongoManager.getUsersCollection() != null) {
            try {
                List<User> list = new ArrayList<>();
                for (Document doc : mongoManager.getUsersCollection().find()) {
                    list.add(docToUser(doc));
                }
                return list;
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(memUsers.values());
    }

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
                .append("categoryBreakdown", new Document((Map) u.getCategoryBreakdown()))
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
                breakdown,
                solves);
    }

    // ═══════════════════════════════════════════════════════════
    // TEAMS
    // ═══════════════════════════════════════════════════════════

    public void saveTeam(Team team) {
        memTeams.put(team.getId(), team);
        if (mongoManager.isConnected() && mongoManager.getTeamsCollection() != null) {
            try {
                Document doc = teamToDoc(team);
                mongoManager.getTeamsCollection().replaceOne(
                        Filters.eq("id", team.getId()),
                        doc,
                        new ReplaceOptions().upsert(true));
            } catch (Exception ex) {
                System.err.println("[MongoRepository] Write error for team " + team.getId() + ": " + ex.getMessage());
            }
        }
    }

    public Optional<Team> getTeamById(String id) {
        if (mongoManager.isConnected() && mongoManager.getTeamsCollection() != null) {
            try {
                Document doc = mongoManager.getTeamsCollection().find(Filters.eq("id", id)).first();
                if (doc != null) return Optional.of(docToTeam(doc));
            } catch (Exception ignored) {}
        }
        return Optional.ofNullable(memTeams.get(id));
    }

    public Optional<Team> getTeamByName(String name) {
        if (mongoManager.isConnected() && mongoManager.getTeamsCollection() != null) {
            try {
                Document doc = mongoManager.getTeamsCollection().find(Filters.eq("teamName", name)).first();
                if (doc != null) return Optional.of(docToTeam(doc));
            } catch (Exception ignored) {}
        }
        for (Team t : memTeams.values()) {
            if (t.getTeamName().equalsIgnoreCase(name)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public List<Team> getAllTeams() {
        if (mongoManager.isConnected() && mongoManager.getTeamsCollection() != null) {
            try {
                List<Team> list = new ArrayList<>();
                for (Document doc : mongoManager.getTeamsCollection().find()) {
                    list.add(docToTeam(doc));
                }
                return list;
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(memTeams.values());
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
        memChallenges.put(challenge.getId(), challenge);
        if (mongoManager.isConnected() && mongoManager.getChallengesCollection() != null) {
            try {
                Document doc = challengeToDoc(challenge);
                mongoManager.getChallengesCollection().replaceOne(
                        Filters.eq("id", challenge.getId()),
                        doc,
                        new ReplaceOptions().upsert(true));
            } catch (Exception ex) {
                System.err.println("[MongoRepository] Write error for challenge " + challenge.getId() + ": " + ex.getMessage());
            }
        }
    }

    public Optional<Challenge> getChallengeById(String id) {
        if (mongoManager.isConnected() && mongoManager.getChallengesCollection() != null) {
            try {
                Document doc = mongoManager.getChallengesCollection().find(Filters.eq("id", id)).first();
                if (doc != null) return Optional.of(docToChallenge(doc));
            } catch (Exception ignored) {}
        }
        return Optional.ofNullable(memChallenges.get(id));
    }

    public List<Challenge> getAllChallenges() {
        if (mongoManager.isConnected() && mongoManager.getChallengesCollection() != null) {
            try {
                List<Challenge> list = new ArrayList<>();
                for (Document doc : mongoManager.getChallengesCollection().find()) {
                    list.add(docToChallenge(doc));
                }
                return list;
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(memChallenges.values());
    }

    public boolean deleteChallenge(String id) {
        memChallenges.remove(id);
        if (mongoManager.isConnected() && mongoManager.getChallengesCollection() != null) {
            try {
                return mongoManager.getChallengesCollection().deleteOne(Filters.eq("id", id)).getDeletedCount() > 0;
            } catch (Exception ignored) {}
        }
        return true;
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
            CPProblem cp = new CPProblem(
                    id,
                    title,
                    basePoints,
                    difficulty,
                    doc.getLong("timeLimitMs") != null ? doc.getLong("timeLimitMs") : 1000L,
                    doc.getInteger("memoryLimitMb", 256),
                    Path.of(doc.getString("testcaseDir") != null ? doc.getString("testcaseDir") : "contest_data/testcases/" + id));
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
        memContests.put(contest.getId(), contest);
        if (mongoManager.isConnected() && mongoManager.getContestsCollection() != null) {
            try {
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
            } catch (Exception ignored) {}
        }
    }

    public Optional<Contest> getContestById(String id) {
        if (mongoManager.isConnected() && mongoManager.getContestsCollection() != null) {
            try {
                Document doc = mongoManager.getContestsCollection().find(Filters.eq("id", id)).first();
                if (doc != null) {
                    return Optional.of(new Contest(
                            doc.getString("id"),
                            doc.getString("title"),
                            doc.getString("description"),
                            parseInstant(doc.getString("startTime")),
                            parseInstant(doc.getString("endTime")),
                            doc.getBoolean("isRunning", true),
                            doc.getList("registeredTeamIds", String.class, List.of())));
                }
            } catch (Exception ignored) {}
        }
        return Optional.ofNullable(memContests.get(id));
    }

    public List<Contest> getAllContests() {
        if (mongoManager.isConnected() && mongoManager.getContestsCollection() != null) {
            try {
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
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(memContests.values());
    }

    public void recordParticipation(ContestParticipation participation) {
        String key = participation.getContestId() + ":" + participation.getUserId();
        memParticipations.put(key, participation);

        if (mongoManager.isConnected() && mongoManager.getParticipationsCollection() != null) {
            try {
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
            } catch (Exception ignored) {}
        }
    }

    public Optional<ContestParticipation> getParticipation(String contestId, String userId) {
        if (mongoManager.isConnected() && mongoManager.getParticipationsCollection() != null) {
            try {
                Document doc = mongoManager.getParticipationsCollection().find(
                        Filters.and(Filters.eq("contestId", contestId), Filters.eq("userId", userId))).first();
                if (doc != null) {
                    return Optional.of(new ContestParticipation(
                            doc.getString("contestId"),
                            doc.getString("teamId"),
                            doc.getString("userId"),
                            parseInstant(doc.getString("joinedAt"))));
                }
            } catch (Exception ignored) {}
        }
        String key = contestId + ":" + userId;
        return Optional.ofNullable(memParticipations.get(key));
    }

    public List<ContestParticipation> getParticipationsByUser(String userId) {
        List<ContestParticipation> list = new ArrayList<>();
        if (mongoManager.isConnected() && mongoManager.getParticipationsCollection() != null) {
            try {
                for (Document doc : mongoManager.getParticipationsCollection().find(Filters.eq("userId", userId))) {
                    list.add(new ContestParticipation(
                            doc.getString("contestId"),
                            doc.getString("teamId"),
                            doc.getString("userId"),
                            parseInstant(doc.getString("joinedAt"))));
                }
                return list;
            } catch (Exception ignored) {}
        }
        for (ContestParticipation cp : memParticipations.values()) {
            if (cp.getUserId().equals(userId)) {
                list.add(cp);
            }
        }
        return list;
    }

    // ═══════════════════════════════════════════════════════════
    // SUBMISSIONS
    // ═══════════════════════════════════════════════════════════

    public void saveSubmission(Submission submission) {
        memSubmissions.add(submission);
        if (mongoManager.isConnected() && mongoManager.getSubmissionsCollection() != null) {
            try {
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
            } catch (Exception ignored) {}
        }
    }

    public List<Submission> getAllSubmissions() {
        if (mongoManager.isConnected() && mongoManager.getSubmissionsCollection() != null) {
            try {
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
            } catch (Exception ignored) {}
        }
        return new ArrayList<>(memSubmissions);
    }

    // ═══════════════════════════════════════════════════════════
    // SEEDING
    // ═══════════════════════════════════════════════════════════

    private void seedDefaultAdminIfEmpty() {
        Optional<User> existingAdmin = getUserByUsername("admin");
        if (existingAdmin.isEmpty()) {
            String adminHash = User.hashPassword("admin_password_123");
            User admin = new User("USER-ADMIN", "admin", "admin@cyberarena.local", adminHash, User.Role.ADMIN, null);
            saveUser(admin);
            System.out.println("[MongoRepository] Initialized administrator: admin / admin_password_123 (BCrypt)");
        } else if (!existingAdmin.get().verifyPassword("admin_password_123")) {
            User current = existingAdmin.get();
            String adminHash = User.hashPassword("admin_password_123");
            User updated = new User(current.getId(), current.getUsername(), current.getEmail(), adminHash, User.Role.ADMIN, current.getTeamId());
            saveUser(updated);
            System.out.println("[MongoRepository] Synchronized administrator password to: admin_password_123 (BCrypt)");
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
