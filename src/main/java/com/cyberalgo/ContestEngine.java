package com.cyberalgo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance state orchestrator for Cyber-Algo Arena.
 * Backed by MongoRepository for persistent, multi-contest management.
 */
public final class ContestEngine {

    private final MongoRepository repository;
    private final Leaderboard leaderboard;
    private final PistonJudgeEngine pistonJudge;

    private final Map<String, Challenge> challengesById = new ConcurrentHashMap<>();
    private final Map<String, User> usersById = new ConcurrentHashMap<>();
    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();
    private final Map<String, Team> teamsById = new ConcurrentHashMap<>();
    private final Map<String, Team> teamsByName = new ConcurrentHashMap<>();
    private final Map<String, Contest> contestsById = new ConcurrentHashMap<>();
    private final List<Submission> submissions = Collections.synchronizedList(new ArrayList<>());

    private final Map<String, Set<String>> teamSolvedChallenges = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> teamWrongAttempts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> teamHintUsage = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> userWrongAttempts = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Integer>> userHintUsage = new ConcurrentHashMap<>();

    /** Auto-resync interval: keeps multi-instance deployments convergent. */
    private static final long SYNC_INTERVAL_MS = 5_000L;
    private volatile long lastSyncMillis = 0L;

    public ContestEngine(MongoRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.leaderboard = new Leaderboard();
        this.pistonJudge = new PistonJudgeEngine();
    }

    /** Loads all persisted state from MongoDB into memory. */
    public synchronized void load() {
        challengesById.clear();
        usersById.clear();
        usersByUsername.clear();
        teamsById.clear();
        teamsByName.clear();
        contestsById.clear();
        submissions.clear();
        teamSolvedChallenges.clear();
        teamWrongAttempts.clear();
        teamHintUsage.clear();
        userWrongAttempts.clear();
        userHintUsage.clear();

        DatabaseSeeder.seedIfEmpty(repository);

        for (Challenge c : repository.getAllChallenges()) {
            challengesById.put(c.getId(), c);
        }
        for (User u : repository.getAllUsers()) {
            usersById.put(u.getId(), u);
            usersByUsername.put(u.getUsername().toLowerCase(Locale.ROOT), u);
        }
        for (Team t : repository.getAllTeams()) {
            teamsById.put(t.getId(), t);
            teamsByName.put(t.getTeamName().toLowerCase(Locale.ROOT), t);
        }
        for (Contest ct : repository.getAllContests()) {
            contestsById.put(ct.getId(), ct);
        }

        List<Submission> allSubs = repository.getAllSubmissions();
        allSubs.sort(Comparator.comparing(Submission::getTimestamp));
        for (Submission s : allSubs) {
            submissions.add(s);
            String teamId = s.getTeamId();
            String userId = s.getUserId();
            String challengeId = s.getChallengeId();

            if (s.getStatus() == SubmissionResult.Status.ACCEPTED) {
                if (teamId != null && !teamId.isBlank()) {
                    teamSolvedChallenges.computeIfAbsent(teamId, k -> ConcurrentHashMap.newKeySet()).add(challengeId);
                }
            } else if (s.getStatus() != SubmissionResult.Status.PENDING
                    && s.getStatus() != SubmissionResult.Status.INVALID) {
                if (teamId != null && !teamId.isBlank()) {
                    teamWrongAttempts.computeIfAbsent(teamId, k -> new ConcurrentHashMap<>())
                            .merge(challengeId, 1, Integer::sum);
                }
                if (userId != null && !userId.isBlank()) {
                    userWrongAttempts.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                            .merge(challengeId, 1, Integer::sum);
                }
            }
            if (s.getHintsUsed() > 0) {
                if (teamId != null && !teamId.isBlank()) {
                    teamHintUsage.computeIfAbsent(teamId, k -> new ConcurrentHashMap<>())
                            .put(challengeId, s.getHintsUsed());
                }
                if (userId != null && !userId.isBlank()) {
                    userHintUsage.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                            .put(challengeId, s.getHintsUsed());
                }
            }
        }

        refreshLeaderboard();
        lastSyncMillis = System.currentTimeMillis();
        System.out.println("[ContestEngine] Loaded state: " + challengesById.size() + " challenges, "
                + usersById.size() + " users, " + teamsById.size() + " teams, " + submissions.size() + " submissions.");
    }

