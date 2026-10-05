package com.cyberalgo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Competitive-programming execution adapter for a Piston-compatible sandbox.
 *
 * Production deployments should point PISTON_URL at a self-hosted/authorized Piston
 * service. Hidden testcase contents never leave the backend except as stdin sent to
 * the isolated judge service.
 */
public final class PistonJudgeEngine {

    private static final String DEFAULT_PISTON_URL = "https://emkc.org/api/v2/piston/execute";
    private static final long MB = 1024L * 1024L;

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI executeEndpoint;

    public PistonJudgeEngine() {
        this(System.getenv("PISTON_URL"));
    }

    PistonJudgeEngine(String executeUrl) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
        this.mapper = new ObjectMapper();
        this.executeEndpoint = URI.create(resolveExecuteUrl(executeUrl));
    }

    public record ExecutionResult(
            SubmissionResult.Status status,
            String message,
            int testcasesPassed,
            int totalTestcases,
            long maxTimeMillis,
            long maxMemoryBytes,
            String language
    ) {}

    public ExecutionResult judge(CPProblem problem, String sourceCode) {
        return judge(problem, sourceCode, null);
    }

    public ExecutionResult judge(CPProblem problem, String sourceCode, String requestedLanguage) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return result(SubmissionResult.Status.INVALID, "Source code is required", 0, 0, 0, 0, "");
        }

        List<CPTestCase> tests = problem.getJudgeTestCases();
        if (tests.isEmpty()) {
            return result(
                    SubmissionResult.Status.INVALID,
                    "Judge misconfigured: no server-side testcases are configured for this problem",
                    0, 0, 0, 0, "");
        }

        DetectedLanguage lang;
        try {
            lang = detectLanguage(sourceCode, requestedLanguage);
        } catch (IllegalArgumentException ex) {
            return result(SubmissionResult.Status.INVALID, ex.getMessage(), 0, tests.size(), 0, 0, "");
        }

        int passed = 0;
        long maxTime = 0L;
        long maxMemory = 0L;
        long memoryLimitBytes = problem.getMemoryLimitMb() * MB;
        int runLimitMs = Math.toIntExact(Math.max(50L, problem.getTimeLimitMillis()));

        for (int i = 0; i < tests.size(); i++) {
            CPTestCase test = tests.get(i);

            try {
                Map<String, Object> reqBody = new LinkedHashMap<>();
                reqBody.put("language", lang.language());
                reqBody.put("version", "*");
                reqBody.put("files", List.of(Map.of(
                        "name", lang.filename(),
                        "content", sourceCode)));
                reqBody.put("stdin", test.input());
                reqBody.put("compile_timeout", 10_000);
                reqBody.put("compile_cpu_time", 10_000);
                reqBody.put("run_timeout", runLimitMs);
                reqBody.put("run_cpu_time", runLimitMs);
                reqBody.put("run_memory_limit", memoryLimitBytes);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(executeEndpoint)
                        .timeout(Duration.ofMillis(Math.max(12_000L, runLimitMs + 11_000L)))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                mapper.writeValueAsString(reqBody),
                                StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    String msg = response.statusCode() == 401 || response.statusCode() == 403
                            ? "Judge service authorization failed. Configure an authorized/self-hosted Piston service."
                            : "Judge service error: HTTP " + response.statusCode();
                    return result(SubmissionResult.Status.INVALID, msg, passed, tests.size(), maxTime, maxMemory, lang.displayName());
                }

                JsonNode root = mapper.readTree(response.body());
                JsonNode compile = root.path("compile");
                if (!compile.isMissingNode() && !compile.isNull()) {
                    String compileStatus = compile.path("status").asText("");
                    Integer compileCode = compile.path("code").isNull() ? null : compile.path("code").asInt();
                    if ("TO".equalsIgnoreCase(compileStatus)) {
                        return result(SubmissionResult.Status.COMPILATION_ERROR,
                                "Compilation timed out", passed, tests.size(), maxTime, maxMemory, lang.displayName());
                    }
                    if ((compileCode != null && compileCode != 0) || "RE".equalsIgnoreCase(compileStatus)) {
                        String details = firstNonBlank(
                                compile.path("stderr").asText(""),
                                compile.path("output").asText(""),
                                "Compilation failed");
                        return result(SubmissionResult.Status.COMPILATION_ERROR,
                                test.hidden() ? "Compilation Error on hidden testcase" : "Compilation Error: " + truncate(details, 700),
                                passed, tests.size(), maxTime, maxMemory, lang.displayName());
                    }
                }

                JsonNode run = root.path("run");
                String runStatus = run.path("status").asText("");
                String signal = run.path("signal").asText("");
                Integer exitCode = run.path("code").isNull() ? null : run.path("code").asInt();
                long wallTime = Math.max(0L, run.path("wall_time").asLong(0L));
                long cpuTime = Math.max(0L, run.path("cpu_time").asLong(0L));
                long memory = Math.max(0L, run.path("memory").asLong(0L));
                maxTime = Math.max(maxTime, Math.max(wallTime, cpuTime));
                maxMemory = Math.max(maxMemory, memory);

                if ("TO".equalsIgnoreCase(runStatus)
                        || wallTime > problem.getTimeLimitMillis()
                        || cpuTime > problem.getTimeLimitMillis()) {
                    return result(SubmissionResult.Status.TIME_LIMIT_EXCEEDED,
                            verdictMessage("Time Limit Exceeded", test, i),
                            passed, tests.size(), maxTime, maxMemory, lang.displayName());
                }

                if (memory > memoryLimitBytes) {
                    return result(SubmissionResult.Status.MEMORY_LIMIT_EXCEEDED,
                            verdictMessage("Memory Limit Exceeded", test, i),
                            passed, tests.size(), maxTime, maxMemory, lang.displayName());
                }

                if ("OL".equalsIgnoreCase(runStatus) || "EL".equalsIgnoreCase(runStatus)) {
                    return result(SubmissionResult.Status.RUNTIME_ERROR,
                            verdictMessage("Output limit exceeded", test, i),
                            passed, tests.size(), maxTime, maxMemory, lang.displayName());
                }

                if (!signal.isBlank() || (exitCode != null && exitCode != 0)
                        || "RE".equalsIgnoreCase(runStatus) || "SG".equalsIgnoreCase(runStatus)) {
                    String details = firstNonBlank(
                            run.path("stderr").asText(""),
                            run.path("message").asText(""),
                            signal.isBlank() ? "Runtime Error" : "Terminated by " + signal);
                    return result(SubmissionResult.Status.RUNTIME_ERROR,
                            test.hidden() ? verdictMessage("Runtime Error", test, i)
                                    : verdictMessage("Runtime Error", test, i) + ": " + truncate(details, 350),
                            passed, tests.size(), maxTime, maxMemory, lang.displayName());
                }

                String stdout = run.path("stdout").asText("");
                if (!outputsMatch(test.expectedOutput(), stdout)) {
                    return result(SubmissionResult.Status.WRONG_ANSWER,
                            verdictMessage("Wrong Answer", test, i),
                            passed, tests.size(), maxTime, maxMemory, lang.displayName());
                }

                passed++;
            } catch (java.net.http.HttpTimeoutException ex) {
                return result(SubmissionResult.Status.TIME_LIMIT_EXCEEDED,
                        verdictMessage("Time Limit Exceeded", test, i),
                        passed, tests.size(), maxTime, maxMemory, lang.displayName());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return result(SubmissionResult.Status.INVALID,
                        "Judge request interrupted", passed, tests.size(), maxTime, maxMemory, lang.displayName());
            } catch (Exception ex) {
                return result(SubmissionResult.Status.INVALID,
                        "Judge service unavailable",
                        passed, tests.size(), maxTime, maxMemory, lang.displayName());
            }
        }

        String msg = "Accepted · " + passed + "/" + tests.size()
                + " tests · " + maxTime + " ms · "
                + String.format(Locale.ROOT, "%.1f MB", maxMemory / (double) MB);
        return result(SubmissionResult.Status.ACCEPTED, msg, passed, tests.size(), maxTime, maxMemory, lang.displayName());
    }

    public String getExecuteEndpointHost() {
        return executeEndpoint.getHost() == null ? "configured judge" : executeEndpoint.getHost();
    }

    private static ExecutionResult result(
            SubmissionResult.Status status,
            String message,
            int passed,
            int total,
            long time,
            long memory,
            String language) {
        return new ExecutionResult(status, message, passed, total, time, memory, language);
    }

    private static String verdictMessage(String verdict, CPTestCase test, int zeroBasedIndex) {
        if (test.hidden()) {
            return verdict + " on hidden test #" + (zeroBasedIndex + 1);
        }
        return verdict + " on sample test #" + (zeroBasedIndex + 1);
    }

    private static boolean outputsMatch(String expected, String actual) {
        String[] a = tokenize(expected);
        String[] b = tokenize(actual);
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (!a[i].equals(b[i])) return false;
        }
        return true;
    }

    private static String[] tokenize(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
    }

    private static DetectedLanguage detectLanguage(String code, String requested) {
        if (requested != null && !requested.isBlank()) {
            String normalized = requested.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "cpp", "c++", "cpp17", "cpp20" -> new DetectedLanguage("c++", "Main.cpp", "GNU C++");
                case "java", "java21", "java17" -> new DetectedLanguage("java", "Main.java", "Java");
                case "python", "python3", "py" -> new DetectedLanguage("python", "main.py", "Python 3");
                default -> throw new IllegalArgumentException("Unsupported language: " + requested);
            };
        }

        if (code.contains("#include") || code.contains("std::") || code.contains("cout <<")) {
            return new DetectedLanguage("c++", "Main.cpp", "GNU C++");
        }
        if (code.contains("public class") || code.contains("class Main")) {
            return new DetectedLanguage("java", "Main.java", "Java");
        }
        if (code.contains("def ") || code.contains("print(") || code.contains("input(")) {
            return new DetectedLanguage("python", "main.py", "Python 3");
        }
        throw new IllegalArgumentException("Unable to detect language. Choose C++, Java, or Python.");
    }

    private static String resolveExecuteUrl(String configured) {
        String raw = configured == null ? "" : configured.trim();
        if (raw.isBlank()) return DEFAULT_PISTON_URL;
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            throw new IllegalArgumentException("PISTON_URL must start with http:// or https://");
        }
        raw = raw.replaceAll("/+$", "");
        if (raw.endsWith("/api/v2/execute")) return raw;
        return raw + "/api/v2/execute";
    }

    private static String sanitizeError(String value) {
        if (value == null || value.isBlank()) return "unknown error";
        return truncate(value.replaceAll("[\\r\\n]+", " "), 240);
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private record DetectedLanguage(String language, String filename, String displayName) {}
}
