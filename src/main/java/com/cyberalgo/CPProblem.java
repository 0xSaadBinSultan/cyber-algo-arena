package com.cyberalgo;

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
 * Competitive-programming problem.
 *
 * Production problems keep testcases in MongoDB via {@link CPTestCase}; the legacy
 * input_N.txt/output_N.txt directory remains supported for local development and migration.
 */
public final class CPProblem extends Challenge {

    private static final Pattern OUTPUT_FILE_PATTERN = Pattern.compile("output_(\\d+)\\.txt");
    private static final Pattern INPUT_FILE_PATTERN = Pattern.compile("input_(\\d+)\\.txt");
    private static final int WRONG_ATTEMPT_PENALTY = 10;
    private static final long TIME_PENALTY_INTERVAL_MILLIS = 10_000L;

    private final long timeLimitMillis;
    private final int memoryLimitMb;
    private final Path testcaseDirectory;
    private final List<CPTestCase> testCases;

    public CPProblem(
            String id,
            String title,
            int basePoints,
            Difficulty difficulty,
            long timeLimitMillis,
            int memoryLimitMb,
            Path testcaseDirectory) {
        this(id, title, basePoints, difficulty, timeLimitMillis, memoryLimitMb, testcaseDirectory, List.of());
    }

    public CPProblem(
            String id,
            String title,
            int basePoints,
            Difficulty difficulty,
            long timeLimitMillis,
            int memoryLimitMb,
            Path testcaseDirectory,
            List<CPTestCase> testCases) {
        super(id, title, basePoints, difficulty);
        if (timeLimitMillis <= 0) {
            throw new IllegalArgumentException("timeLimitMillis must be positive");
        }
        if (timeLimitMillis > 30_000L) {
            throw new IllegalArgumentException("timeLimitMillis must be <= 30000");
        }
        if (memoryLimitMb <= 0 || memoryLimitMb > 2048) {
            throw new IllegalArgumentException("memoryLimitMb must be between 1 and 2048");
        }
        this.timeLimitMillis = timeLimitMillis;
        this.memoryLimitMb = memoryLimitMb;
        this.testcaseDirectory = Objects.requireNonNull(testcaseDirectory, "testcaseDirectory must not be null");
        this.testCases = testCases == null ? List.of() : List.copyOf(testCases);
    }

    @Override
    public String getType() {
        return "CP";
    }

    @Override
    public String getHintText() {
        return "Work from the constraints and sample cases. Your solution must fit "
                + timeLimitMillis + " ms and " + memoryLimitMb + " MB.";
    }

    @Override
    public int getHintCost() {
        return 0;
    }

    @Override
    public boolean evaluate(String submissionPayload) throws InvalidSubmissionException {
        if (submissionPayload == null || submissionPayload.trim().isEmpty()) {
            throw new InvalidSubmissionException("Candidate output directory must not be null or blank");
        }
        return evaluateOutputs(Path.of(submissionPayload));
    }

    public List<CPTestCase> getConfiguredTestCases() {
        return testCases;
    }

    /**
     * Returns all judge cases. Mongo-backed cases take precedence; legacy disk cases
     * are loaded only when the database record predates DB-backed hidden tests.
     */
    public List<CPTestCase> getJudgeTestCases() {
        if (!testCases.isEmpty()) {
            return testCases;
        }
        return loadLegacyTestCases();
    }

    public List<CPTestCase> getPublicSampleCases() {
        return getJudgeTestCases().stream().filter(tc -> !tc.hidden()).toList();
    }

    public int getHiddenTestCount() {
        return (int) getJudgeTestCases().stream().filter(CPTestCase::hidden).count();
    }

    public boolean hasJudgeTestCases() {
        return !getJudgeTestCases().isEmpty();
    }

    /**
     * Legacy output-directory evaluator retained for CLI/migration tooling.
     */
    public boolean evaluateOutputs(Path candidateOutputDirectory) throws InvalidSubmissionException {
        Path candidateDirectory = Objects.requireNonNull(candidateOutputDirectory, "candidateOutputDirectory must not be null");
        List<CPTestCase> cases = getJudgeTestCases();
        if (cases.isEmpty()) {
            throw new InvalidSubmissionException("No judge testcases configured for " + getId());
        }
        requireDirectory(candidateDirectory, "candidate output");

        try {
            for (int i = 0; i < cases.size(); i++) {
                Path candidateOutput = candidateDirectory.resolve("output_" + (i + 1) + ".txt");
                requireRegularFile(candidateOutput, "candidate output");
                String actual = Files.readString(candidateOutput);
                if (!outputsMatch(cases.get(i).expectedOutput(), actual)) {
                    return false;
                }
            }
            return true;
        } catch (IOException ex) {
            throw new InvalidSubmissionException("Unable to read candidate output files", ex);
        }
    }

    @Override
    public int calculateScore(int wrongAttempts, int hintsUsed, long elapsedMillis) {
        requireNonNegative(wrongAttempts, "wrongAttempts");
        requireNonNegative(hintsUsed, "hintsUsed");
        requireNonNegative(elapsedMillis, "elapsedMillis");
        if (hintsUsed > 0) {
            throw new InvalidSubmissionException("CP problems do not support hint deductions");
        }
        long penalty = (long) wrongAttempts * WRONG_ATTEMPT_PENALTY
                + elapsedMillis / TIME_PENALTY_INTERVAL_MILLIS;
        return clampScore(getDynamicPoints() - penalty);
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
            testcaseDirectory.toString(),
            ""
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

    private List<CPTestCase> loadLegacyTestCases() {
        if (!Files.isDirectory(testcaseDirectory)) {
            return List.of();
        }

        List<Path> inputs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(testcaseDirectory, "input_*.txt")) {
            for (Path p : stream) {
                if (Files.isRegularFile(p) && INPUT_FILE_PATTERN.matcher(p.getFileName().toString()).matches()) {
                    inputs.add(p);
                }
            }
        } catch (IOException ex) {
            return List.of();
        }

        inputs.sort(Comparator.comparingInt(CPProblem::extractInputIndex));
        List<CPTestCase> loaded = new ArrayList<>();
        for (Path input : inputs) {
            int index = extractInputIndex(input);
            Path output = testcaseDirectory.resolve("output_" + index + ".txt");
            if (!Files.isRegularFile(output)) {
                continue;
            }
            try {
                // Legacy convention: testcase #1 is the public sample, remaining cases are hidden.
                loaded.add(new CPTestCase(
                        Files.readString(input),
                        Files.readString(output),
                        index != 1));
            } catch (IOException ignored) {}
        }
        return List.copyOf(loaded);
    }

    private static int extractInputIndex(Path inputFile) {
        Matcher matcher = INPUT_FILE_PATTERN.matcher(inputFile.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not an input testcase file: " + inputFile);
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
