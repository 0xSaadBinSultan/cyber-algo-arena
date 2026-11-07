package com.cyberalgo;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JudgeProtocolTest {
    private PistonJudgeEngine.ExecutionResult response(String json, int httpStatus, List<CPTestCase> tests) throws Exception {
        return response(json, httpStatus, tests, "python");
    }

    private PistonJudgeEngine.ExecutionResult response(String json, int httpStatus, List<CPTestCase> tests, String language) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v2/execute", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(httpStatus, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            CPProblem cp = new CPProblem("TEST-JUDGE", "Protocol test", 100, Challenge.Difficulty.EASY, 1000, 256, Path.of("absent"), tests);
            return new PistonJudgeEngine("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v2/execute").judge(cp, "print(42)", language);
        } finally { server.stop(0); }
    }
    @Test void acceptsCorrectOutputAndRejectsWrongOutput() throws Exception {
        String run = "{\"run\":{\"code\":0,\"stdout\":\"42\\n\",\"wall_time\":2}}";
        assertEquals(SubmissionResult.Status.ACCEPTED, response(run,200,List.of(CPTestCase.sample("", "42"))).status());
        assertEquals(SubmissionResult.Status.WRONG_ANSWER, response(run,200,List.of(CPTestCase.hidden("secret", "43"))).status());
    }
    @Test void javaStartupAllowanceDoesNotRelaxOtherLanguageLimits() throws Exception {
        String run = "{\"run\":{\"code\":0,\"stdout\":\"42\\n\",\"wall_time\":1500}}";
        var tests = List.of(CPTestCase.sample("", "42"));
        assertEquals(SubmissionResult.Status.ACCEPTED, response(run,200,tests,"java").status());
        assertEquals(SubmissionResult.Status.TIME_LIMIT_EXCEEDED, response(run,200,tests,"python").status());
    }
    @Test void hiddenRuntimeDiagnosticsNeverEchoSecretInput() throws Exception {
        var result = response("{\"run\":{\"code\":1,\"stderr\":\"HIDDEN-INPUT-SECRET\"}}",200,List.of(CPTestCase.hidden("HIDDEN-INPUT-SECRET", "42")));
        assertEquals(SubmissionResult.Status.RUNTIME_ERROR,result.status());
        assertFalse(result.message().contains("HIDDEN-INPUT-SECRET"));
    }
    @Test void malformedAndUnauthorizedResponsesAreInfrastructureFailures() throws Exception {
        assertEquals(SubmissionResult.Status.INVALID,response("{}",200,List.of(CPTestCase.sample("", ""))).status());
        assertEquals(SubmissionResult.Status.INVALID,response("{}",403,List.of(CPTestCase.sample("", ""))).status());
    }
    @Test void compileAndRuntimeLimitsHaveDistinctVerdicts() throws Exception {
        var tests=List.of(CPTestCase.sample("", "42"));
        assertEquals(SubmissionResult.Status.COMPILATION_ERROR,response("{\"compile\":{\"code\":1,\"stderr\":\"bad syntax\"}}",200,tests).status());
        assertEquals(SubmissionResult.Status.TIME_LIMIT_EXCEEDED,response("{\"run\":{\"code\":1,\"status\":\"TO\"}}",200,tests).status());
        assertEquals(SubmissionResult.Status.MEMORY_LIMIT_EXCEEDED,response("{\"run\":{\"code\":0,\"memory\":999999999}}",200,tests).status());
    }
}
