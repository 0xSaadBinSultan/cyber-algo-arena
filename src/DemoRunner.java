import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Standalone executable simulating a full Cyber-Algo Arena contest lifecycle
 * programmatically, without manual input.
 *
 * Lifecycle:
 *   1. Clean data directory setup
 *   2. Register 1 admin, 2 teams (2 users each)
 *   3. Admin creates 1 CTF challenge + 1 CP problem dynamically
 *   4. Sequential submissions: wrong attempts, hint deductions, successful solves
 *   5. Prints final ranked leaderboard
 *   6. Asserts data consistency across memory and CSV files
 */
public final class DemoRunner {

    private static final Path DATA_DIR = Path.of("contest_data_demo");
    private static final Path CHALLENGES_CSV = DATA_DIR.resolve("challenges.csv");
    private static final Path TESTCASE_DIR = DATA_DIR.resolve("testcases").resolve("DEMO-CP01");

    private static int assertions = 0;
    private static int passed = 0;

    private DemoRunner() {
    }

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║       Cyber-Algo Arena — DemoRunner v1.0        ║");
        System.out.println("║   Full Lifecycle Simulation & Verification      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // Phase 0: Clean setup
        setupDataDirectory();

        FileIOManager fileIO = new FileIOManager(CHALLENGES_CSV);
        ContestEngine engine = new ContestEngine(fileIO);
        engine.load();

        // Phase 1: Register teams and users
        section("Phase 1: Registration");
        Team teamAlpha = new Team("T-ALPHA", "Alpha Squad");
        Team teamBravo = new Team("T-BRAVO", "Bravo Force");
        engine.registerTeam(teamAlpha);
        engine.registerTeam(teamBravo);
        System.out.println("  Registered teams: Alpha Squad, Bravo Force");

        User admin = engine.registerUserAccount("U-ADMIN", "admin", "admin123", User.Role.ADMIN, null);
        User alice = engine.registerUserAccount("U-ALICE", "alice", "pass_alice", User.Role.PLAYER, "T-ALPHA");
        User bob = engine.registerUserAccount("U-BOB", "bob", "pass_bob", User.Role.PLAYER, "T-ALPHA");
        User carol = engine.registerUserAccount("U-CAROL", "carol", "pass_carol", User.Role.PLAYER, "T-BRAVO");
        User dave = engine.registerUserAccount("U-DAVE", "dave", "pass_dave", User.Role.PLAYER, "T-BRAVO");
        System.out.println("  Registered admin: admin");
        System.out.println("  Alpha Squad: alice, bob");
        System.out.println("  Bravo Force: carol, dave");

        // Verify authentication
        assertTrue("Admin authenticates", engine.authenticate("admin", "admin123").isPresent());
        assertTrue("Alice authenticates", engine.authenticate("alice", "pass_alice").isPresent());
        assertTrue("Wrong password rejected", engine.authenticate("alice", "wrong").isEmpty());

        // Phase 2: Admin creates challenges
        section("Phase 2: Admin Challenge Creation");

        setupAttachments();
        CTFChallenge ctfDemo = engine.addCtfChallenge(
                "DEMO-CTF01",
                "Demo Flag Hunt",
                200,
                Challenge.Difficulty.MEDIUM,
                "CRYPTO",
                "flag{demo_secret_2025}",
                25,
                "demo_packet.pcap");
        System.out.println("  Created CTF: " + ctfDemo.getId()
                + " [cat=" + ctfDemo.getCategory() + ", attachment=" + ctfDemo.getAttachmentFileName() + "]");
        assertTrue("CTF has attachment", ctfDemo.hasAttachment());
        assertTrue("CTF category is CRYPTO", ctfDemo.getCategory() == CTFChallenge.Category.CRYPTO);

        setupCpTestcases();
        CPProblem cpDemo = engine.addCpChallenge(
                "DEMO-CP01",
                "Demo Sum Checker",
                150,
                Challenge.Difficulty.EASY,
                5000L,
                256,
                TESTCASE_DIR);
        System.out.println("  Created CP: " + cpDemo.getId()
                + " [testcases=" + TESTCASE_DIR + "]");

        assertTrue("CTF challenge registered", engine.getChallenge("DEMO-CTF01") != null);
        assertTrue("CP challenge registered", engine.getChallenge("DEMO-CP01") != null);
        assertTrue("Total challenges = 2", engine.getChallenges().size() == 2);

        // Phase 3: Submissions — wrong attempts, hints, correct solves
        section("Phase 3: Submission Simulation");

        // 3a: Alice submits wrong CTF flag
        System.out.println("  [Alice] Submitting wrong CTF flag...");
        Submission wrongSub = new Submission(
                "SUB-0001", alice.getId(), "T-ALPHA", "DEMO-CTF01",
                "flag{wrong_guess}", 0, 0, Instant.now());
        SubmissionResult wrongResult = engine.submit(wrongSub);
        System.out.println("    Result: " + wrongResult.getStatus() + " (expected WRONG_ANSWER)");
        assertTrue("Wrong flag → WRONG_ANSWER",
                wrongResult.getStatus() == SubmissionResult.Status.WRONG_ANSWER);

        // 3b: Alice requests hint for CTF (costs 25 pts)
        System.out.println("  [Alice] Requesting CTF hint (cost: 25 pts)...");
        String hint = engine.requestHint("T-ALPHA", "DEMO-CTF01");
        System.out.println("    Hint received: " + hint);
        assertTrue("Hint usage tracked",
                engine.getHintUsageCount("T-ALPHA", "DEMO-CTF01") == 1);

        // 3c: Alice submits correct CTF flag (with 1 wrong attempt + 1 hint)
        System.out.println("  [Alice] Submitting correct CTF flag...");
        Submission correctCtfSub = new Submission(
                "SUB-0002", alice.getId(), "T-ALPHA", "DEMO-CTF01",
                "flag{demo_secret_2025}", 0, 0, Instant.now());
        SubmissionResult correctCtfResult = engine.submit(correctCtfSub);
        System.out.println("    Result: " + correctCtfResult.getStatus()
                + " | Points: " + correctCtfResult.getPointsAwarded());
        assertTrue("Correct flag → ACCEPTED",
                correctCtfResult.getStatus() == SubmissionResult.Status.ACCEPTED);
        assertTrue("Points awarded > 0", correctCtfResult.getPointsAwarded() > 0);
        assertTrue("Alpha solved CTF",
                engine.isSolvedByTeam("T-ALPHA", "DEMO-CTF01"));

        // 3d: Carol submits wrong CP answer
        System.out.println("  [Carol] Submitting wrong CP answer...");
        Path wrongCpDir = DATA_DIR.resolve("candidate_wrong");
        Files.createDirectories(wrongCpDir);
        Files.writeString(wrongCpDir.resolve("output_1.txt"), "999");
        Submission wrongCpSub = new Submission(
                "SUB-0003", carol.getId(), "T-BRAVO", "DEMO-CP01",
                wrongCpDir.toString(), 0, 0, Instant.now());
        SubmissionResult wrongCpResult = engine.submit(wrongCpSub);
        System.out.println("    Result: " + wrongCpResult.getStatus());
        assertTrue("Wrong CP → WRONG_ANSWER",
                wrongCpResult.getStatus() == SubmissionResult.Status.WRONG_ANSWER);

        // 3e: Carol requests free CP hint
        System.out.println("  [Carol] Requesting CP hint (free)...");
        String cpHint = engine.requestHint("T-BRAVO", "DEMO-CP01");
        System.out.println("    Hint received: " + cpHint);

        // 3f: Carol submits correct CP answer
        System.out.println("  [Carol] Submitting correct CP answer...");
        Path correctCpDir = DATA_DIR.resolve("candidate_correct");
        Files.createDirectories(correctCpDir);
        Files.writeString(correctCpDir.resolve("output_1.txt"), "15");
        Submission correctCpSub = new Submission(
                "SUB-0004", carol.getId(), "T-BRAVO", "DEMO-CP01",
                correctCpDir.toString(), 0, 0, Instant.now());
        SubmissionResult correctCpResult = engine.submit(correctCpSub);
        System.out.println("    Result: " + correctCpResult.getStatus()
                + " | Points: " + correctCpResult.getPointsAwarded());
        assertTrue("Correct CP → ACCEPTED",
                correctCpResult.getStatus() == SubmissionResult.Status.ACCEPTED);
        assertTrue("Bravo solved CP",
                engine.isSolvedByTeam("T-BRAVO", "DEMO-CP01"));

        // 3g: Bob tries to submit for already-solved CTF (team duplicate)
        System.out.println("  [Bob] Attempting already-solved CTF...");
        boolean duplicateRejected = false;
        try {
            Submission dupSub = new Submission(
                    "SUB-0005", bob.getId(), "T-ALPHA", "DEMO-CTF01",
                    "flag{demo_secret_2025}", 0, 0, Instant.now());
            engine.submit(dupSub);
        } catch (InvalidSubmissionException ex) {
            duplicateRejected = true;
            System.out.println("    Correctly rejected: " + ex.getMessage());
        }
        assertTrue("Duplicate team solve rejected", duplicateRejected);

        // Phase 4: Leaderboard
        section("Phase 4: Final Leaderboard");
        engine.refreshLeaderboard();
        CLIController.printLeaderboard(engine);

        // Phase 5: CSV consistency verification
        section("Phase 5: CSV Data Consistency Verification");
        engine.syncData();

        // Reload from CSV in a fresh engine and compare
        FileIOManager freshIO = new FileIOManager(CHALLENGES_CSV);
        ContestEngine freshEngine = new ContestEngine(freshIO);
        freshEngine.load();

        assertTrue("Challenges persisted",
                freshEngine.getChallenges().size() == engine.getChallenges().size());
        assertTrue("Users persisted",
                freshEngine.getUsers().size() == engine.getUsers().size());
        assertTrue("Teams persisted",
                freshEngine.getTeams().size() == engine.getTeams().size());
        assertTrue("Submissions persisted",
                freshEngine.getSubmissions().size() == engine.getSubmissions().size());

        // Verify team scores match after reload
        Team reloadedAlpha = freshEngine.getTeam("T-ALPHA");
        Team reloadedBravo = freshEngine.getTeam("T-BRAVO");
        assertTrue("Alpha score matches after reload",
                reloadedAlpha.getTotalScore() == engine.getTeam("T-ALPHA").getTotalScore());
        assertTrue("Bravo score matches after reload",
                reloadedBravo.getTotalScore() == engine.getTeam("T-BRAVO").getTotalScore());

        // Verify solved status persisted
        assertTrue("Alpha CTF solve persisted",
                freshEngine.isSolvedByTeam("T-ALPHA", "DEMO-CTF01"));
        assertTrue("Bravo CP solve persisted",
                freshEngine.isSolvedByTeam("T-BRAVO", "DEMO-CP01"));

        // Verify attachment persisted
        CTFChallenge reloadedCtf = (CTFChallenge) freshEngine.getChallenge("DEMO-CTF01");
        assertTrue("Reloaded CTF retains attachment", reloadedCtf.hasAttachment());
        assertTrue("Reloaded CTF category is CRYPTO", reloadedCtf.getCategory() == CTFChallenge.Category.CRYPTO);

        // Verify CSV files exist
        assertTrue("challenges.csv exists", Files.exists(CHALLENGES_CSV));
        assertTrue("users.csv exists", Files.exists(DATA_DIR.resolve("users.csv")));
        assertTrue("teams.csv exists", Files.exists(DATA_DIR.resolve("teams.csv")));
        assertTrue("submissions.csv exists", Files.exists(DATA_DIR.resolve("submissions.csv")));

        // Print CSV file sizes
        System.out.println("  CSV file sizes:");
        for (String name : new String[]{"challenges.csv", "users.csv", "teams.csv", "submissions.csv"}) {
            long size = Files.size(DATA_DIR.resolve(name));
            System.out.println("    " + name + ": " + size + " bytes");
        }

        // Final summary
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.printf("║  DEMO COMPLETE: %d/%d assertions passed           ║%n", passed, assertions);
        System.out.println("╚══════════════════════════════════════════════════╝");

        if (passed != assertions) {
            System.err.println("FAILURE: " + (assertions - passed) + " assertion(s) failed.");
            System.exit(1);
        }
    }

