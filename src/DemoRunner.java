import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Automated lifecycle verification suite for Cyber-Algo Arena against MongoDB.
 */
public final class DemoRunner {

    private static final String TEST_DB = "cyber_algo_arena_test";
    private static final Path ATTACH_DIR = Path.of("contest_data", "attachments");
    private static final Path TESTCASE_DIR = Path.of("contest_data", "testcases", "DEMO-CP01");

    private static int assertions = 0;
    private static int passed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║   Cyber-Algo Arena — MongoDB Test Suite v2.0    ║");
        System.out.println("║   Full Multi-Contest & Team Lifecycle Test      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        try (MongoManager mongo = new MongoManager(MongoManager.DEFAULT_URI, TEST_DB)) {
            // Clean test database
            mongo.getDatabase().drop();
            System.out.println("[Setup] Fresh test database dropped & initialized: " + TEST_DB);

            MongoRepository repo = new MongoRepository(mongo);
            ContestEngine engine = new ContestEngine(repo);
            engine.load();

            // ━━━ Phase 1: Authentication & User Registration ━━━
            section("Phase 1: Authentication & User Registration");

            // Verify seeded admin
            assertTrue("Seeded admin authenticates", engine.authenticate("admin", "admin_password_123").isPresent());

            // Register standard player accounts (no team required initially)
            User alice = engine.registerUser("alice", "alice@test.local", "pass_alice");
            User bob = engine.registerUser("bob", "bob@test.local", "pass_bob");
            User carol = engine.registerUser("carol", "carol@test.local", "pass_carol");

            assertTrue("Alice registered as PLAYER", alice.getRole() == User.Role.PLAYER);
            assertTrue("Alice authenticates", engine.authenticate("alice", "pass_alice").isPresent());
            assertTrue("Wrong password rejected", engine.authenticate("alice", "wrong_pass").isEmpty());

            // ━━━ Phase 2: Team Creation & Join Workflows ━━━
            section("Phase 2: Team Creation & Join Flow");

            // Alice creates team Alpha Squad with password
            Team teamAlpha = engine.createTeam("Alpha Squad", "alpha_secret", alice.getId());
            assertTrue("Team Alpha created", teamAlpha != null);
            assertTrue("Alice is captain of Alpha Squad", teamAlpha.getCaptainUserId().equals(alice.getId()));
            assertTrue("Alice teamId set to Alpha Squad", engine.getUser(alice.getId()).getTeamId().equals(teamAlpha.getId()));

            // Bob joins Alpha Squad with password
            Team joinedAlpha = engine.joinTeam("Alpha Squad", "alpha_secret", bob.getId());
            assertTrue("Bob joined Alpha Squad", joinedAlpha.isMember(bob.getId()));
            assertTrue("Alpha Squad member count = 2", joinedAlpha.getMemberUserIds().size() == 2);

            // Carol creates Bravo Force
            Team teamBravo = engine.createTeam("Bravo Force", "bravo_secret", carol.getId());
            assertTrue("Bravo Force created", teamBravo != null);

            // ━━━ Phase 3: CTFtime Multi-Contest & Team Constraint ━━━
            section("Phase 3: Contest Participation Constraint");

            Contest ctfContest = new Contest("CTF-SPRING-2026", "Spring CTF Championship", "Annual CTF", Instant.now(), Instant.now().plusSeconds(86400), true, List.of());
            repo.saveContest(ctfContest);

            // Alice registers under Alpha Squad in contest
            engine.registerPlayerInContest("CTF-SPRING-2026", teamAlpha.getId(), alice.getId());
            assertTrue("Alice registered under Alpha in contest", true);

            // Attempt: Alice tries to participate under Bravo Force in same contest -> must fail
            boolean duplicateFailed = false;
            try {
                engine.registerPlayerInContest("CTF-SPRING-2026", teamBravo.getId(), alice.getId());
            } catch (IllegalStateException ex) {
                duplicateFailed = true;
            }
            assertTrue("Player cannot join multiple teams in same contest", duplicateFailed);

            // ━━━ Phase 4: Challenges & Attachments ━━━
            section("Phase 4: Challenges & Attachments");

            setupAttachments();
            CTFChallenge ctf = engine.addCtfChallenge(
                    "CTF-DEMO-01",
                    "Crypto Matrix",
                    250,
                    Challenge.Difficulty.MEDIUM,
                    "CRYPTO",
                    "flag{crypto_master_2026}",
                    25,
                    "demo_cipher.txt");

            setupCpTestcases();
            CPProblem cp = engine.addCpChallenge(
                    "CP-DEMO-01",
                    "Dynamic Grid Walk",
                    300,
                    Challenge.Difficulty.HARD,
                    1500L,
                    256,
                    TESTCASE_DIR);

            assertTrue("CTF registered in MongoDB", engine.getChallenge("CTF-DEMO-01") != null);
            assertTrue("CTF has attachment", ctf.hasAttachment());
            assertTrue("CTF category is CRYPTO", ctf.getCategory() == CTFChallenge.Category.CRYPTO);
            assertTrue("CP registered in MongoDB", engine.getChallenge("CP-DEMO-01") != null);

            // ━━━ Phase 5: Submissions, Profiles & Leaderboard ━━━
            section("Phase 5: Submissions, Profiles & Leaderboard");

            // Alice submits wrong flag
            Submission wrongSub = new Submission("SUB-1", "CTF-SPRING-2026", alice.getId(), teamAlpha.getId(), "CTF-DEMO-01", "flag{wrong}", 0, 0, Instant.now());
            SubmissionResult wrongRes = engine.submit(wrongSub);
            assertTrue("Wrong flag -> WRONG_ANSWER", wrongRes.getStatus() == SubmissionResult.Status.WRONG_ANSWER);

            // Alice requests hint
            String hint = engine.requestHint(teamAlpha.getId(), "CTF-DEMO-01");
            assertTrue("Hint unlocked", hint != null && !hint.isBlank());
            assertTrue("Hint count tracked", engine.getHintUsageCount(teamAlpha.getId(), "CTF-DEMO-01") == 1);

            // Alice submits correct flag
            Submission correctSub = new Submission("SUB-2", "CTF-SPRING-2026", alice.getId(), teamAlpha.getId(), "CTF-DEMO-01", "flag{crypto_master_2026}", 1, 1, Instant.now());
            SubmissionResult correctRes = engine.submit(correctSub);
            assertTrue("Correct flag -> ACCEPTED", correctRes.getStatus() == SubmissionResult.Status.ACCEPTED);
            assertTrue("Points awarded with deductions", correctRes.getPointsAwarded() == (250 - 10 - 25)); // 215

            // Check User Profile Stats
            User updatedAlice = engine.getUser(alice.getId());
            assertTrue("Alice personal score updated", updatedAlice.getPersonalScore() == 215);
            assertTrue("Alice solves count = 1", updatedAlice.getSolvesCount() == 1);
            assertTrue("Alice category breakdown contains CRYPTO", updatedAlice.getCategoryBreakdown().getOrDefault("CRYPTO", 0) == 1);
            assertTrue("Alice solved challenge tracked", updatedAlice.isSolved("CTF-DEMO-01"));

            // ━━━ Phase 6: Challenge Deletion ━━━
            section("Phase 6: Challenge Deletion");

            engine.removeChallenge("CTF-DEMO-01");
            boolean notFound = false;
            try {
                engine.getChallenge("CTF-DEMO-01");
            } catch (ChallengeNotFoundException ex) {
                notFound = true;
            }
            assertTrue("Challenge deleted from MongoDB", notFound);
            assertTrue("Attachment file cleaned up from disk", !Files.exists(ATTACH_DIR.resolve("demo_cipher.txt")));

            // Final Summary
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.printf("║  MONGODB TEST COMPLETE: %d/%d assertions passed   ║%n", passed, assertions);
            System.out.println("╚══════════════════════════════════════════════════╝");

            if (passed != assertions) {
                System.err.println("FAILURE: " + (assertions - passed) + " assertion(s) failed.");
                System.exit(1);
            }
        } catch (Exception ex) {
            System.err.println("Fatal test error: " + ex.getMessage());
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void setupAttachments() throws IOException {
        Files.createDirectories(ATTACH_DIR);
        Files.writeString(ATTACH_DIR.resolve("demo_cipher.txt"), "CIPHER_TEXT_ATTACHMENT_SAMPLE\n", StandardCharsets.UTF_8);
    }

    private static void setupCpTestcases() throws IOException {
        Files.createDirectories(TESTCASE_DIR);
        Files.writeString(TESTCASE_DIR.resolve("input_1.txt"), "5 10\n", StandardCharsets.UTF_8);
        Files.writeString(TESTCASE_DIR.resolve("output_1.txt"), "15\n", StandardCharsets.UTF_8);
    }

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
