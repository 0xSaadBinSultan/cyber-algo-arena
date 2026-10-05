package com.cyberalgo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.staticfiles.Location;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Hardened REST Web layer for Cyber-Algo Arena.
 * Enforces rate limiting, path traversal guards, security response headers, and session controls.
 */
public final class WebServer {

    private final ContestEngine engine;
    private final Javalin app;
    private final ObjectMapper mapper;
    private final RateLimiter rateLimiter;
    private final SecureRandom secureRandom;
    private final ContestRadarService radarService;
    private final CodeforcesSyncService codeforcesSyncService;
    private final SecurityPuzzleSyncService securityPuzzleSyncService;
    private final ProblemSyncService problemSyncService;
    private final AiTutorService aiTutorService;

    public WebServer(ContestEngine engine, int port) {
        this.engine = engine;
        this.rateLimiter = new RateLimiter();
        this.secureRandom = new SecureRandom();
        this.radarService = new ContestRadarService();
        this.codeforcesSyncService = new CodeforcesSyncService();
        this.securityPuzzleSyncService = new SecurityPuzzleSyncService();
        this.problemSyncService = new ProblemSyncService();
        this.aiTutorService = new AiTutorService(new GeminiService());
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

        int effectivePort = resolvePort(port);
        registerSecurityMiddleware();
        registerRoutes();
        app.start(effectivePort);
        System.out.println("[WebServer] Running on http://localhost:" + effectivePort);
    }

    public static int resolvePort(int defaultPort) {
        if (defaultPort > 0) {
            return defaultPort;
        }
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                return Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 8080;
    }

    private void registerSecurityMiddleware() {
        app.before(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("Content-Security-Policy",
                    "default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com https://cdn.jsdelivr.net; " +
                    "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
                    "font-src 'self' https://fonts.gstatic.com https://cdnjs.cloudflare.com; img-src 'self' data:; connect-src 'self';");
        });