    /**
     * Reloads state from the database when the in-memory view is older than
     * the sync interval. Keeps every instance (admin page, logged-in players,
     * anonymous visitors) serving consistent challenge data in multi-instance
     * deployments without hammering the database on every request.
     */
    public void syncIfStale() {
        if (System.currentTimeMillis() - lastSyncMillis >= SYNC_INTERVAL_MS) {
            load();
        }
    }

    public boolean isDatabaseReady() {
        return repository.isDatabaseReady();
    }

    // ═══════════════════════════════════════════════════════════
    // AUTHENTICATION & USERS
    // ═══════════════════════════════════════════════════════════

    public synchronized Optional<User> authenticate(String username, String password) {
        if (username == null || password == null) return Optional.empty();
        User user = usersByUsername.get(username.trim().toLowerCase(Locale.ROOT));
        if (user == null) return Optional.empty();
        return user.verifyPassword(password) ? Optional.of(user) : Optional.empty();
    }

    public synchronized User registerUser(String username, String email, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Username and password must not be blank");
        }
        String normUsername = username.trim().toLowerCase(Locale.ROOT);
        if (usersByUsername.containsKey(normUsername)) {
            throw new IllegalArgumentException("Username already taken: " + username);
        }

        String userId = "U-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String passHash = User.hashPassword(password);
        User user = new User(userId, username.trim(), email, passHash, User.Role.PLAYER, null);

