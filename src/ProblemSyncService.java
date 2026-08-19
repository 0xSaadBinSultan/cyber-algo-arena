import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Multi-Platform Problemset Importer and Synchronization Hub.
 * Manages ingestion of challenges from Codeforces, AtCoder, CodeChef, and Security Puzzle libraries.
 */
public final class ProblemSyncService {

    private final CodeforcesSyncService codeforcesSyncService;
    private final SecurityPuzzleSyncService securityPuzzleSyncService;

    public ProblemSyncService() {
        this.codeforcesSyncService = new CodeforcesSyncService();
        this.securityPuzzleSyncService = new SecurityPuzzleSyncService();
    }

    public record SyncResult(String platform, int syncedCount, List<String> problemIds) {}

    /**
     * Sync Codeforces problems by count and rating.
     */
    public SyncResult syncCodeforces(ContestEngine engine, int count, int minRating, int maxRating) {
        CodeforcesSyncService.SyncResult res = codeforcesSyncService.sync(engine, count, minRating, maxRating);
        List<String> ids = res.problems().stream().map(m -> String.valueOf(m.get("id"))).toList();
        return new SyncResult("CODEFORCES", res.syncedCount(), ids);
    }

    /**
     * Sync Security exercises by category.
     */
    public SyncResult syncSecurityExercises(ContestEngine engine, String category) {
        SecurityPuzzleSyncService.SyncResult res = securityPuzzleSyncService.sync(engine, category);
        List<String> ids = res.problems().stream().map(m -> String.valueOf(m.get("id"))).toList();
        return new SyncResult("SECURITY_EXERCISES", res.syncedCount(), ids);
    }

    /**
     * Ingest curated AtCoder beginner and intermediate algorithmic challenges.
     */
    public SyncResult syncAtCoder(ContestEngine engine) {
        List<CPTemplate> atcoderProblems = List.of(
                new CPTemplate("AC-ABC365-A", "Leap Year Calculation", 100, Challenge.Difficulty.EASY, 1000L, 256,
                        "Determine if year Y is a leap year (divisible by 4, not 100 unless 400).",
                        "2024\n", "366\n"),
                new CPTemplate("AC-ABC365-B", "Second Best Number", 150, Challenge.Difficulty.EASY, 1000L, 256,
                        "Find the 1-based index of the second maximum element in array A.",
                        "4\n8 2 5 1\n", "3\n"),
                new CPTemplate("AC-ABC366-C", "Balls and Bag Query", 250, Challenge.Difficulty.MEDIUM, 2000L, 256,
                        "Maintain a multiset under insert, delete, and unique count queries.",
                        "4\n1 3\n1 5\n1 3\n3\n", "2\n"),
                new CPTemplate("AC-ARC180-A", "ABA and BAB Substring Operations", 350, Challenge.Difficulty.HARD, 2000L, 512,
                        "Count number of distinct strings reachable by replacing ABA with A and BAB with B.",
                        "5\nABABA\n", "4\n")
        );

        return ingestCpTemplates(engine, "ATCODER", atcoderProblems);
    }

    /**
     * Ingest curated CodeChef Starter/Cook-Off algorithmic challenges.
     */
    public SyncResult syncCodeChef(ContestEngine engine) {
        List<CPTemplate> codechefProblems = List.of(
                new CPTemplate("CC-START150-A", "Equal Distribution", 100, Challenge.Difficulty.EASY, 1000L, 256,
                        "Alice has A candies and Bob has B candies. Determine if total candies can be equally split.",
                        "4 6\n", "YES\n"),
                new CPTemplate("CC-START150-B", "Alternating Binary String", 200, Challenge.Difficulty.MEDIUM, 1500L, 256,
                        "Find minimum operations to transform binary string into an alternating pattern.",
                        "4\n1001\n", "1\n"),
                new CPTemplate("CC-START150-C", "Array Elimination Game", 300, Challenge.Difficulty.HARD, 2000L, 256,
                        "Choose integer K such that all bits in array can be zeroed out in steps of K elements.",
                        "3\n13 7 25\n", "1 2\n")
        );

        return ingestCpTemplates(engine, "CODECHEF", codechefProblems);
    }

    private SyncResult ingestCpTemplates(ContestEngine engine, String platform, List<CPTemplate> templates) {
        int added = 0;
        List<String> ids = new ArrayList<>();

        for (CPTemplate t : templates) {
            try {
                engine.getChallenge(t.id());
            } catch (ChallengeNotFoundException ex) {
                Path tcDir = Path.of("contest_data", "testcases", t.id());
                try {
                    Files.createDirectories(tcDir);
                    Files.writeString(tcDir.resolve("input_1.txt"), t.sampleInput(), StandardCharsets.UTF_8);
                    Files.writeString(tcDir.resolve("output_1.txt"), t.sampleOutput(), StandardCharsets.UTF_8);
                } catch (IOException ignored) {}

                CPProblem cp = new CPProblem(t.id(), t.title(), t.basePoints(), t.difficulty(), t.timeLimitMs(), t.memoryLimitMb(), tcDir);
                cp.setDescription("[" + platform + "] " + t.description());
                engine.addChallenge(cp);
                added++;
                ids.add(t.id());
            }
        }
        return new SyncResult(platform, added, ids);
    }

    private record CPTemplate(String id, String title, int basePoints, Challenge.Difficulty difficulty,
                              long timeLimitMs, int memoryLimitMb, String description,
                              String sampleInput, String sampleOutput) {}
}
