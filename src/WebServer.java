import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * REST Web layer for Cyber-Algo Arena.
 * Connects Javalin endpoints with Mongo-backed ContestEngine.
 */
public final class WebServer {

    private final ContestEngine engine;
    private final Javalin app;
    private final ObjectMapper mapper;

    public WebServer(ContestEngine engine, int port) {
        this.engine = engine;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.app = Javalin.create(config -> {
            config.staticFiles.add(staticFileConfig -> {
                staticFileConfig.directory = "public";
                staticFileConfig.location = Location.EXTERNAL;
                staticFileConfig.hostedPath = "/";
            });
            config.http.defaultContentType = "application/json";
        });

        registerRoutes();
        app.start(port);
        System.out.println("[WebServer] Running on http://localhost:" + port);
    }

    private void registerRoutes() {
        // ── Auth ──
        app.post("/api/auth/login", this::handleLogin);
        app.post("/api/auth/register", this::handleRegister);
        app.post("/api/auth/logout", this::handleLogout);
        app.get("/api/auth/me", this::handleMe);

        // ── Teams Hub ──
        app.post("/api/teams/create", this::handleCreateTeam);
        app.post("/api/teams/join", this::handleJoinTeam);
        app.get("/api/teams/my-team", this::handleMyTeam);

        // ── Profiles (CTFtime Multi-team & Stats) ──
        app.get("/api/users/me/profile", this::handleMyProfile);
        app.get("/api/users/{id}/profile", this::handleUserProfile);

        // ── Contests ──
        app.get("/api/contests", this::handleGetContests);
        app.post("/api/contests/{id}/register", this::handleRegisterContest);

        // ── Challenges ──
        app.get("/api/challenges", this::handleGetChallenges);
        app.get("/api/challenges/{id}", this::handleGetChallenge);
        app.get("/api/challenges/{id}/download", this::handleDownloadAttachment);

        // ── Hints ──
        app.post("/api/hints/{challengeId}", this::handleRequestHint);

        // ── Submissions ──
        app.post("/api/submit", this::handleSubmit);

        // ── Leaderboard ──
        app.get("/api/leaderboard", this::handleLeaderboard);

        // ── Admin ──
        app.get("/api/admin/submissions", this::handleAdminSubmissions);
        app.post("/api/admin/challenges/ctf", this::handleAddCtf);
        app.post("/api/admin/challenges/cp", this::handleAddCp);
        app.put("/api/admin/challenges/{id}/points", this::handleUpdatePoints);
        app.delete("/api/admin/challenges/{id}", this::handleDeleteChallenge);
        app.post("/api/admin/sync", this::handleSync);
    }

    // ═══════════════════════════════════════════
    // AUTH HANDLERS
    // ═══════════════════════════════════════════

    private void handleLogin(Context ctx) {
        Map<String, String> body = parseBody(ctx);
        String username = body.get("username");
        String password = body.get("password");

        Optional<User> userOpt = engine.authenticate(username, password);
        if (userOpt.isEmpty()) {
            ctx.status(401).json(errorMap("Invalid username or password"));
            return;
        }

        User user = userOpt.get();
        ctx.sessionAttribute("userId", user.getId());
        ctx.json(userToMap(user));
    }

    private void handleRegister(Context ctx) {
        Map<String, String> body = parseBody(ctx);
        String username = body.getOrDefault("username", "").trim();
        String email = body.getOrDefault("email", "").trim();
        String password = body.getOrDefault("password", "").trim();

        if (username.isBlank() || password.isBlank()) {
            ctx.status(400).json(errorMap("Username and password required"));
            return;
        }

        try {
            User user = engine.registerUser(username, email, password);
            ctx.sessionAttribute("userId", user.getId());
            ctx.status(201).json(userToMap(user));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        }
    }

    private void handleLogout(Context ctx) {
        ctx.req().getSession().invalidate();
        ctx.json(Map.of("message", "Session terminated"));
    }

    private void handleMe(Context ctx) {
        User user = getSessionUser(ctx);
        if (user == null) {
            ctx.status(401).json(errorMap("Not authenticated"));
            return;
        }
        ctx.json(userToMap(user));
    }

    // ═══════════════════════════════════════════
    // TEAM HUB HANDLERS
    // ═══════════════════════════════════════════

