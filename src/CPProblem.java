import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Competitive-programming problem backed by input_N.txt / output_N.txt testcase files.
 * Phase 1 compares candidate output files with expected outputs; process execution is engine work.
 */
public final class CPProblem extends Challenge {

    private static final Pattern OUTPUT_FILE_PATTERN = Pattern.compile("output_(\\d+)\\.txt");
    private static final int WRONG_ATTEMPT_PENALTY = 10;
    private static final long TIME_PENALTY_INTERVAL_MILLIS = 10_000L;

    private final long timeLimitMillis;
    private final int memoryLimitMb;
    private final Path testcaseDirectory;

    public CPProblem(
            String id,
            String title,
            int basePoints,
            Difficulty difficulty,
            long timeLimitMillis,
            int memoryLimitMb,
            Path testcaseDirectory) {
        super(id, title, basePoints, difficulty);
        if (timeLimitMillis <= 0) {
            throw new IllegalArgumentException("timeLimitMillis must be positive");
        }
        if (memoryLimitMb <= 0) {
            throw new IllegalArgumentException("memoryLimitMb must be positive");
        }
        this.timeLimitMillis = timeLimitMillis;
        this.memoryLimitMb = memoryLimitMb;
        this.testcaseDirectory = Objects.requireNonNull(testcaseDirectory, "testcaseDirectory must not be null");
    }

    @Override
    public String getType() {
        return "CP";
    }

    @Override
    public String getHintText() {
        return "Inspect paired input/output files, respect time limit " + timeLimitMillis
                + " ms and memory limit " + memoryLimitMb + " MB.";
    }

    @Override
    public int getHintCost() {
        return 0;
    }

    /**
     * Interprets the polymorphic payload as a directory containing candidate output_N.txt files.
     */
    @Override
    public boolean evaluate(String submissionPayload) throws InvalidSubmissionException {
        if (submissionPayload == null || submissionPayload.trim().isEmpty()) {
            throw new InvalidSubmissionException("Candidate output directory must not be null or blank");
        }
        return evaluateOutputs(Path.of(submissionPayload));
    }

    /**
     * Compares every expected output with the same-named candidate output.
     * Whitespace tokenization accepts conventional CP formatting differences.
     */
    public boolean evaluateOutputs(Path candidateOutputDirectory) throws InvalidSubmissionException {
        Path candidateDirectory = Objects.requireNonNull(candidateOutputDirectory, "candidateOutputDirectory must not be null");
        requireDirectory(testcaseDirectory, "testcase");
        requireDirectory(candidateDirectory, "candidate output");

        List<Path> expectedOutputs = listExpectedOutputs();
        if (expectedOutputs.isEmpty()) {
            throw new InvalidSubmissionException("No expected output files found in " + testcaseDirectory);
        }

        try {
            for (Path expectedOutput : expectedOutputs) {
                int index = extractOutputIndex(expectedOutput);
                Path pairedInput = testcaseDirectory.resolve("input_" + index + ".txt");
                Path candidateOutput = candidateDirectory.resolve(expectedOutput.getFileName().toString());

                requireRegularFile(pairedInput, "paired input");
                requireRegularFile(candidateOutput, "candidate output");

                String expected = Files.readString(expectedOutput);
                String actual = Files.readString(candidateOutput);
                if (!outputsMatch(expected, actual)) {
                    return false;
                }
            }
            return true;
        } catch (IOException ex) {
            throw new InvalidSubmissionException("Unable to read testcase or candidate output files", ex);
        }
    }

    /**
     * Applies a deterministic time penalty: one point per full ten seconds elapsed.
     * Solutions beyond the configured time limit receive zero.
     */
    @Override
    public int calculateScore(int wrongAttempts, int hintsUsed, long elapsedMillis) {
        requireNonNegative(wrongAttempts, "wrongAttempts");
        requireNonNegative(hintsUsed, "hintsUsed");
        requireNonNegative(elapsedMillis, "elapsedMillis");
        if (hintsUsed > 0) {
            throw new InvalidSubmissionException("CP problems do not support hint deductions");
        }
        if (elapsedMillis > timeLimitMillis) {
            return 0;
        }

        long penalty = (long) wrongAttempts * WRONG_ATTEMPT_PENALTY
                + elapsedMillis / TIME_PENALTY_INTERVAL_MILLIS;
        return clampScore(getBasePoints() - penalty);
    }

    public boolean isWithinLimits(long elapsedMillis, int memoryUsedMb) {
        requireNonNegative(elapsedMillis, "elapsedMillis");
        requireNonNegative(memoryUsedMb, "memoryUsedMb");
        return elapsedMillis <= timeLimitMillis && memoryUsedMb <= memoryLimitMb;
    }

    @Override
    protected String[] getTypeSpecificCsvFields() {
        return new String[] {
            String.valueOf(timeLimitMillis),
            String.valueOf(memoryLimitMb),
            testcaseDirectory.toString()
        };
    }

    public long getTimeLimitMillis() {
        return timeLimitMillis;
    }

    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    public Path getTestcaseDirectory() {
        return testcaseDirectory;
    }

    private List<Path> listExpectedOutputs() throws InvalidSubmissionException {
        List<Path> outputs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testcaseDirectory, "output_*.txt")) {
            for (Path candidate : stream) {
                if (Files.isRegularFile(candidate) && OUTPUT_FILE_PATTERN.matcher(candidate.getFileName().toString()).matches()) {
                    outputs.add(candidate);
                }
            }
        } catch (IOException ex) {
            throw new InvalidSubmissionException("Unable to list expected outputs in " + testcaseDirectory, ex);
        }
        outputs.sort(Comparator.comparingInt(CPProblem::extractOutputIndex));
        return outputs;
    }

    private static int extractOutputIndex(Path outputFile) {
        Matcher matcher = OUTPUT_FILE_PATTERN.matcher(outputFile.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not an expected output file: " + outputFile);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean outputsMatch(String expected, String actual) {
        String[] expectedTokens = tokenize(expected);
        String[] actualTokens = tokenize(actual);
        if (expectedTokens.length != actualTokens.length) {
            return false;
        }
        for (int i = 0; i < expectedTokens.length; i++) {
            if (!expectedTokens[i].equals(actualTokens[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] tokenize(String output) {
        String normalized = output == null ? "" : output.trim();
        return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
    }

    private static void requireDirectory(Path directory, String description) {
        if (!Files.isDirectory(directory)) {
            throw new InvalidSubmissionException("Missing " + description + " directory: " + directory);
        }
    }

    private static void requireRegularFile(Path file, String description) {
        if (!Files.isRegularFile(file)) {
            throw new InvalidSubmissionException("Missing " + description + " file: " + file);
        }
    }
}
