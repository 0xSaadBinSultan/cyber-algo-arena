import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

/**
 * Cloud-based CP Judge execution engine using the free Piston API (https://emkc.org/api/v2/piston).
 * Executes source code against testcases in an isolated sandbox.
 */
public final class PistonJudgeEngine {

    private static final String PISTON_ENDPOINT = "https://emkc.org/api/v2/piston/execute";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public PistonJudgeEngine() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
        this.mapper = new ObjectMapper();
    }

    public record ExecutionResult(
            SubmissionResult.Status status,
            String message,
            int testcasesPassed,
            int totalTestcases
    ) {}

    /**
     * Judges source code or output payload against CP problem testcases.
     */
    public ExecutionResult judge(CPProblem problem, String payload) {
        Path testDir = problem.getTestcaseDirectory();

        // 1. Gather all input_*.txt files
        List<Path> inputs = new ArrayList<>();
        if (Files.exists(testDir) && Files.isDirectory(testDir)) {
            try (var stream = Files.list(testDir)) {
                inputs = stream.filter(p -> p.getFileName().toString().matches("input_\\d+\\.txt"))
                        .sorted(Comparator.comparing(Path::getFileName))
                        .toList();
            } catch (IOException ignored) {}
        }

        // Fallback dummy testcase if directory is empty
        if (inputs.isEmpty()) {
            return new ExecutionResult(SubmissionResult.Status.ACCEPTED, "Solution evaluated (No external testcases configured)", 1, 1);
        }

        // 2. Determine if payload is Source Code or Directory Path
        if (isSourceCode(payload)) {
            return executeSourceCode(problem, payload, inputs);
        } else {
            // Local directory evaluation
            try {
                boolean passed = problem.evaluate(payload);
                if (passed) {
                    return new ExecutionResult(SubmissionResult.Status.ACCEPTED, "All " + inputs.size() + " testcases passed", inputs.size(), inputs.size());
                } else {
                    return new ExecutionResult(SubmissionResult.Status.WRONG_ANSWER, "Output mismatch against testcase suite", 0, inputs.size());
                }
            } catch (Exception ex) {
                return new ExecutionResult(SubmissionResult.Status.INVALID, "Evaluation error: " + ex.getMessage(), 0, inputs.size());
            }
        }
    }

    private ExecutionResult executeSourceCode(CPProblem problem, String sourceCode, List<Path> inputs) {
        DetectedLanguage lang = detectLanguage(sourceCode);
        int total = inputs.size();
        int passed = 0;

        for (int i = 0; i < total; i++) {
            Path inPath = inputs.get(i);
            String outName = inPath.getFileName().toString().replace("input_", "output_");
            Path outPath = inPath.getParent().resolve(outName);

            String stdin = "";
            String expected = "";
            try {
                stdin = Files.readString(inPath, StandardCharsets.UTF_8);
                if (Files.exists(outPath)) {
                    expected = Files.readString(outPath, StandardCharsets.UTF_8).trim();
                }
            } catch (IOException e) {
                return new ExecutionResult(SubmissionResult.Status.INVALID, "Failed reading testcase #" + (i + 1), passed, total);
            }

            // Call Piston API
            try {
                Map<String, Object> reqBody = new LinkedHashMap<>();
                reqBody.put("language", lang.language);
                reqBody.put("version", lang.version);
                reqBody.put("files", List.of(Map.of("name", lang.filename, "content", sourceCode)));
                reqBody.put("stdin", stdin);
                reqBody.put("run_timeout", (int) Math.max(1000, problem.getTimeLimitMillis()));

                String jsonPayload = mapper.writeValueAsString(reqBody);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(PISTON_ENDPOINT))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return new ExecutionResult(SubmissionResult.Status.INVALID, "Judge API communication error: HTTP " + response.statusCode(), passed, total);
                }

                JsonNode root = mapper.readTree(response.body());

                // Check compile error
                JsonNode compile = root.path("compile");
                if (!compile.isMissingNode() && compile.path("code").asInt(0) != 0) {
                    String stderr = compile.path("stderr").asText(compile.path("output").asText("Compilation failed"));
                    return new ExecutionResult(SubmissionResult.Status.COMPILATION_ERROR, "Compilation Error: " + truncate(stderr, 200), passed, total);
                }

                // Check run results
                JsonNode run = root.path("run");
                int exitCode = run.path("code").asInt(0);
                String signal = run.path("signal").asText("");
                String stdout = run.path("stdout").asText("").trim();
                String stderr = run.path("stderr").asText("");

                if ("SIGKILL".equalsIgnoreCase(signal) || run.path("output").asText("").contains("Timed Out")) {
                    return new ExecutionResult(SubmissionResult.Status.TIME_LIMIT_EXCEEDED, "Time Limit Exceeded on testcase #" + (i + 1), passed, total);
                }

                if (exitCode != 0 && !stderr.isBlank()) {
                    return new ExecutionResult(SubmissionResult.Status.RUNTIME_ERROR, "Runtime Error (exit code " + exitCode + "): " + truncate(stderr, 150), passed, total);
                }

                // Compare stdout with expected
                if (!normalize(stdout).equals(normalize(expected))) {
                    return new ExecutionResult(SubmissionResult.Status.WRONG_ANSWER, "Wrong Answer on testcase #" + (i + 1), passed, total);
                }

                passed++;
            } catch (Exception ex) {
                // If cloud API is unreachable, fall back gracefully
                return new ExecutionResult(SubmissionResult.Status.INVALID, "Cloud Judge Error: " + ex.getMessage(), passed, total);
            }
        }

        return new ExecutionResult(SubmissionResult.Status.ACCEPTED, "All " + total + " testcases accepted", passed, total);
    }

    private static boolean isSourceCode(String text) {
        if (text == null) return false;
        String t = text.trim();
        return t.contains("#include") || t.contains("int main(") || t.contains("public class")
                || t.contains("def ") || t.contains("import ") || t.contains("print(") || t.contains("System.out");
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace("\r", "\n").trim();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    private record DetectedLanguage(String language, String version, String filename) {}

    private static DetectedLanguage detectLanguage(String code) {
        if (code.contains("#include") || code.contains("std::") || code.contains("cout <<")) {
            return new DetectedLanguage("cpp", "10.2.0", "solution.cpp");
        } else if (code.contains("public class") || code.contains("class Main")) {
            return new DetectedLanguage("java", "15.0.2", "Main.java");
        } else {
            return new DetectedLanguage("python", "3.10.0", "solution.py");
        }
    }
}
