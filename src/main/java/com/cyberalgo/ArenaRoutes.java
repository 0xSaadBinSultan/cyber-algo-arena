package com.cyberalgo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import java.time.Instant;
import java.util.*;

/** Hosting and practice endpoints. Never serializes source, raw flags or hidden testcases. */
final class ArenaRoutes {
    private final ContestEngine engine;
    private final ObjectMapper json = new ObjectMapper();
    ArenaRoutes(Javalin app, ContestEngine engine) {
        this.engine = engine;
        app.post("/api/admin/contests", ctx -> {
            try {
                Map<String, Object> body = json.readValue(ctx.body(), new TypeReference<>() {});
                String title = String.valueOf(body.getOrDefault("title", "")).trim();
                String description = String.valueOf(body.getOrDefault("description", "")).trim();
                if (title.isEmpty() || title.length() > 120 || description.length() > 4000) throw new IllegalArgumentException("Provide a title (up to 120 characters) and a description up to 4000 characters");
                Object ids = body.get("challengeIds");
                if (!(ids instanceof List<?> list) || list.size() > 50) throw new IllegalArgumentException("Select 1 to 50 problems");
                Contest contest = new Contest("ROUND-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(),
                        title, description, Instant.parse(String.valueOf(body.get("startTime"))),
                        Instant.parse(String.valueOf(body.get("endTime"))), true, List.of());
                contest.setChallengeIds(list.stream().map(String::valueOf).toList());
                engine.createContest(contest);
                ctx.status(201).json(Map.of("id", contest.getId(), "message", "Contest published"));
            } catch (Exception ex) { throw new BadRequestResponse(ex.getMessage() == null ? "Invalid contest details" : ex.getMessage()); }
        });
        app.get("/api/contests/{id}/standings", ctx -> {
            Contest contest;
            try { contest = engine.getContest(ctx.pathParam("id")); }
            catch (IllegalArgumentException ex) { ctx.status(404).json(Map.of("error", "Contest not found")); return; }
            Map<String, Map<String, Submission>> solves = new HashMap<>();
            for (Submission s : engine.getSubmissions()) {
                if (!contest.getId().equals(s.getContestId()) || s.getStatus() != SubmissionResult.Status.ACCEPTED) continue;
                solves.computeIfAbsent(s.getTeamId(), k -> new HashMap<>()).putIfAbsent(s.getChallengeId(), s);
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (String teamId : contest.getRegisteredTeamIds()) {
                Map<String, Submission> teamSolves = solves.getOrDefault(teamId, Map.of());
                int score = teamSolves.values().stream().mapToInt(Submission::getPointsAwarded).sum();
                long last = teamSolves.values().stream().map(Submission::getTimestamp).mapToLong(Instant::toEpochMilli).max().orElse(Long.MAX_VALUE);
                String name;
                try { name = engine.getTeam(teamId).getTeamName(); } catch (Exception ex) { name = teamId; }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name); row.put("teamId", teamId); row.put("score", score);
                row.put("solves", teamSolves.size()); row.put("lastSolve", last);
                row.put("solvedChallengeIds", teamSolves.keySet()); rows.add(row);
            }
            rows.sort(Comparator.<Map<String,Object>>comparingInt(r -> (Integer) r.get("score")).reversed()
                    .thenComparingLong(r -> (Long) r.get("lastSolve")).thenComparing(r -> r.get("name").toString()));
            for (int i = 0; i < rows.size(); i++) rows.get(i).put("rank", i + 1);
            ctx.json(rows);
        });
        app.get("/api/standings/practice", ctx -> {
            List<User> users = new ArrayList<>(engine.getUsers());
            users.removeIf(User::isAdmin);
            users.sort(Comparator.comparingInt(User::getPersonalScore).reversed().thenComparing(User::getUsername));
            List<Map<String,Object>> rows = new ArrayList<>();
            for (User u : users) rows.add(Map.of("rank", rows.size()+1, "name", u.getUsername(), "score", u.getPersonalScore(),
                    "solves", u.getSolvesCount(), "cpSolves", u.getCpSolvesCount(), "ctfSolves", u.getCtfSolvesCount()));
            ctx.json(rows);
        });
        app.get("/api/submissions/me", ctx -> {
            User user = auth(ctx);
            List<Map<String,Object>> rows = new ArrayList<>();
            for (Submission s : engine.getSubmissions()) {
                if (!user.getId().equals(s.getUserId())) continue;
                rows.add(Map.of("id", s.getId(), "challengeId", s.getChallengeId(), "contestId", s.getContestId(),
                        "status", s.getStatus().name(), "points", s.getPointsAwarded(), "timestamp", s.getTimestamp().toString(),
                        "message", s.getResultMessage()));
            }
            Collections.reverse(rows);
            ctx.json(rows.stream().limit(100).toList());
        });
        RateLimiter samplesLimiter = new RateLimiter();
        app.post("/api/run-samples", ctx -> {
            User user = auth(ctx);
            if (!samplesLimiter.allow(user.getId(), 6, 60_000)) { ctx.status(429).json(Map.of("error", "Please wait before running more samples")); return; }
            try {
                Map<String,String> body = json.readValue(ctx.body(), new TypeReference<>() {});
                Challenge challenge = engine.getChallenge(body.get("challengeId"));
                if (!(challenge instanceof CPProblem cp) || !cp.isJudgeReady()) throw new IllegalArgumentException("Select a judge-ready CP problem");
                String code = body.getOrDefault("payload", "");
                if (code.length() > 100000) throw new IllegalArgumentException("Source code exceeds 100 KB");
                CPProblem samples = new CPProblem(cp.getId(), cp.getTitle(), cp.getBasePoints(), cp.getDifficulty(),
                        cp.getTimeLimitMillis(), cp.getMemoryLimitMb(), cp.getTestcaseDirectory(), cp.getPublicSampleCases());
                ctx.json(new PistonJudgeEngine().judge(samples, code, body.get("language")));
            } catch (IllegalArgumentException ex) { throw new BadRequestResponse(ex.getMessage()); }
        });
    }
    private User auth(Context ctx) {
        String id = ctx.sessionAttribute("userId");
        if (id == null) throw new UnauthorizedResponse("Sign in to continue");
        return engine.getUser(id);
    }
}