    private void handleCreateTeam(Context ctx) {
        User user = requireAuth(ctx);
        if (user == null) return;

        Map<String, String> body = parseBody(ctx);
        String teamName = body.getOrDefault("teamName", "");
        String teamPassword = body.getOrDefault("teamPassword", "");

        try {
            Team team = engine.createTeam(teamName, teamPassword, user.getId());
            ctx.status(201).json(teamToMap(team));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        }
    }

    private void handleJoinTeam(Context ctx) {
        User user = requireAuth(ctx);
        if (user == null) return;

        Map<String, String> body = parseBody(ctx);
        String teamName = body.getOrDefault("teamName", "");
        String teamPassword = body.getOrDefault("teamPassword", "");

        try {
            Team team = engine.joinTeam(teamName, teamPassword, user.getId());
            ctx.json(teamToMap(team));
        } catch (TeamNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        }
    }

    private void handleMyTeam(Context ctx) {
        User user = requireAuth(ctx);
        if (user == null) return;

        if (user.getTeamId() == null) {
            ctx.status(404).json(errorMap("You are not currently part of any team."));
            return;
        }

        try {
            Team team = engine.getTeam(user.getTeamId());
            ctx.json(teamToMap(team));
        } catch (TeamNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════
    // PROFILE HANDLERS (CTFtime Radar & Metrics)
    // ═══════════════════════════════════════════

    private void handleMyProfile(Context ctx) {
        User user = requireAuth(ctx);
        if (user == null) return;
        ctx.json(userProfileToMap(user));
    }

    private void handleUserProfile(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            User user = engine.getUser(id);
            ctx.json(userProfileToMap(user));
        } catch (UserNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════
    // CONTEST HANDLERS
    // ═══════════════════════════════════════════

    private void handleGetContests(Context ctx) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Contest c : engine.getContests()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("title", c.getTitle());
            map.put("description", c.getDescription());
            map.put("startTime", c.getStartTime().toString());
            map.put("endTime", c.getEndTime().toString());
            map.put("isRunning", c.isRunning());
            map.put("registeredTeamsCount", c.getRegisteredTeamIds().size());
            list.add(map);
        }
        ctx.json(list);
    }

    private void handleRegisterContest(Context ctx) {
        User user = requireAuth(ctx);
        if (user == null) return;

        if (user.getTeamId() == null) {
            ctx.status(400).json(errorMap("Must join or create a team before entering a contest"));
            return;
        }

        String contestId = ctx.pathParam("id");
        try {
            engine.registerPlayerInContest(contestId, user.getTeamId(), user.getId());
            ctx.json(Map.of("message", "Registered for contest successfully", "contestId", contestId, "teamId", user.getTeamId()));
        } catch (IllegalStateException ex) {
            ctx.status(409).json(errorMap(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════
    // CHALLENGE HANDLERS
    // ═══════════════════════════════════════════

    private void handleGetChallenges(Context ctx) {
        User user = getSessionUser(ctx);
        String teamId = user != null ? user.getTeamId() : null;

        List<Map<String, Object>> result = new ArrayList<>();
        for (Challenge c : engine.getChallenges()) {
            result.add(challengeToMap(c, teamId, user));
        }
        ctx.json(result);
    }

    private void handleGetChallenge(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            Challenge c = engine.getChallenge(id);
            User user = getSessionUser(ctx);
            String teamId = user != null ? user.getTeamId() : null;
            Map<String, Object> map = challengeToMap(c, teamId, user);

            if (c instanceof CTFChallenge ctf) {
                map.put("category", ctf.getCategoryName());
                map.put("hintCost", ctf.getHintCost());
                map.put("attachmentFileName", ctf.getAttachmentFileName());
                map.put("hasAttachment", ctf.hasAttachment());
            } else if (c instanceof CPProblem cp) {
                map.put("timeLimitMs", cp.getTimeLimitMillis());
                map.put("memoryLimitMb", cp.getMemoryLimitMb());
                map.put("testcaseDir", cp.getTestcaseDirectory().toString());
            }

            if (teamId != null) {
                map.put("hintsUsed", engine.getHintUsageCount(teamId, id));
            }
            ctx.json(map);
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        }
    }

    private void handleDownloadAttachment(Context ctx) {
        String id = ctx.pathParam("id");
        try {
            Challenge c = engine.getChallenge(id);
            if (!(c instanceof CTFChallenge ctf) || !ctf.hasAttachment()) {
                ctx.status(404).json(errorMap("No attachment available for challenge " + id));
                return;
            }

            String fileName = ctf.getAttachmentFileName();
            Path path = Path.of("contest_data", "attachments", fileName);
            if (!Files.exists(path)) {
                path = Path.of(fileName);
            }

            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                ctx.status(404).json(errorMap("Attachment file not found on disk: " + fileName));
                return;
            }

            ctx.header("Content-Disposition", "attachment; filename=\"" + path.getFileName().toString() + "\"");
            ctx.contentType("application/octet-stream");
            ctx.result(Files.newInputStream(path));
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        } catch (IOException ex) {
            ctx.status(500).json(errorMap("Error reading attachment: " + ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════
    // HINTS & SUBMISSIONS
    // ═══════════════════════════════════════════

    private void handleRequestHint(Context ctx) {
        User user = requireAuth(ctx);
        if (user == null) return;

        String teamId = user.getTeamId();
        if (teamId == null) {
            ctx.status(400).json(errorMap("Join a team before requesting hints"));
            return;
        }

        String challengeId = ctx.pathParam("challengeId");
        try {
            String hint = engine.requestHint(teamId, challengeId);
            int usageCount = engine.getHintUsageCount(teamId, challengeId);
            Challenge c = engine.getChallenge(challengeId);
            ctx.json(Map.of(
                    "hint", hint,
                    "hintsUsed", usageCount,
                    "hintCost", c.getHintCost()
            ));
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        }
    }

    private void handleSubmit(Context ctx) {
        User user = requireAuth(ctx);
        if (user == null) return;

        String teamId = user.getTeamId();
        if (teamId == null) {
            ctx.status(400).json(errorMap("Join or create a team before submitting solutions."));
            return;
        }

        Map<String, String> body = parseBody(ctx);
        String challengeId = body.getOrDefault("challengeId", "").trim();
        String payload = body.getOrDefault("payload", "").trim();
        String contestId = body.getOrDefault("contestId", "GLOBAL");

        if (challengeId.isBlank() || payload.isBlank()) {
            ctx.status(400).json(errorMap("challengeId and payload required"));
            return;
        }

        try {
            Challenge challenge = engine.getChallenge(challengeId);

            String effectivePayload = payload;
            if (challenge instanceof CPProblem && !payload.contains("/") && !payload.contains("\\")) {
                Path tempDir = Files.createTempDirectory("cyber-algo-web-cp-");
                Files.writeString(tempDir.resolve("output_1.txt"), payload);
                effectivePayload = tempDir.toString();
            }

            String subId = "SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Submission submission = new Submission(
                    subId,
                    contestId,
                    user.getId(),
                    teamId,
                    challengeId,
                    effectivePayload,
                    engine.getWrongAttempts(teamId, challengeId),
                    engine.getHintUsageCount(teamId, challengeId),
                    Instant.now(),
                    SubmissionResult.Status.INVALID,
                    0,
                    "",
                    Instant.now());

            SubmissionResult result = engine.submit(submission);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("submissionId", subId);
            response.put("status", result.getStatus().name());
            response.put("pointsAwarded", result.getPointsAwarded());
            response.put("message", result.getMessage());
            response.put("teamScore", engine.getTeam(teamId).getTotalScore());
            response.put("personalScore", engine.getUser(user.getId()).getPersonalScore());

            if (challenge instanceof CTFChallenge ctf) {
                response.put("hashVerified", result.getStatus() == SubmissionResult.Status.ACCEPTED);
                response.put("hintDeduction", engine.getHintUsageCount(teamId, challengeId) * ctf.getHintCost());
            }

            ctx.json(response);
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        } catch (DuplicateSubmissionException | InvalidSubmissionException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        } catch (IOException ex) {
            ctx.status(500).json(errorMap("I/O error during evaluation: " + ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════
    // LEADERBOARD
    // ═══════════════════════════════════════════

    private void handleLeaderboard(Context ctx) {
        engine.refreshLeaderboard();
        List<Team> ranking = engine.getLeaderboard().getRanking();

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            Team t = ranking.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", i + 1);
            entry.put("teamId", t.getId());
            entry.put("teamName", t.getTeamName());
            entry.put("solves", engine.getSolveCount(t.getId()));
            entry.put("score", t.getTotalScore());
            entry.put("memberCount", t.getMemberUserIds().size());
            entry.put("lastSolveTime", t.getLastSolveTime() != null ? t.getLastSolveTime().toString() : null);
            result.add(entry);
        }
        ctx.json(result);
    }

    // ═══════════════════════════════════════════
    // ADMIN HANDLERS
    // ═══════════════════════════════════════════

    private void handleAdminSubmissions(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        List<Map<String, Object>> result = new ArrayList<>();
        List<Submission> subs = new ArrayList<>(engine.getSubmissions());
        subs.sort(Comparator.comparing(Submission::getTimestamp).reversed());

        for (Submission s : subs) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", s.getId());
            entry.put("userId", s.getUserId());
            entry.put("teamId", s.getTeamId());
            entry.put("challengeId", s.getChallengeId());
            entry.put("timestamp", s.getTimestamp().toString());
            entry.put("status", s.getStatus().name());
            entry.put("pointsAwarded", s.getPointsAwarded());
            entry.put("wrongAttempts", s.getWrongAttempts());
            entry.put("hintsUsed", s.getHintsUsed());
            result.add(entry);
        }
        ctx.json(result);
    }

    private void handleAddCtf(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        Map<String, String> body = parseBody(ctx);
        try {
            String id = requireField(body, "id");
            String title = requireField(body, "title");
            int basePoints = Integer.parseInt(requireField(body, "basePoints"));
            Challenge.Difficulty difficulty = Challenge.Difficulty.fromToken(requireField(body, "difficulty"));
            String category = requireField(body, "category");
            String rawFlag = requireField(body, "rawFlag");
            int hintCost = Integer.parseInt(requireField(body, "hintCost"));
            String attachmentFileName = body.get("attachmentFileName");
            if (attachmentFileName == null || attachmentFileName.isBlank()) {
                attachmentFileName = body.get("attachment");
            }

            CTFChallenge ctf = engine.addCtfChallenge(id, title, basePoints, difficulty, category, rawFlag, hintCost, attachmentFileName);
            ctx.status(201).json(Map.of("message", "CTF challenge created", "id", ctf.getId()));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        }
    }

    private void handleAddCp(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        Map<String, String> body = parseBody(ctx);
        try {
            String id = requireField(body, "id");
            String title = requireField(body, "title");
            int basePoints = Integer.parseInt(requireField(body, "basePoints"));
            Challenge.Difficulty difficulty = Challenge.Difficulty.fromToken(requireField(body, "difficulty"));
            long timeLimitMs = Long.parseLong(requireField(body, "timeLimitMs"));
            int memoryLimitMb = Integer.parseInt(requireField(body, "memoryLimitMb"));
            Path testcaseDir = Path.of(requireField(body, "testcaseDir"));

            CPProblem cp = engine.addCpChallenge(id, title, basePoints, difficulty, timeLimitMs, memoryLimitMb, testcaseDir);
            ctx.status(201).json(Map.of("message", "CP challenge created", "id", cp.getId()));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        }
    }

    private void handleUpdatePoints(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        String id = ctx.pathParam("id");
        Map<String, String> body = parseBody(ctx);
        try {
            int newPoints = Integer.parseInt(requireField(body, "basePoints"));
            engine.updateChallengeBasePoints(id, newPoints);
            ctx.json(Map.of("message", "Points updated", "id", id, "newPoints", newPoints));
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        } catch (NumberFormatException ex) {
            ctx.status(400).json(errorMap("Invalid points value"));
        }
    }

    private void handleDeleteChallenge(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        String id = ctx.pathParam("id");
        try {
            engine.removeChallenge(id);
            ctx.json(Map.of("message", "Challenge deleted from database and disk", "id", id));
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        }
    }

    private void handleSync(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        engine.syncData();
        ctx.json(Map.of("message", "Memory state synchronized with database."));
    }

    // ═══════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════

    private User getSessionUser(Context ctx) {
        String userId = ctx.sessionAttribute("userId");
        if (userId == null) return null;
        try {
            return engine.getUser(userId);
        } catch (UserNotFoundException ex) {
            return null;
        }
    }

    private User requireAuth(Context ctx) {
        User user = getSessionUser(ctx);
        if (user == null) {
            ctx.status(401).json(errorMap("Authentication required"));
        }
        return user;
    }

    private User requireAdmin(Context ctx) {
        User user = requireAuth(ctx);
        if (user != null && !user.isAdmin()) {
            ctx.status(403).json(errorMap("Admin access required"));
            return null;
        }
        return user;
    }

    private Map<String, Object> userToMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("role", user.getRole().name());
        map.put("teamId", user.getTeamId());
        return map;
    }

    private Map<String, Object> userProfileToMap(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", user.getId());
        map.put("username", user.getUsername());
        map.put("email", user.getEmail());
        map.put("role", user.getRole().name());
        map.put("teamId", user.getTeamId());
        map.put("personalScore", user.getPersonalScore());
        map.put("solvesCount", user.getSolvesCount());
        map.put("categoryBreakdown", user.getCategoryBreakdown());
        map.put("solvedChallenges", user.getSolvedChallengeIds());
        map.put("createdAt", user.getCreatedAt().toString());

        if (user.getTeamId() != null) {
            try {
                Team t = engine.getTeam(user.getTeamId());
                map.put("teamName", t.getTeamName());
                map.put("isCaptain", user.getId().equals(t.getCaptainUserId()));
            } catch (Exception ignored) {}
        }
        return map;
    }

    private Map<String, Object> teamToMap(Team team) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", team.getId());
        map.put("teamName", team.getTeamName());
        map.put("captainUserId", team.getCaptainUserId());
        map.put("memberUserIds", team.getMemberUserIds());
        map.put("totalScore", team.getTotalScore());
        map.put("lastSolveTime", team.getLastSolveTime() != null ? team.getLastSolveTime().toString() : null);
        map.put("createdAt", team.getCreatedAt().toString());

        List<Map<String, String>> memberList = new ArrayList<>();
        for (String mId : team.getMemberUserIds()) {
            try {
                User u = engine.getUser(mId);
                memberList.add(Map.of("userId", u.getId(), "username", u.getUsername(), "score", String.valueOf(u.getPersonalScore())));
            } catch (Exception ignored) {}
        }
        map.put("members", memberList);
        return map;
    }

    private Map<String, Object> challengeToMap(Challenge c, String teamId, User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", c.getId());
        map.put("type", c.getType());
        map.put("title", c.getTitle());
        map.put("difficulty", c.getDifficulty().name());
        map.put("basePoints", c.getBasePoints());
        map.put("hintCost", c.getHintCost());

        if (c instanceof CTFChallenge ctf) {
            map.put("category", ctf.getCategoryName());
            map.put("attachmentFileName", ctf.getAttachmentFileName());
            map.put("hasAttachment", ctf.hasAttachment());
            if (ctf.hasAttachment()) {
                Path p = Path.of("contest_data", "attachments", ctf.getAttachmentFileName());
                if (!Files.exists(p)) {
                    p = Path.of(ctf.getAttachmentFileName());
                }
                if (Files.exists(p)) {
                    try {
                        map.put("attachmentSize", Files.size(p));
                    } catch (Exception ignored) {}
                }
            }
        } else if (c instanceof CPProblem cp) {
            map.put("timeLimitMs", cp.getTimeLimitMillis());
            map.put("memoryLimitMb", cp.getMemoryLimitMb());
            map.put("testcaseDir", cp.getTestcaseDirectory().toString());
        }

        if (teamId != null) {
            map.put("solved", engine.isSolvedByTeam(teamId, c.getId()));
        } else if (user != null) {
            map.put("solved", user.isSolved(c.getId()));
        } else {
            map.put("solved", false);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseBody(Context ctx) {
        try {
            return mapper.readValue(ctx.body(), Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String requireField(Map<String, String> body, String key) {
        String value = body.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value;
    }

    private static Map<String, String> errorMap(String message) {
        return Map.of("error", message);
    }

    public Javalin getApp() {
        return app;
    }

    public void stop() {
        app.stop();
    }
}