        // Strict RBAC Interceptor for all administrative operations
        app.before("/api/admin/*", ctx -> {
            String userId = ctx.sessionAttribute("userId");
            if (userId == null || userId.isBlank()) {
                throw new io.javalin.http.UnauthorizedResponse("Unauthorized: Authentication required for administrative operations");
            }
            try {
                User user = engine.getUser(userId);
                if (user == null || user.getRole() != User.Role.ADMIN) {
                    throw new io.javalin.http.ForbiddenResponse("Forbidden: Administrator privileges required");
                }
            } catch (Exception ex) {
                throw new io.javalin.http.ForbiddenResponse("Forbidden: Administrator privileges required");
            }
        });
    }

    private void registerRoutes() {
        app.get("/admin", this::handleAdminPage);
        app.get("/admin/", this::handleAdminPage);

        app.get("/api/ping", ctx -> ctx.json(Map.of("status", "ok", "time", Instant.now().toString())));
        app.get("/api/health/live", ctx -> ctx.status(200).json(Map.of(
                "status", "UP",
                "engine", "Cyber-Algo Arena",
                "timestamp", System.currentTimeMillis()
        )));
        app.get("/api/health/ready", ctx -> {
            boolean databaseReady = engine.isDatabaseReady();
            ctx.status(databaseReady ? 200 : 503).json(Map.of(
                    "status", databaseReady ? "READY" : "NOT_READY",
                    "database", databaseReady ? "UP" : "DOWN",
                    "timestamp", System.currentTimeMillis()
            ));
        });
        app.get("/api/health", ctx -> {
            boolean databaseReady = engine.isDatabaseReady();
            ctx.status(200).json(Map.of(
                    "status", "UP",
                    "engine", "Cyber-Algo Arena",
                    "database", databaseReady ? "UP" : "DEGRADED",
                    "timestamp", System.currentTimeMillis()
            ));
        });
        app.get("/api/judge/status", this::handleJudgeStatus);

        // ── Auth ──
        app.post("/api/auth/login", this::handleLogin);
        app.post("/api/auth/admin-login", this::handleAdminLogin);
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

        // ── Contests & Radar ──
        app.get("/api/contests", this::handleGetContests);
        app.post("/api/contests/{id}/register", this::handleRegisterContest);
        app.get("/api/events/upcoming", this::handleUpcomingEvents);

        // ── Challenges ──
        app.get("/api/challenges", this::handleGetChallenges);
        app.get("/api/challenges/{id}", this::handleGetChallenge);
        app.get("/api/challenges/{id}/download", this::handleDownloadAttachment);

        // ── Hints ──
        app.post("/api/hints/{challengeId}", this::handleRequestHint);

        // ── AI Tutor ──
        app.get("/api/ai/status", this::handleAiStatus);
        app.post("/api/ai/tutor", this::handleAiTutor);

        // ── Submissions ──
        app.post("/api/submit", this::handleSubmit);

        // ── Leaderboard ──
        app.get("/api/leaderboard", this::handleLeaderboard);

        // ── Admin ──
        app.get("/api/admin/submissions", this::handleAdminSubmissions);
        app.post("/api/admin/scoreboard/freeze", this::handleFreezeScoreboard);
        app.post("/api/admin/challenges", this::handleAddChallenge);
        
        app.put("/api/admin/challenges/{id}/points", this::handleUpdatePoints);
        app.delete("/api/admin/challenges/{id}", this::handleDeleteChallenge);
        app.post("/api/admin/sync", this::handleSync);
        app.post("/api/admin/sync/codeforces", this::handleSyncCodeforces);
        app.post("/api/admin/sync/atcoder", this::handleSyncAtCoder);
        app.post("/api/admin/sync/codechef", this::handleSyncCodeChef);
        app.post("/api/admin/sync/security-exercises", this::handleSyncSecurityExercises);
    }

    private void handleAdminPage(Context ctx) {
        ctx.header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        ctx.header("Pragma", "no-cache");
        ctx.header("X-Robots-Tag", "noindex, nofollow, noarchive");

        String userId = ctx.sessionAttribute("userId");
        boolean authorized = false;
        try {
            if (userId != null && !userId.isBlank()) {
                User user = engine.getUser(userId);
                authorized = user != null && user.getRole() == User.Role.ADMIN;
            }

            Path view = authorized
                    ? Path.of("views", "admin.html")
                    : Path.of("views", "admin-login.html");
            ctx.html(Files.readString(view));
        } catch (Exception ex) {
            ctx.status(404).result("Admin UI not found");
        }
    }

    private void handleJudgeStatus(Context ctx) {
        String configured = System.getenv("PISTON_URL");
        ctx.json(Map.of(
                "provider", "Piston-compatible sandbox",
                "configured", configured != null && !configured.isBlank(),
                "mode", (configured != null && !configured.isBlank()) ? "configured" : "public-fallback"
        ));
    }

    // ═══════════════════════════════════════════
    // AUTH HANDLERS
    // ═══════════════════════════════════════════

    private void handleLogin(Context ctx) {
        String ip = ctx.ip();
        if (!rateLimiter.allow("login:" + ip, 20, 60_000L)) {
            ctx.status(429).json(errorMap("Too Many Requests: Rate limit exceeded. Try again in 60 seconds."));
            return;
        }

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
        ctx.sessionAttribute("role", user.getRole().name());
        ctx.json(userToMap(user));
    }

    private void handleAdminLogin(Context ctx) {
        String ip = ctx.ip();
        if (!rateLimiter.allow("admin_login:" + ip, 10, 60_000L)) {
            ctx.status(429).json(errorMap("Too Many Requests: Rate limit exceeded. Try again in 60 seconds."));
            return;
        }

        Map<String, String> body = parseBody(ctx);
        String username = body.get("username");
        String password = body.get("password");

        Optional<User> userOpt = engine.authenticate(username, password);
        if (userOpt.isEmpty()) {
            ctx.status(401).json(errorMap("Invalid administrator credentials"));
            return;
        }

        User user = userOpt.get();
        if (user.getRole() != User.Role.ADMIN) {
            ctx.status(403).json(errorMap("Forbidden: Account does not possess administrator privileges"));
            return;
        }

        ctx.sessionAttribute("userId", user.getId());
        ctx.sessionAttribute("role", user.getRole().name());
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

    private void handleAiStatus(Context ctx) {
        ctx.json(Map.of(
                "configured", aiTutorService.isConfigured(),
                "model", aiTutorService.model()
        ));
    }

    private void handleAiTutor(Context ctx) {
        String ip = ctx.ip();
        if (!rateLimiter.allow("ai_tutor:" + ip, 12, 60_000L)) {
            ctx.status(429).json(errorMap("AI Tutor rate limit exceeded. Try again shortly."));
            return;
        }

        User user = requireAuth(ctx);
        if (user == null) return;

        Map<String, String> body = parseBody(ctx);
        String challengeId = body.getOrDefault("challengeId", "").trim();
        String mode = body.getOrDefault("mode", "HINT");
        String question = body.getOrDefault("question", "");
        String code = body.getOrDefault("code", "");
        String compilerError = body.getOrDefault("compilerError", "");

        int hintLevel = 1;
        try {
            hintLevel = Integer.parseInt(body.getOrDefault("hintLevel", "1"));
        } catch (NumberFormatException ignored) {
            hintLevel = 1;
        }

        if (challengeId.isBlank()) {
            ctx.status(400).json(errorMap("challengeId is required"));
            return;
        }

        if (!aiTutorService.isConfigured()) {
            ctx.status(503).json(errorMap("Gemini AI Tutor is not configured on the server."));
            return;
        }

        try {
            Challenge challenge = engine.getChallenge(challengeId);
            String message = aiTutorService.tutor(
                    challenge,
                    mode,
                    hintLevel,
                    question,
                    code,
                    compilerError);

            ctx.json(Map.of(
                    "message", message,
                    "mode", mode.toUpperCase(Locale.ROOT),
                    "hintLevel", Math.max(1, Math.min(3, hintLevel)),
                    "model", aiTutorService.model()
            ));
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        } catch (IllegalStateException ex) {
            ctx.status(502).json(errorMap(ex.getMessage()));
        }
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

    private void handleUpcomingEvents(Context ctx) {
        ctx.json(radarService.getUpcomingEvents());
    }

    // ═══════════════════════════════════════════
    // CHALLENGE HANDLERS & PATH TRAVERSAL GUARD
    // ═══════════════════════════════════════════

    private void handleGetChallenges(Context ctx) {
        engine.syncIfStale(); // converge with database (multi-instance safe)
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
            engine.syncIfStale(); // converge with database (multi-instance safe)
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

            String requestedFileName = ctf.getAttachmentFileName();
            if (requestedFileName == null || requestedFileName.isBlank()) {
                ctx.status(404).json(errorMap("No file name configured for challenge " + id));
                return;
            }

            // Path Traversal Defense: Strict canonical resolution against attachments base directory
            Path baseDir = Path.of("contest_data", "attachments").toAbsolutePath().normalize();
            Files.createDirectories(baseDir);

            Path resolvedPath = baseDir.resolve(requestedFileName).normalize();
            if (!resolvedPath.startsWith(baseDir)) {
                ctx.status(403).json(errorMap("Access Denied: Path traversal detected."));
                return;
            }

            if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
                ctx.status(404).json(errorMap("Attachment file not found on disk: " + requestedFileName));
                return;
            }

            ctx.header("Content-Disposition", "attachment; filename=\"" + resolvedPath.getFileName().toString() + "\"");
            ctx.contentType("application/octet-stream");
            ctx.result(Files.newInputStream(resolvedPath));
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        } catch (IOException ex) {
            ctx.status(500).json(errorMap("Error reading attachment: " + ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════
    // HINTS & SUBMISSIONS WITH RATE LIMITING
    // ═══════════════════════════════════════════

    private void handleRequestHint(Context ctx) {
        User user = requireAuth(ctx);
        if (user == null) return;

        String challengeId = ctx.pathParam("challengeId");
        try {
            String hint = engine.requestHintForUser(user.getId(), challengeId);
            int usageCount = engine.getUserHintUsageCount(user.getId(), challengeId);
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
        String rateKey = "submit:" + user.getId() + ":" + ctx.ip();

        if (rateLimiter.isCooldownActive(rateKey, 3, 15_000L)) {
            long remaining = rateLimiter.getRemainingCooldownSeconds(rateKey, 15_000L);
            ctx.status(429).json(errorMap("Submission cooldown active. Wait " + remaining + " seconds before re-submitting."));
            return;
        }

        if (!rateLimiter.allow(rateKey, 10, 60_000L)) {
            ctx.status(429).json(errorMap("Too Many Requests: max 10 submissions per minute."));
            return;
        }

        Map<String, String> body = parseBody(ctx);
        String challengeId = body.getOrDefault("challengeId", "").trim();
        String payload = body.getOrDefault("payload", "").trim();
        String contestId = body.getOrDefault("contestId", "GLOBAL");
        String language = body.getOrDefault("language", "").trim();

        if (challengeId.isBlank() || payload.isBlank()) {
            ctx.status(400).json(errorMap("challengeId and payload required"));
            return;
        }

        try {
            Challenge challenge = engine.getChallenge(challengeId);
            if (challenge instanceof CPProblem && payload.length() > 100_000) {
                ctx.status(413).json(errorMap("CP source code exceeds the 100 KB submission limit"));
                return;
            }
            if (challenge instanceof CTFChallenge && payload.length() > 4096) {
                ctx.status(413).json(errorMap("CTF submission payload is too large"));
                return;
            }
            if (challenge instanceof CPProblem && language.isBlank()) {
                ctx.status(400).json(errorMap("language is required for CP submissions"));
                return;
            }

            String subId = "SUB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Submission submission = new Submission(
                    subId,
                    contestId,
                    user.getId(),
                    teamId,
                    challengeId,
                    payload,
                    engine.getUserWrongAttempts(user.getId(), challengeId),
                    engine.getUserHintUsageCount(user.getId(), challengeId),
                    Instant.now(),
                    SubmissionResult.Status.PENDING,
                    0,
                    "",
                    Instant.now());

            SubmissionResult result = engine.submit(submission, language);

            if (result.getStatus() == SubmissionResult.Status.ACCEPTED) {
                rateLimiter.resetFailures(rateKey);
            } else if (result.getStatus() != SubmissionResult.Status.INVALID
                    && result.getStatus() != SubmissionResult.Status.PENDING) {
                rateLimiter.recordFailure(rateKey);
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("submissionId", subId);
            response.put("status", result.getStatus().name());
            response.put("pointsAwarded", result.getPointsAwarded());
            response.put("message", result.getMessage());
            response.put("personalScore", engine.getUser(user.getId()).getPersonalScore());
            response.put("track", challenge.getType());

            if (teamId != null && !teamId.isBlank()) {
                response.put("teamScore", engine.getTeam(teamId).getTotalScore());
            } else {
                response.put("teamScore", null);
            }

            if (challenge instanceof CTFChallenge ctf) {
                response.put("hashVerified", result.getStatus() == SubmissionResult.Status.ACCEPTED);
                response.put("hintDeduction", engine.getUserHintUsageCount(user.getId(), challengeId) * ctf.getHintCost());
            }

            ctx.json(response);
        } catch (ChallengeNotFoundException ex) {
            ctx.status(404).json(errorMap(ex.getMessage()));
        } catch (DuplicateSubmissionException | InvalidSubmissionException ex) {
            ctx.status(400).json(errorMap(ex.getMessage()));
        }
    }

    // ═══════════════════════════════════════════
    // LEADERBOARD
    // ═══════════════════════════════════════════

    private void handleFreezeScoreboard(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;
        List<Contest> contests = engine.getContests();
        if (contests.isEmpty()) {
            ctx.status(404).json(errorMap("No active contest found to freeze."));
            return;
        }
        Contest activeContest = contests.get(0);

        Map<String, String> body = parseBody(ctx);
        boolean freeze = Boolean.parseBoolean(body.get("freeze"));

        activeContest.toggleFreeze(freeze);
        engine.getRepository().saveContest(activeContest);
        engine.syncData(); // refresh anything necessary

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("scoreboardFrozen", activeContest.isScoreboardFrozen());
        response.put("frozenAt", activeContest.getFreezeTimestamp());
        ctx.json(response);
    }

    private void handleLeaderboard(Context ctx) {
        engine.refreshLeaderboard();
        
        List<Contest> contests = engine.getContests();
        Contest activeContest = contests.isEmpty() ? null : contests.get(0);
        boolean isFrozen = activeContest != null && activeContest.isScoreboardFrozen();
        
        String role = ctx.sessionAttribute("role");
        boolean isAdmin = "ADMIN".equals(role);
        
        List<Team> finalRanking;
        
        if (!isFrozen || isAdmin) {
            finalRanking = engine.getLeaderboard().getRanking();
        } else {
            long freezeTimestamp = activeContest.getFreezeTimestamp();
            Map<String, Team> snapshotTeams = new HashMap<>();
            
            List<Team> teams = engine.getTeams();
            if (teams != null) {
                for (Team t : teams) {
                    if (t != null) {
                        snapshotTeams.put(t.getId(), new Team(t.getId(), t.getTeamName(), t.getMemberUserIds(), 0, null));
                    }
                }
            }
            
            List<Submission> subs = engine.getSubmissions();
            if (subs != null) {
                for (Submission s : subs) {
                    if (s != null && s.getTimestamp() != null && s.getTimestamp().toEpochMilli() <= freezeTimestamp) {
                        Team t = snapshotTeams.get(s.getTeamId());
                        if (t != null && s.getResult() != null && s.getResult().getStatus() == SubmissionResult.Status.ACCEPTED) {
                            t.applyScore(s.getResult().getPointsAwarded(), s.getTimestamp());
                        }
                    }
                }
            }
            Leaderboard snapBoard = new Leaderboard();
            snapBoard.update(snapshotTeams.values());
            finalRanking = snapBoard.getRanking();
        }

        if (finalRanking == null) {
            finalRanking = new ArrayList<>();
        }

        List<Map<String, Object>> standings = new ArrayList<>();
        for (int i = 0; i < finalRanking.size(); i++) {
            Team t = finalRanking.get(i);
            if (t == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", i + 1);
            entry.put("teamId", t.getId());
            entry.put("teamName", t.getTeamName());
            if (!isFrozen || isAdmin) {
                entry.put("solves", engine.getSolveCount(t.getId()));
            } else {
                long ft = activeContest != null ? activeContest.getFreezeTimestamp() : 0L;
                long solveCount = 0;
                List<Submission> allSubs = engine.getSubmissions();
                if (allSubs != null) {
                    solveCount = allSubs.stream()
                        .filter(s -> s != null && s.getTeamId() != null && s.getTeamId().equals(t.getId()) 
                                  && s.getTimestamp() != null && s.getTimestamp().toEpochMilli() <= ft
                                  && s.getResult() != null 
                                  && s.getResult().getStatus() == SubmissionResult.Status.ACCEPTED)
                        .count();
                }
                entry.put("solves", solveCount);
            }
            entry.put("score", t.getTotalScore());
            entry.put("memberCount", t.getMemberUserIds() != null ? t.getMemberUserIds().size() : 0);
            entry.put("lastSolveTime", t.getLastSolveTime() != null ? t.getLastSolveTime().toString() : null);
            standings.add(entry);
        }
        
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("isFrozen", isFrozen);
        response.put("adminBypass", isFrozen && isAdmin);
        response.put("standings", standings != null ? standings : new ArrayList<>());
        response.put("timeline", new ArrayList<>()); // Timeline placeholder for graph
        
        ctx.contentType("application/json");
        ctx.json(response);
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

    private void handleAddChallenge(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        Map<String, String> body = parseBody(ctx);
        try {
            String type = requireField(body, "type");
            String id = requireField(body, "id");
            String title = requireField(body, "title");
            String description = body.getOrDefault("description", "");
            int basePoints = Integer.parseInt(requireField(body, "basePoints"));
            Challenge.Difficulty difficulty = Challenge.Difficulty.fromToken(requireField(body, "difficulty"));

            Challenge created = null;

            if ("CTF".equalsIgnoreCase(type)) {
                String category = requireField(body, "category");
                int hintCost = Integer.parseInt(body.getOrDefault("hintCost", "0"));
                String attachmentFileName = body.get("attachmentFileName");
                if (attachmentFileName == null || attachmentFileName.isBlank()) {
                    attachmentFileName = body.get("attachment");
                }
                // Verification type: exact string (rawFlag, hashed server-side) or pre-computed SHA-256 hash
                String flagHash = body.get("flagHash");
                CTFChallenge ctf;
                if (flagHash != null && flagHash.trim().matches("[0-9a-fA-F]{64}")) {
                    ctf = new CTFChallenge(id, title, basePoints, difficulty, category,
                            flagHash.trim().toLowerCase(java.util.Locale.ROOT), hintCost, attachmentFileName);
                    engine.addChallenge(ctf);
                } else {
                    String rawFlag = requireField(body, "rawFlag");
                    ctf = engine.addCtfChallenge(id, title, basePoints, difficulty, category, rawFlag, hintCost, attachmentFileName);
                }
                ctf.setDescription(description);
                engine.getRepository().saveChallenge(ctf);
                created = ctf;
            } else if ("CP".equalsIgnoreCase(type)) {
                long timeLimitMs = Long.parseLong(body.getOrDefault("timeLimitMs", "1000"));
                int memoryLimitMb = Integer.parseInt(body.getOrDefault("memoryLimitMb", "256"));

                String sampleIn = body.getOrDefault("sampleInput", "");
                String sampleOut = body.getOrDefault("sampleOutput", "");
                String hiddenTestsJson = body.getOrDefault("hiddenTests", "[]");

                if (sampleIn.isBlank() && sampleOut.isBlank()) {
                    throw new IllegalArgumentException("CP problem requires a public sample testcase");
                }

                List<CPTestCase> testCases = new ArrayList<>();
                testCases.add(CPTestCase.sample(sampleIn, sampleOut));

                try {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> hidden = mapper.readValue(hiddenTestsJson, List.class);
                    if (hidden.size() > 30) {
                        throw new IllegalArgumentException("At most 30 hidden testcases are allowed");
                    }
                    int totalHiddenChars = 0;
                    for (Map<String, Object> item : hidden) {
                        String input = Objects.toString(item.get("input"), "");
                        String output = Objects.toString(item.get("output"), "");
                        if (input.isBlank() && output.isBlank()) continue;
                        totalHiddenChars += input.length() + output.length();
                        if (totalHiddenChars > 2_000_000) {
                            throw new IllegalArgumentException("Hidden testcase payload is too large");
                        }
                        testCases.add(CPTestCase.hidden(input, output));
                    }
                } catch (IllegalArgumentException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new IllegalArgumentException("hiddenTests must be a valid JSON array");
                }

                if (testCases.stream().noneMatch(CPTestCase::hidden)) {
                    throw new IllegalArgumentException("CP problem requires at least one hidden testcase");
                }

                Path testcaseDir = Path.of("contest_data", "testcases", id);
                CPProblem cp = engine.addCpChallenge(
                        id, title, basePoints, difficulty,
                        timeLimitMs, memoryLimitMb, testcaseDir, testCases);
                cp.setDescription(description);
                engine.getRepository().saveChallenge(cp);
                created = cp;
            } else {
                throw new IllegalArgumentException("Invalid challenge type");
            }

            ctx.status(201).json(Map.of("message", "Challenge created", "id", created.getId()));
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
            ctx.json(Map.of("status", "SUCCESS", "message", "Challenge deleted"));
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

    private void handleSyncCodeforces(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        int count = 10;
        int minRating = 800;
        int maxRating = 1400;
        try {
            String countParam = ctx.queryParam("count");
            if (countParam != null) count = Integer.parseInt(countParam);
            String minParam = ctx.queryParam("minRating");
            if (minParam != null) minRating = Integer.parseInt(minParam);
            String maxParam = ctx.queryParam("maxRating");
            if (maxParam != null) maxRating = Integer.parseInt(maxParam);
        } catch (NumberFormatException ignored) {}

        CodeforcesSyncService.SyncResult result = codeforcesSyncService.sync(engine, count, minRating, maxRating);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Codeforces sync complete");
        response.put("syncedCount", result.syncedCount());
        response.put("problems", result.problems());
        ctx.json(response);
    }

    private void handleSyncSecurityExercises(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        String category = ctx.queryParamAsClass("category", String.class).getOrDefault("ALL");
        SecurityPuzzleSyncService.SyncResult result = securityPuzzleSyncService.sync(engine, category);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Security exercise sync complete");
        response.put("syncedCount", result.syncedCount());
        response.put("problems", result.problems());
        ctx.json(response);
    }

    private void handleSyncAtCoder(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        ProblemSyncService.SyncResult result = problemSyncService.syncAtCoder(engine);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "AtCoder challenge sync complete");
        response.put("syncedCount", result.syncedCount());
        response.put("problems", result.problemIds());
        ctx.json(response);
    }

    private void handleSyncCodeChef(Context ctx) {
        User user = requireAdmin(ctx);
        if (user == null) return;

        ProblemSyncService.SyncResult result = problemSyncService.syncCodeChef(engine);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "CodeChef challenge sync complete");
        response.put("syncedCount", result.syncedCount());
        response.put("problems", result.problemIds());
        ctx.json(response);
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
        map.put("ctfScore", user.getCtfScore());
        map.put("ctfSolvesCount", user.getCtfSolvesCount());
        map.put("cpScore", user.getCpScore());
        map.put("cpSolvesCount", user.getCpSolvesCount());
        map.put("categoryBreakdown", user.getCategoryBreakdown());
        map.put("solvedChallenges", user.getSolvedChallengeIds());
        map.put("createdAt", user.getCreatedAt().toString());

        // Compute Global Rank
        List<User> allUsers = new ArrayList<>(engine.getUsers());
        allUsers.sort((a, b) -> Integer.compare(b.getPersonalScore(), a.getPersonalScore()));
        int rank = 1;
        for (int i = 0; i < allUsers.size(); i++) {
            if (allUsers.get(i).getId().equals(user.getId())) {
                rank = i + 1;
                break;
            }
        }
        map.put("globalRank", rank);

        // Build detailed solved log from engine submissions
        List<Map<String, Object>> solvedLog = new ArrayList<>();
        for (Submission s : engine.getSubmissions()) {
            if (s.getUserId().equals(user.getId()) && s.getStatus() == SubmissionResult.Status.ACCEPTED) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("challengeId", s.getChallengeId());
                try {
                    Challenge ch = engine.getChallenge(s.getChallengeId());
                    entry.put("title", ch.getTitle());
                    boolean isCp = ch instanceof CPProblem;
                    entry.put("type", isCp ? "CP" : "CTF");
                    entry.put("category", isCp ? "CP" : ((CTFChallenge) ch).getCategoryName());
                } catch (Exception ex) {
                    entry.put("title", s.getChallengeId());
                    entry.put("type", "CTF");
                    entry.put("category", "MISC");
                }
                entry.put("points", s.getPointsAwarded());
                entry.put("timestamp", s.getTimestamp().toString());
                solvedLog.add(entry);
            }
        }
        map.put("solvedLog", solvedLog);

        if (user.getTeamId() != null) {
            try {
                Team t = engine.getTeam(user.getTeamId());
                map.put("teamName", t.getTeamName());
                map.put("isCaptain", user.getId().equals(t.getCaptainUserId()));
                map.put("teamScore", t.getTotalScore());
                List<Map<String, String>> memberList = new ArrayList<>();
                for (String mId : t.getMemberUserIds()) {
                    try {
                        User u = engine.getUser(mId);
                        memberList.add(Map.of("userId", u.getId(), "username", u.getUsername(), "score", String.valueOf(u.getPersonalScore())));
                    } catch (Exception ignored) {}
                }
                map.put("teamMembers", memberList);
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
        map.put("description", c.getDescription());
        map.put("currentPoints", c.getDynamicPoints());
        map.put("solveCount", c.getSolveCount());
        map.put("decayLimit", c.getDecayLimit());
        map.put("minimumPoints", c.getMinimumPoints());
        map.put("firstBloodTeamId", c.getFirstBloodTeamId());
        map.put("firstBloodUserId", c.getFirstBloodUserId());

        if (c instanceof CTFChallenge ctf) {
            map.put("category", ctf.getCategoryName());
            map.put("attachmentFileName", ctf.getAttachmentFileName());
            map.put("hasAttachment", ctf.hasAttachment());
            if (ctf.hasAttachment()) {
                Path baseDir = Path.of("contest_data", "attachments").toAbsolutePath().normalize();
                Path p = baseDir.resolve(ctf.getAttachmentFileName()).normalize();
                if (p.startsWith(baseDir) && Files.exists(p)) {
                    try {
                        map.put("attachmentSize", Files.size(p));
                    } catch (Exception ignored) {}
                }
            }
        } else if (c instanceof CPProblem cp) {
            map.put("timeLimitMs", cp.getTimeLimitMillis());
            map.put("memoryLimitMb", cp.getMemoryLimitMb());
            map.put("hiddenTestCount", cp.getHiddenTestCount());

            List<Map<String, String>> samples = new ArrayList<>();
            for (CPTestCase tc : cp.getPublicSampleCases()) {
                samples.add(Map.of("input", tc.input(), "output", tc.expectedOutput()));
            }
            map.put("sampleCases", samples);
        }

        if (user != null) {
            map.put("solved", user.isSolved(c.getId()));
        } else if (teamId != null) {
            map.put("solved", engine.isSolvedByTeam(teamId, c.getId()));
        } else {
            map.put("solved", false);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseBody(Context ctx) {
        try {
            Map<String, Object> raw = mapper.readValue(ctx.body(), Map.class);
            Map<String, String> normalized = new java.util.HashMap<>();
            raw.forEach((k, v) -> {
                if (v != null) normalized.put(k, String.valueOf(v));
            });
            return normalized;
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private static String requireField(Map<String, String> body, String key) {
        String value = body.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value.trim();
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
