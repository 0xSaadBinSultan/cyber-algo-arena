import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Orchestrates authentication, registries, challenge CRUD, submissions, scoring, and leaderboard refresh. */
public final class ContestEngine {

    private final FileIOManager fileIOManager;
    private final Map<String, Challenge> challengesById = new LinkedHashMap<>();
    private final Map<String, User> usersById = new LinkedHashMap<>();
    private final Map<String, Team> teamsById = new LinkedHashMap<>();
    private final Map<String, Submission> submissionsById = new LinkedHashMap<>();
    private final Map<String, Integer> hintUsageByTeamChallenge = new LinkedHashMap<>();
    private final Leaderboard leaderboard = new Leaderboard();

    public ContestEngine(FileIOManager fileIOManager) {
        this.fileIOManager = Objects.requireNonNull(fileIOManager, "fileIOManager must not be null");
    }

    /** Loads all registries, rebuilds hint totals from evaluated submissions, and derives ranking. */
    public synchronized void load() throws IOException, CorruptedFileException {
        challengesById.clear();
        usersById.clear();
        teamsById.clear();
        submissionsById.clear();
        hintUsageByTeamChallenge.clear();

        for (Challenge challenge : fileIOManager.loadChallenges()) {
            challengesById.put(challenge.getId(), challenge);
        }
        for (User user : fileIOManager.loadUsers()) {
            usersById.put(user.getId(), user);
        }
        for (Team team : fileIOManager.loadTeams()) {
            teamsById.put(team.getId(), team);
        }
        for (Submission submission : fileIOManager.loadSubmissions()) {
            submissionsById.put(submission.getId(), submission);
            if (submission.getHintsUsed() > 0) {
                hintUsageByTeamChallenge.merge(
                        hintKey(submission.getTeamId(), submission.getChallengeId()),
                        submission.getHintsUsed(),
                        Math::max);
            }
        }
        leaderboard.recalculate(teamsById.values());
    }