        usersById.put(user.getId(), user);
        usersByUsername.put(normUsername, user);
        repository.saveUser(user);
        return user;
    }

    public synchronized User registerUserAccount(String id, String username, String password, User.Role role, String teamId) {
        String normUsername = username.trim().toLowerCase(Locale.ROOT);
        if (usersByUsername.containsKey(normUsername)) {
            throw new IllegalArgumentException("Username already registered: " + username);
        }
        String passHash = User.hashPassword(password);
        User user = new User(id, username, null, passHash, role, teamId);

        usersById.put(user.getId(), user);
        usersByUsername.put(normUsername, user);
        repository.saveUser(user);
        return user;
    }

    public User getUser(String userId) {
        User user = usersById.get(userId);
        if (user == null) throw new UserNotFoundException(userId);
        return user;
    }

    public List<User> getUsers() {
        return List.copyOf(usersById.values());
    }

    // ═══════════════════════════════════════════════════════════
    // TEAM MANAGEMENT (CREATE, JOIN, ROSTER)
    // ═══════════════════════════════════════════════════════════

    public synchronized Team createTeam(String teamName, String rawPassword, String creatorUserId) {
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("Team name cannot be blank");
        }
        String normName = teamName.trim().toLowerCase(Locale.ROOT);
        if (teamsByName.containsKey(normName)) {
            throw new IllegalArgumentException("Team name already exists: " + teamName);
        }

        User creator = getUser(creatorUserId);
        String teamId = "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String passHash = (rawPassword != null && !rawPassword.isBlank()) ? User.hashPassword(rawPassword) : "";

        Team team = new Team(teamId, teamName.trim(), passHash, creatorUserId, List.of(creatorUserId), 0, null, Instant.now());
        teamsById.put(team.getId(), team);
        teamsByName.put(normName, team);
        repository.saveTeam(team);

        creator.setTeamId(team.getId());
        repository.saveUser(creator);
        refreshLeaderboard();
        return team;
    }

    public synchronized Team joinTeam(String teamName, String rawPassword, String userId) {
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("Team name required");
        }
        Team team = teamsByName.get(teamName.trim().toLowerCase(Locale.ROOT));
        if (team == null) {
            team = teamsById.get(teamName.trim());
        }
        if (team == null) {
            throw new TeamNotFoundException("Team not found: " + teamName);
        }

        if (!team.verifyPassword(rawPassword)) {
            throw new IllegalArgumentException("Incorrect team passkey");
        }

        User user = getUser(userId);
        team.addMember(user.getId());
        user.setTeamId(team.getId());

        repository.saveTeam(team);
        repository.saveUser(user);
        return team;
    }

    public synchronized void registerTeam(Team team) {
        Objects.requireNonNull(team, "team must not be null");
        teamsById.put(team.getId(), team);
        teamsByName.put(team.getTeamName().toLowerCase(Locale.ROOT), team);
        repository.saveTeam(team);
        refreshLeaderboard();
    }

    public Team getTeam(String teamId) {
        Team team = teamsById.get(teamId);
        if (team == null) throw new TeamNotFoundException(teamId);
        return team;
    }

    public List<Team> getTeams() {
        return List.copyOf(teamsById.values());
    }

    // ═══════════════════════════════════════════════════════════
    // CONTEST & PARTICIPATION (CTFtime Multi-Team Rules)
    // ═══════════════════════════════════════════════════════════

    public synchronized void registerPlayerInContest(String contestId, String teamId, String userId) {
        // Enforce: Player cannot belong to multiple teams in the same contest
        Optional<ContestParticipation> existing = repository.getParticipation(contestId, userId);
        if (existing.isPresent()) {
            if (!existing.get().getTeamId().equals(teamId)) {
                throw new IllegalStateException("Player already participating under team " + existing.get().getTeamId() + " in contest " + contestId);
            }
            return; // Already registered under this team
        }

        ContestParticipation cp = new ContestParticipation(contestId, teamId, userId, Instant.now());
        repository.recordParticipation(cp);

        Contest contest = contestsById.get(contestId);
        if (contest != null) {
            contest.registerTeam(teamId);
            repository.saveContest(contest);
        }
    }

    public List<Contest> getContests() {
        return repository.getAllContests();
    }

    // ═══════════════════════════════════════════════════════════
    // CHALLENGES (CRUD & ATTACHMENT DELETION)
    // ═══════════════════════════════════════════════════════════

    public synchronized void addChallenge(Challenge challenge) {
        Objects.requireNonNull(challenge, "challenge must not be null");
        challengesById.put(challenge.getId(), challenge);
        repository.saveChallenge(challenge);
    }

    public synchronized CTFChallenge addCtfChallenge(
            String id,
            String title,
            int basePoints,
            Challenge.Difficulty difficulty,
            String category,
            String rawFlag,
            int hintCost,
            String attachmentFileName) {
        CTFChallenge ctf = new CTFChallenge(
                id,
                title,
                basePoints,
                difficulty,
                category,
                CTFChallenge.sha256Hex(rawFlag),
                hintCost,
                attachmentFileName);
        addChallenge(ctf);
        return ctf;
    }

    public synchronized CTFChallenge addCtfChallenge(
            String id,
            String title,
            int basePoints,
            Challenge.Difficulty difficulty,
            String category,
            String rawFlag,
            int hintCost) {
        return addCtfChallenge(id, title, basePoints, difficulty, category, rawFlag, hintCost, null);
    }

    public synchronized CPProblem addCpChallenge(
            String id,
            String title,
            int basePoints,
            Challenge.Difficulty difficulty,
            long timeLimitMillis,
            int memoryLimitMb,
            Path testcaseDirectory) {
        CPProblem problem = new CPProblem(
                id,
                title,
                basePoints,
                difficulty,
                timeLimitMillis,
                memoryLimitMb,
                testcaseDirectory);
        addChallenge(problem);
        return problem;
    }

    public synchronized CPProblem addCpChallenge(
            String id,
            String title,
            int basePoints,
            Challenge.Difficulty difficulty,
            long timeLimitMillis,
            int memoryLimitMb,
            Path testcaseDirectory,
            List<CPTestCase> testCases) {
        CPProblem problem = new CPProblem(
                id,
                title,
                basePoints,
                difficulty,
                timeLimitMillis,
                memoryLimitMb,
                testcaseDirectory,
                testCases);
        addChallenge(problem);
        return problem;
    }

    public synchronized void removeChallenge(String challengeId) {
        Challenge c = challengesById.remove(challengeId);
        if (c == null) throw new ChallengeNotFoundException(challengeId);
        repository.deleteChallenge(challengeId);

        // Clean attachment file from disk if present
        if (c instanceof CTFChallenge ctf && ctf.hasAttachment()) {
            try {
                Path p = Path.of("contest_data", "attachments", ctf.getAttachmentFileName());
                Files.deleteIfExists(p);
            } catch (Exception ignored) {}
        }
    }

    public synchronized void updateChallengeBasePoints(String challengeId, int newBasePoints) {
        Challenge existing = getChallenge(challengeId);
        Challenge updated;
        if (existing instanceof CTFChallenge ctf) {
            updated = new CTFChallenge(
                    ctf.getId(), ctf.getTitle(), newBasePoints, ctf.getDifficulty(),
                    ctf.getCategory(), ctf.getFlagHash(), ctf.getHintCost(), ctf.getAttachmentFileName());
        } else if (existing instanceof CPProblem cp) {
            updated = new CPProblem(
                    cp.getId(), cp.getTitle(), newBasePoints, cp.getDifficulty(),
                    cp.getTimeLimitMillis(), cp.getMemoryLimitMb(), cp.getTestcaseDirectory(), cp.getConfiguredTestCases());
        } else {
            throw new IllegalArgumentException("Unsupported challenge type: " + existing.getClass().getName());
        }
        challengesById.put(challengeId, updated);
        repository.saveChallenge(updated);
    }

    public Challenge getChallenge(String challengeId) {
        Challenge challenge = challengesById.get(challengeId);
        if (challenge == null) throw new ChallengeNotFoundException(challengeId);
        return challenge;
    }

    public List<Challenge> getChallenges() {
        return List.copyOf(challengesById.values());
    }

    // ═══════════════════════════════════════════
    // SUBMISSION EVALUATION & PROFILE UPDATES
    // ═══════════════════════════════════════════

    public synchronized SubmissionResult submit(Submission submission) {
        return submit(submission, null);
    }

    public synchronized SubmissionResult submit(Submission submission, String language) {
        Objects.requireNonNull(submission, "submission must not be null");

        Challenge challenge = getChallenge(submission.getChallengeId());
        User user = getUser(submission.getUserId());
        Team team = null;
        if (submission.getTeamId() != null && !submission.getTeamId().isBlank()) {
            team = getTeam(submission.getTeamId());
        }

        // Club practice is individual-first: every member can solve a problem once,
        // even if another member of the same team already solved it.
        if (user.isSolved(challenge.getId())) {
            throw new DuplicateSubmissionException("Challenge already solved by user " + user.getUsername());
        }

        SubmissionResult.Status status = SubmissionResult.Status.WRONG_ANSWER;
        String outcomeMessage = "Wrong answer";

        if (challenge instanceof CPProblem cp) {
            PistonJudgeEngine.ExecutionResult execRes = pistonJudge.judge(cp, submission.getPayload(), language);
            status = execRes.status();
            outcomeMessage = execRes.message();
        } else {
            try {
                boolean accepted = challenge.evaluate(submission.getPayload());
                status = accepted ? SubmissionResult.Status.ACCEPTED : SubmissionResult.Status.WRONG_ANSWER;
                outcomeMessage = accepted ? "Accepted" : "Wrong answer";
            } catch (InvalidSubmissionException ex) {
                SubmissionResult errResult = new SubmissionResult(SubmissionResult.Status.INVALID, 0, ex.getMessage());
                submission.applyResult(errResult);
                submissions.add(submission);
                repository.saveSubmission(submission);
                return errResult;
            }
        }

        if (status == SubmissionResult.Status.ACCEPTED) {
            String firstBloodOwner = team != null ? team.getId() : "USER:" + user.getId();
            if (!challenge.hasFirstBlood()) {
                challenge.setFirstBlood(firstBloodOwner, user.getId());
            }
            boolean isFirstBlood = user.getId().equals(challenge.getFirstBloodUserId());

            challenge.incrementSolveCount();

            int wrongCount = getUserWrongAttempts(user.getId(), challenge.getId());
            int hintsCount = getUserHintUsageCount(user.getId(), challenge.getId());
            int pointsAwarded = challenge.calculateScore(wrongCount, hintsCount, 0L);

            int firstBloodBonus = 0;
            if (isFirstBlood) {
                firstBloodBonus = Math.max(1, pointsAwarded / 10);
                pointsAwarded += firstBloodBonus;
            }

            String fbTag = isFirstBlood ? " FIRST BLOOD (+" + firstBloodBonus + " bonus)" : "";
            Instant solveTime = submission.getTimestamp();

            // Personal profile always receives the solve/points.
            boolean isCpTrack = challenge instanceof CPProblem;
            String cat = isCpTrack ? "CP" : ((CTFChallenge) challenge).getCategoryName();
            user.recordSolve(challenge.getId(), cat, pointsAwarded, isCpTrack ? "CP" : "CTF");
            repository.saveUser(user);

            // A team receives points for a challenge only once, while every member can
            // still solve it individually for practice/profile tracking.
            if (team != null && !isSolvedByTeam(team.getId(), challenge.getId())) {
                team.applyScore(pointsAwarded, solveTime);
                teamSolvedChallenges.computeIfAbsent(team.getId(), k -> ConcurrentHashMap.newKeySet()).add(challenge.getId());
                repository.saveTeam(team);
            }

            repository.saveChallenge(challenge);

            SubmissionResult acceptResult = new SubmissionResult(
                    SubmissionResult.Status.ACCEPTED,
                    pointsAwarded,
                    outcomeMessage + fbTag);
            submission.applyResult(acceptResult);
            submissions.add(submission);
            repository.saveSubmission(submission);

            refreshLeaderboard();
            return acceptResult;
        }

        if (status != SubmissionResult.Status.INVALID && status != SubmissionResult.Status.PENDING) {
            userWrongAttempts.computeIfAbsent(user.getId(), k -> new ConcurrentHashMap<>())
                    .merge(challenge.getId(), 1, Integer::sum);
            if (team != null) {
                teamWrongAttempts.computeIfAbsent(team.getId(), k -> new ConcurrentHashMap<>())
                        .merge(challenge.getId(), 1, Integer::sum);
            }
        }

        SubmissionResult failResult = new SubmissionResult(status, 0, outcomeMessage);
        submission.applyResult(failResult);
        submissions.add(submission);
        repository.saveSubmission(submission);
        return failResult;
    }

    public synchronized String requestHint(String teamId, String challengeId) {
        getTeam(teamId);
        Challenge challenge = getChallenge(challengeId);
        teamHintUsage.computeIfAbsent(teamId, k -> new ConcurrentHashMap<>())
                .merge(challengeId, 1, Integer::sum);
        return challenge.getHintText();
    }

    public synchronized String requestHintForUser(String userId, String challengeId) {
        getUser(userId);
        Challenge challenge = getChallenge(challengeId);
        userHintUsage.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .merge(challengeId, 1, Integer::sum);
        return challenge.getHintText();
    }

    public boolean isSolvedByTeam(String teamId, String challengeId) {
        Set<String> solved = teamSolvedChallenges.get(teamId);
        return solved != null && solved.contains(challengeId);
    }

    public int getSolveCount(String teamId) {
        Set<String> solved = teamSolvedChallenges.get(teamId);
        return solved != null ? solved.size() : 0;
    }

    public int getWrongAttempts(String teamId, String challengeId) {
        Map<String, Integer> map = teamWrongAttempts.get(teamId);
        return (map != null) ? map.getOrDefault(challengeId, 0) : 0;
    }

    public int getHintUsageCount(String teamId, String challengeId) {
        Map<String, Integer> map = teamHintUsage.get(teamId);
        return (map != null) ? map.getOrDefault(challengeId, 0) : 0;
    }

    public int getUserWrongAttempts(String userId, String challengeId) {
        Map<String, Integer> map = userWrongAttempts.get(userId);
        return (map != null) ? map.getOrDefault(challengeId, 0) : 0;
    }

    public int getUserHintUsageCount(String userId, String challengeId) {
        Map<String, Integer> map = userHintUsage.get(userId);
        return (map != null) ? map.getOrDefault(challengeId, 0) : 0;
    }

    public synchronized void refreshLeaderboard() {
        leaderboard.update(teamsById.values());
    }

    public Leaderboard getLeaderboard() {
        return leaderboard;
    }

    public List<Submission> getSubmissions() {
        return List.copyOf(submissions);
    }

    public MongoRepository getRepository() {
        return repository;
    }

    /**
     * Full state resynchronization from the persistent database.
     * Reloads challenges, users, teams, contests and submissions so that
     * every running instance serves a consistent view even when data was
     * mutated by another instance (multi-instance deployments) or directly
     * in the database.
     */
    public synchronized void syncData() {
        load();
    }
}