    // ═══════════════════════════════════════════════════════
    // Setup helpers
    // ═══════════════════════════════════════════════════════

    private static void setupDataDirectory() throws IOException {
        // Clean any previous demo data
        if (Files.isDirectory(DATA_DIR)) {
            Files.walk(DATA_DIR)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        }
        Files.createDirectories(DATA_DIR);
        System.out.println("[Setup] Clean data directory: " + DATA_DIR.toAbsolutePath());
    }

    private static void setupAttachments() throws IOException {
        Path attachDir = DATA_DIR.resolve("attachments");
        Files.createDirectories(attachDir);
        Files.writeString(attachDir.resolve("demo_packet.pcap"), "PCAP_SAMPLE_PACKET_DUMP_DATA\n", StandardCharsets.UTF_8);
        System.out.println("  Attachment created in " + attachDir);
    }

    private static void setupCpTestcases() throws IOException {
        Files.createDirectories(TESTCASE_DIR);
        // Single testcase: input "5 10" → expected output "15"
        Files.writeString(TESTCASE_DIR.resolve("input_1.txt"), "5 10\n", StandardCharsets.UTF_8);
        Files.writeString(TESTCASE_DIR.resolve("output_1.txt"), "15\n", StandardCharsets.UTF_8);
        System.out.println("  Testcase files created in " + TESTCASE_DIR);
    }

    // ═══════════════════════════════════════════════════════
    // Assertion & output helpers
    // ═══════════════════════════════════════════════════════

    private static void assertTrue(String label, boolean condition) {
        assertions++;
        if (condition) {
            passed++;
            System.out.println("  ✓ " + label);
        } else {
            System.out.println("  ✗ FAIL: " + label);
        }
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("━━━ " + title + " ━━━");
    }
}