    /** Registers an account from a raw password while persisting only its SHA-256 hash. */
    public synchronized User registerUserAccount(
            String userId,
            String username,
            String rawPassword,
            User.Role role,
            String teamId) throws IOException {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("rawPassword must not be null or empty");
        }
        User user = new User(userId, username, CTFChallenge.sha256Hex(rawPassword), role, teamId);
        registerUser(user);
        return user;
    }

    /** Authenticates by username and constant-time SHA-256 digest comparison. */
    public synchronized Optional<User> authenticate(String username, String rawPassword) {
        if (username == null || username.trim().isEmpty() || rawPassword == null) {
            return Optional.empty();
        }
        String submittedHash = CTFChallenge.sha256Hex(rawPassword);
        return usersById.values().stream()
                .filter(user -> user.getUsername().equals(username))
                .filter(user -> MessageDigest.isEqual(
                        user.getPasswordHash().getBytes(StandardCharsets.UTF_8),
                        submittedHash.getBytes(StandardCharsets.UTF_8)))
                .findFirst();
    }

    public synchronized void registerUser(User user) throws IOException {
        Objects.requireNonNull(user, "user must not be null");
        if (usersById.containsKey(user.getId())) {
            throw new IllegalArgumentException("Duplicate user ID: " + user.getId());
        }
        boolean duplicateUsername = usersById.values().stream()
                .anyMatch(existing -> existing.getUsername().equalsIgnoreCase(user.getUsername()));
        if (duplicateUsername) {
            throw new IllegalArgumentException("Duplicate username: " + user.getUsername());
        }
        if (user.getTeamId() != null && !teamsById.containsKey(user.getTeamId())) {
            throw new TeamNotFoundException(user.getTeamId());
        }
        usersById.put(user.getId(), user);
        if (user.getTeamId() != null) {
            teamsById.get(user.getTeamId()).addMember(user.getId());
            saveTeams();
        }
        saveUsers();
    }

    public synchronized void registerTeam(Team team) throws IOException {
        Objects.requireNonNull(team, "team must not be null");
        if (teamsById.containsKey(team.getId())) {
            throw new IllegalArgumentException("Duplicate team ID: " + team.getId());
        }
        teamsById.put(team.getId(), team);
        leaderboard.recalculate(teamsById.values());
        saveTeams();
    }

    public synchronized void assignUserToTeam(String userId, String teamId) throws IOException {
        User user = usersById.get(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        Team team = teamsById.get(teamId);
        if (team == null) {
            throw new TeamNotFoundException(teamId);
        }

        String previousTeamId = user.getTeamId();
        if (previousTeamId != null && teamsById.containsKey(previousTeamId)) {
            teamsById.get(previousTeamId).removeMember(userId);
        }
        user.assignToTeam(teamId);
        team.addMember(userId);
        saveUsers();
        saveTeams();
    }

    /** Adds a validated challenge entity and persists the registry. */
    public synchronized void addChallenge(Challenge challenge) throws IOException {
        Objects.requireNonNull(challenge, "challenge must not be null");
        if (challengesById.containsKey(challenge.getId())) {
            throw new IllegalArgumentException("Duplicate challenge ID: " + challenge.getId());
        }
        challengesById.put(challenge.getId(), challenge);
        saveChallenges();
    }

    /** Admin helper: hashes the raw CTF flag immediately and never persists clear text. */
    public synchronized CTFChallenge addCtfChallenge(
            String id,
            String title,
            int basePoints,
            Challenge.Difficulty difficulty,
            String category,
            String rawFlag,
            int hintCost) throws IOException {
        CTFChallenge challenge = new CTFChallenge(
                id,
                title,
                basePoints,
                difficulty,
                category,
                CTFChallenge.sha256Hex(rawFlag),
                hintCost);
        addChallenge(challenge);
        return challenge;
    }

    /** Admin helper: registers an existing testcase directory as a CP problem. */
    public synchronized CPProblem addCpChallenge(
            String id,
            String title,
            int basePoints,
            Challenge.Difficulty difficulty,
            long timeLimitMillis,
            int memoryLimitMb,
            Path testcaseDirectory) throws IOException {
        if (!Files.isDirectory(testcaseDirectory)) {
            throw new IllegalArgumentException("testcaseDirectory must exist: " + testcaseDirectory);
        }
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

    /** Replaces the challenge with an equivalent subtype carrying updated base points. */
    public synchronized void updateChallengeBasePoints(String challengeId, int newBasePoints) throws IOException {
        Challenge existing = getChallenge(challengeId);
        Challenge updated;
        if (existing instanceof CTFChallenge) {
            CTFChallenge ctf = (CTFChallenge) existing;
            updated = new CTFChallenge(
                    ctf.getId(),
                    ctf.getTitle(),
                    newBasePoints,
                    ctf.getDifficulty(),
                    ctf.getCategory(),
                    ctf.getFlagHash(),
                    ctf.getHintCost());
        } else if (existing instanceof CPProblem) {
            CPProblem cp = (CPProblem) existing;
            updated = new CPProblem(
                    cp.getId(),
                    cp.getTitle(),
                    newBasePoints,
                    cp.getDifficulty(),
                    cp.getTimeLimitMillis(),
                    cp.getMemoryLimitMb(),
                    cp.getTestcaseDirectory());
        } else {
            throw new IllegalStateException("Unsupported challenge subtype: " + existing.getClass().getName());
        }
        challengesById.put(updated.getId(), updated);
        saveChallenges();
    }

    public synchronized void removeChallenge(String challengeId) throws IOException {
        if (challengesById.remove(challengeId) == null) {
            throw new ChallengeNotFoundException(challengeId);
        }
        saveChallenges();
    }

    /** Records a paid CTF hint request. CP hints are informational and free. */
    public synchronized String requestHint(String teamId, String challengeId) {
        getTeam(teamId);
        Challenge challenge = getChallenge(challengeId);
        if (challenge.getHintCost() > 0) {
            hintUsageByTeamChallenge.merge(hintKey(teamId, challengeId), 1, Integer::sum);
        }
        return challenge.getHintText();
    }

    public synchronized int getHintUsageCount(String teamId, String challengeId) {
        return hintUsageByTeamChallenge.getOrDefault(hintKey(teamId, challengeId), 0);
    }

    public synchronized boolean isSolvedByTeam(String teamId, String challengeId) {
        return submissionsById.values().stream()
                .anyMatch(submission -> submission.getTeamId().equals(teamId)
                        && submission.getChallengeId().equals(challengeId)
                        && submission.getStatus() == SubmissionResult.Status.ACCEPTED);
    }

    public synchronized int getSolveCount(String teamId) {
        return getSolvedChallengeIdsForTeam(teamId).size();
    }

    public synchronized Set<String> getSolvedChallengeIdsForTeam(String teamId) {
        Set<String> solvedIds = new LinkedHashSet<>();
        for (Submission submission : submissionsById.values()) {
            if (submission.getTeamId().equals(teamId)
                    && submission.getStatus() == SubmissionResult.Status.ACCEPTED) {
                solvedIds.add(submission.getChallengeId());
            }
        }
        return Collections.unmodifiableSet(solvedIds);
    }

    /**
     * Evaluates one pending submission, derives prior wrong attempts and recorded hints,
     * persists the audit outcome, and refreshes ranking immediately.
     */
    public synchronized SubmissionResult submit(Submission submission) throws IOException {
        Objects.requireNonNull(submission, "submission must not be null");
        if (submissionsById.containsKey(submission.getId())) {
            throw new DuplicateSubmissionException(submission.getId());
        }

        User user = usersById.get(submission.getUserId());
        if (user == null) {
            throw new UserNotFoundException(submission.getUserId());
        }
        Team team = teamsById.get(submission.getTeamId());
        if (team == null) {
            throw new TeamNotFoundException(submission.getTeamId());
        }
        if (!submission.getTeamId().equals(user.getTeamId())) {
            throw new InvalidSubmissionException(
                    "User " + user.getId() + " is not assigned to team " + submission.getTeamId());
        }
        Challenge challenge = challengesById.get(submission.getChallengeId());
        if (challenge == null) {
            throw new ChallengeNotFoundException(submission.getChallengeId());
        }
        if (isSolvedByTeam(submission.getTeamId(), submission.getChallengeId())) {
            throw new InvalidSubmissionException("Challenge already solved by team " + submission.getTeamId());
        }

        int derivedWrongAttempts = countFailedAttempts(submission.getTeamId(), submission.getChallengeId());
        int effectiveWrongAttempts = Math.max(submission.getWrongAttempts(), derivedWrongAttempts);
        int effectiveHints = Math.max(
                submission.getHintsUsed(),
                getHintUsageCount(submission.getTeamId(), submission.getChallengeId()));
        Submission effectiveSubmission = submission.withAttemptAndHintCounts(effectiveWrongAttempts, effectiveHints);

        submissionsById.put(effectiveSubmission.getId(), effectiveSubmission);
        Instant evaluatedAt = Instant.now();
        try {
            boolean accepted = challenge.evaluate(effectiveSubmission.getPayload());
            if (accepted) {
                long elapsedMillis = Math.max(
                        0L,
                        Duration.between(effectiveSubmission.getTimestamp(), evaluatedAt).toMillis());
                int pointsAwarded = challenge.calculateScore(
                        effectiveSubmission.getWrongAttempts(),
                        effectiveSubmission.getHintsUsed(),
                        elapsedMillis);
                effectiveSubmission.markAccepted(pointsAwarded, evaluatedAt);
                team.recordSolve(pointsAwarded, evaluatedAt);
            } else {
                effectiveSubmission.markWrongAnswer(evaluatedAt);
            }
        } catch (InvalidSubmissionException ex) {
            effectiveSubmission.markInvalid(evaluatedAt, ex.getMessage());
        }

        persistSubmissionOutcome(effectiveSubmission);
        leaderboard.recalculate(teamsById.values());
        return effectiveSubmission.getResult();
    }

    public synchronized void refreshLeaderboard() {
        leaderboard.recalculate(teamsById.values());
    }

    /** Forces every managed registry back to CSV storage. */
    public synchronized void syncData() throws IOException {
        saveChallenges();
        saveUsers();
        saveTeams();
        fileIOManager.saveSubmissions(submissionsById.values());
    }

    public synchronized Challenge getChallenge(String challengeId) {
        Challenge challenge = challengesById.get(challengeId);
        if (challenge == null) {
            throw new ChallengeNotFoundException(challengeId);
        }
        return challenge;
    }

    public synchronized User getUser(String userId) {
        User user = usersById.get(userId);
        if (user == null) {
            throw new UserNotFoundException(userId);
        }
        return user;
    }

    public synchronized Team getTeam(String teamId) {
        Team team = teamsById.get(teamId);
        if (team == null) {
            throw new TeamNotFoundException(teamId);
        }
        return team;
    }

    public Leaderboard getLeaderboard() {
        return leaderboard;
    }

    public synchronized Collection<Challenge> getChallenges() {
        return Collections.unmodifiableCollection(challengesById.values());
    }

    public synchronized Collection<User> getUsers() {
        return Collections.unmodifiableCollection(usersById.values());
    }

    public synchronized Collection<Team> getTeams() {
        return Collections.unmodifiableCollection(teamsById.values());
    }

    public synchronized Collection<Submission> getSubmissions() {
        return Collections.unmodifiableCollection(submissionsById.values());
    }

    private int countFailedAttempts(String teamId, String challengeId) {
        int failedAttempts = 0;
        for (Submission submission : submissionsById.values()) {
            if (submission.getTeamId().equals(teamId)
                    && submission.getChallengeId().equals(challengeId)
                    && (submission.getStatus() == SubmissionResult.Status.WRONG_ANSWER
                            || submission.getStatus() == SubmissionResult.Status.INVALID)) {
                failedAttempts++;
            }
        }
        return failedAttempts;
    }

    private void persistSubmissionOutcome(Submission submission) throws IOException {
        saveTeams();
        fileIOManager.appendSubmission(submission);
    }

    private void saveChallenges() throws IOException {
        fileIOManager.saveChallenges(challengesById.values());
    }

    private void saveUsers() throws IOException {
        fileIOManager.saveUsers(usersById.values());
    }

    private void saveTeams() throws IOException {
        fileIOManager.saveTeams(teamsById.values());
    }

    private static String hintKey(String teamId, String challengeId) {
        return teamId + '\u0000' + challengeId;
    }
}
