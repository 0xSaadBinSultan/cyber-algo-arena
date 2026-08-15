import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Role-aware console state machine backed entirely by ContestEngine and CSV persistence. */
public final class CLIController {

    private final ContestEngine engine;
    private final InputHandler input;
    private User currentUser;

    public CLIController(ContestEngine engine, InputHandler input) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
        this.input = Objects.requireNonNull(input, "input must not be null");
    }

    public void start() {
        try {
            engine.load();
        } catch (IOException | CorruptedFileException ex) {
            System.out.println("Data load failed: " + ex.getMessage());
            return;
        }

        boolean running = true;
        while (running) {
            try {
                if (currentUser == null) {
                    running = showUnauthenticatedMenu();
                } else if (currentUser.isAdmin()) {
                    showAdminMenu();
                } else {
                    showPlayerMenu();
                }
            } catch (IllegalStateException ex) {
                System.out.println("Input closed. Goodbye.");
                return;
            } catch (IOException ex) {
                System.out.println("Persistence error: " + ex.getMessage());
            } catch (RuntimeException ex) {
                System.out.println("Error: " + ex.getMessage());
            }
        }
        System.out.println("Goodbye.");
    }

    private boolean showUnauthenticatedMenu() throws IOException {
        System.out.println();
        System.out.println("=== Cyber-Algo Arena ===");
        System.out.println("1. Register user");
        System.out.println("2. Login");
        System.out.println("0. Exit");
        int choice = input.readMenuChoice("Select option: ", 0, 2);
        switch (choice) {
            case 1:
                registerUserFlow();
                return true;
            case 2:
                loginFlow();
                return true;
            case 0:
                return false;
            default:
                throw new IllegalStateException("Unhandled menu choice: " + choice);
        }
    }

    private void registerUserFlow() throws IOException {
        System.out.println("--- Register User ---");
        String username = input.readNonEmpty("Username: ");
        String password = input.readNonEmpty("Password: ");
        String confirmation = input.readNonEmpty("Confirm password: ");
        if (!password.equals(confirmation)) {
            System.out.println("Passwords do not match. Registration cancelled.");
            return;
        }

        int roleChoice = input.readMenuChoice("Role (1 = PLAYER, 2 = ADMIN): ", 1, 2);
        User.Role role = roleChoice == 2 ? User.Role.ADMIN : User.Role.PLAYER;
        String teamId = null;
        if (role == User.Role.PLAYER) {
            printTeams();
            teamId = input.readNonEmpty("Team ID: ");
            try {
                engine.getTeam(teamId);
            } catch (TeamNotFoundException ex) {
                if (!input.readConfirmation("Team not found. Create it?")) {
                    return;
                }
                String teamName = input.readNonEmpty("New team name: ");
                engine.registerTeam(new Team(teamId, teamName));
            }
        }

        User user = engine.registerUserAccount(newId("USER"), username, password, role, teamId);
        System.out.println("Registered " + user.getRole() + " user '" + user.getUsername() + "'.");
    }

    private void loginFlow() {
        System.out.println("--- Login ---");
        String username = input.readNonEmpty("Username: ");
        String password = input.readNonEmpty("Password: ");
        currentUser = engine.authenticate(username, password).orElse(null);
        System.out.println(currentUser == null
                ? "Invalid username or password."
                : "Logged in as " + currentUser.getUsername() + " (" + currentUser.getRole() + ").");
    }

    private void showPlayerMenu() throws IOException {
        System.out.println();
        System.out.println("=== Player Dashboard: " + currentUser.getUsername() + " ===");
        System.out.println("1. View challenge catalog");
        System.out.println("2. View details / request hint");
        System.out.println("3. Submit solution");
        System.out.println("4. View leaderboard");
        System.out.println("0. Logout");
        int choice = input.readMenuChoice("Select option: ", 0, 4);
        switch (choice) {
            case 1:
                printChallengeCatalog();
                break;
            case 2:
                challengeDetailsFlow();
                break;
            case 3:
                submitSolutionFlow();
                break;
            case 4:
                printLeaderboard(engine);
                break;
            case 0:
                currentUser = null;
                break;
            default:
                throw new IllegalStateException("Unhandled menu choice: " + choice);
        }
    }

    private void printChallengeCatalog() {
        String teamId = currentUser.getTeamId();
        List<Challenge> challenges = sortedChallenges();
        System.out.println();
        System.out.printf("%-10s %-12s %-28s %-10s %-7s %-7s%n", "ID", "TYPE", "TITLE", "DIFFICULTY", "POINTS", "SOLVED");
        System.out.println("-----------------------------------------------------------------------------");
        for (Challenge challenge : challenges) {
            boolean solved = teamId != null && engine.isSolvedByTeam(teamId, challenge.getId());
            System.out.printf(
                    "%-10s %-12s %-28s %-10s %-7d %-7s%n",
                    truncate(challenge.getId(), 10),
                    truncate(typeLabel(challenge), 12),
                    truncate(challenge.getTitle(), 28),
                    challenge.getDifficulty(),
                    challenge.getBasePoints(),
                    solved ? "YES" : "NO");
        }
    }

    private void challengeDetailsFlow() {
        Challenge challenge = engine.getChallenge(input.readNonEmpty("Challenge ID: "));
        System.out.println("ID: " + challenge.getId());
        System.out.println("Title: " + challenge.getTitle());
        System.out.println("Type: " + typeLabel(challenge));
        System.out.println("Difficulty: " + challenge.getDifficulty());
        System.out.println("Base points: " + challenge.getBasePoints());
        printSubtypeDetails(challenge);

        String teamId = currentUser.getTeamId();
        if (teamId == null) {
            System.out.println("Join a team before requesting hints.");
            return;
        }
        int cost = challenge.getHintCost();
        String prompt = cost > 0
                ? "Reveal hint for " + cost + " points?"
                : "Reveal free CP hint?";
        if (input.readConfirmation(prompt)) {
            String hint = engine.requestHint(teamId, challenge.getId());
            System.out.println("Hint: " + hint);
            if (cost > 0) {
                System.out.println("Hints used for this challenge: "
                        + engine.getHintUsageCount(teamId, challenge.getId()));
            }
        }
    }

    private void submitSolutionFlow() throws IOException {
        String teamId = currentUser.getTeamId();
        if (teamId == null) {
            System.out.println("Join a team before submitting.");
            return;
        }
        Challenge challenge = engine.getChallenge(input.readNonEmpty("Challenge ID: "));
        if (engine.isSolvedByTeam(teamId, challenge.getId())) {
            System.out.println("Challenge already solved by your team.");
            return;
        }

        String payload;
        if (challenge instanceof CTFChallenge) {
            payload = input.readNonEmpty("Flag: ");
        } else if (challenge instanceof CPProblem) {
            payload = readCpSubmissionPayload((CPProblem) challenge);
        } else {
            throw new IllegalStateException("Unsupported challenge subtype: " + challenge.getClass().getName());
        }

        Submission submission = new Submission(
                newId("SUB"),
                currentUser.getId(),
                teamId,
                challenge.getId(),
                payload,
                0,
                engine.getHintUsageCount(teamId, challenge.getId()),
                Instant.now());
        SubmissionResult result = engine.submit(submission);
        System.out.println("Status: " + result.getStatus());
        System.out.println("Points awarded: " + result.getPointsAwarded());
        System.out.println("Message: " + result.getMessage());
        System.out.println("Team score: " + engine.getTeam(teamId).getTotalScore());
    }

    private String readCpSubmissionPayload(CPProblem problem) throws IOException {
        System.out.println("CP submission accepts a candidate output directory or inline testcase outputs.");
        int mode = input.readMenuChoice("Mode (1 = directory, 2 = inline outputs): ", 1, 2);
        if (mode == 1) {
            return input.readNonEmpty("Candidate output directory: ");
        }

        int testcaseCount = countExpectedOutputs(problem);
        Path candidateDirectory = Files.createTempDirectory("cyber-algo-cp-");
        for (int index = 1; index <= testcaseCount; index++) {
            String output = input.readNonEmpty("Output for test " + index + " (use \\n for line breaks): ");
            Files.writeString(
                    candidateDirectory.resolve("output_" + index + ".txt"),
                    output.replace("\\n", System.lineSeparator()),
                    StandardCharsets.UTF_8);
        }
        return candidateDirectory.toString();
    }

    private void showAdminMenu() throws IOException {
        System.out.println();
        System.out.println("=== Admin Dashboard: " + currentUser.getUsername() + " ===");
        System.out.println("1. Add CTF challenge");
        System.out.println("2. Add CP challenge");
        System.out.println("3. Update challenge points");
        System.out.println("4. Remove challenge");
        System.out.println("5. View submission logs");
        System.out.println("6. Force leaderboard refresh + CSV sync");
        System.out.println("0. Logout");
        int choice = input.readMenuChoice("Select option: ", 0, 6);
        switch (choice) {
            case 1:
                addCtfFlow();
                break;
            case 2:
                addCpFlow();
                break;
            case 3:
                updateChallengePointsFlow();
                break;
            case 4:
                removeChallengeFlow();
                break;
            case 5:
                printSubmissionLogs();
                break;
            case 6:
                engine.refreshLeaderboard();
                engine.syncData();
                System.out.println("Leaderboard refreshed. CSV data synchronized.");
                break;
            case 0:
                currentUser = null;
                break;
            default:
                throw new IllegalStateException("Unhandled menu choice: " + choice);
        }
    }

    private void addCtfFlow() throws IOException {
        String id = input.readNonEmpty("Challenge ID: ");
        String title = input.readNonEmpty("Title: ");
        int points = input.readInt("Base points: ");
        Challenge.Difficulty difficulty = readDifficulty();
        String category = input.readNonEmpty("Category: ");
        String rawFlag = input.readNonEmpty("Raw flag (will be SHA-256 hashed): ");
        int hintCost = input.readInt("Hint cost: ");
        engine.addCtfChallenge(id, title, points, difficulty, category, rawFlag, hintCost);
        System.out.println("CTF challenge added: " + id);
    }

    private void addCpFlow() throws IOException {
        String id = input.readNonEmpty("Challenge ID: ");
        String title = input.readNonEmpty("Title: ");
        int points = input.readInt("Base points: ");
        Challenge.Difficulty difficulty = readDifficulty();
        long timeLimit = input.readLong("Time limit (ms): ");
        int memoryLimit = input.readInt("Memory limit (MB): ");
        Path testcaseDirectory = Path.of(input.readNonEmpty("Testcase directory: "));
        engine.addCpChallenge(id, title, points, difficulty, timeLimit, memoryLimit, testcaseDirectory);
        System.out.println("CP challenge added: " + id);
    }

    private void updateChallengePointsFlow() throws IOException {
        printChallengeCatalog();
        String challengeId = input.readNonEmpty("Challenge ID: ");
        int newPoints = input.readInt("New base points: ");
        engine.updateChallengeBasePoints(challengeId, newPoints);
        System.out.println("Challenge points updated: " + challengeId);
    }

    private void removeChallengeFlow() throws IOException {
        printChallengeCatalog();
        String challengeId = input.readNonEmpty("Challenge ID: ");
        if (input.readConfirmation("Remove challenge " + challengeId + "?")) {
            engine.removeChallenge(challengeId);
            System.out.println("Challenge removed: " + challengeId);
        }
    }

    private void printSubmissionLogs() {
        List<Submission> submissions = new ArrayList<>(engine.getSubmissions());
        submissions.sort(Comparator.comparing(Submission::getTimestamp));
        System.out.println();
        System.out.printf("%-14s %-10s %-10s %-22s %-13s %-7s %-7s %-6s%n",
                "SUBMISSION", "TEAM", "CHALLENGE", "TIMESTAMP", "STATUS", "POINTS", "ATTEMPTS", "HINTS");
        System.out.println("------------------------------------------------------------------------------------------------");
        for (Submission submission : submissions) {
            System.out.printf(
                    "%-14s %-10s %-10s %-22s %-13s %-7d %-7d %-6d%n",
                    truncate(submission.getId(), 14),
                    truncate(submission.getTeamId(), 10),
                    truncate(submission.getChallengeId(), 10),
                    submission.getTimestamp(),
                    submission.getStatus(),
                    submission.getResult().getPointsAwarded(),
                    submission.getWrongAttempts(),
                    submission.getHintsUsed());
        }
    }

    static void printLeaderboard(ContestEngine engine) {
        List<Team> ranking = engine.getLeaderboard().getRanking();
        System.out.println();
        System.out.printf("%-6s %-24s %-8s %-10s %-26s%n", "RANK", "TEAM", "SOLVES", "SCORE", "LAST SOLVE");
        System.out.println("--------------------------------------------------------------------------");
        for (int index = 0; index < ranking.size(); index++) {
            Team team = ranking.get(index);
            Instant lastSolve = team.getLastSolveTime();
            System.out.printf(
                    "%-6d %-24s %-8d %-10d %-26s%n",
                    index + 1,
                    truncate(team.getTeamName(), 24),
                    engine.getSolveCount(team.getId()),
                    team.getTotalScore(),
                    lastSolve == null ? "-" : lastSolve.toString());
        }
    }

    private void printTeams() {
        List<Team> teams = new ArrayList<>(engine.getTeams());
        teams.sort(Comparator.comparing(Team::getId));
        System.out.println("Available teams:");
        for (Team team : teams) {
            System.out.println("  " + team.getId() + " - " + team.getTeamName());
        }
    }

    private List<Challenge> sortedChallenges() {
        List<Challenge> challenges = new ArrayList<>(engine.getChallenges());
        challenges.sort(Comparator.comparing(Challenge::getId));
        return challenges;
    }

    private Challenge.Difficulty readDifficulty() {
        while (true) {
            String token = input.readNonEmpty("Difficulty (EASY/MEDIUM/HARD): ");
            try {
                return Challenge.Difficulty.fromToken(token);
            } catch (IllegalArgumentException ex) {
                System.out.println("Enter EASY, MEDIUM, or HARD.");
            }
        }
    }

    private static void printSubtypeDetails(Challenge challenge) {
        if (challenge instanceof CTFChallenge) {
            CTFChallenge ctf = (CTFChallenge) challenge;
            System.out.println("Category: " + ctf.getCategory());
            System.out.println("Hint cost: " + ctf.getHintCost());
        } else if (challenge instanceof CPProblem) {
            CPProblem cp = (CPProblem) challenge;
            System.out.println("Time limit: " + cp.getTimeLimitMillis() + " ms");
            System.out.println("Memory limit: " + cp.getMemoryLimitMb() + " MB");
            System.out.println("Testcase directory: " + cp.getTestcaseDirectory());
        }
    }

    private static String typeLabel(Challenge challenge) {
        if (challenge instanceof CTFChallenge) {
            return "CTF/" + ((CTFChallenge) challenge).getCategory();
        }
        return challenge.getType();
    }

    private static int countExpectedOutputs(CPProblem problem) throws IOException {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(problem.getTestcaseDirectory(), "output_*.txt")) {
            for (Path ignored : stream) {
                count++;
            }
        }
        if (count == 0) {
            throw new InvalidSubmissionException("No expected outputs in " + problem.getTestcaseDirectory());
        }
        return count;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
