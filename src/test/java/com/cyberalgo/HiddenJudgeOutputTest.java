package com.cyberalgo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class HiddenJudgeOutputTest {
    @Test
    void programCannotExfiltrateHiddenInputViaRuntimeOrCompilerDiagnostics() throws Exception {
        for (String phase : List.of("run", "compile")) {
            HttpServer sandbox = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            sandbox.createContext("/execute", exchange -> {
                var request = new ObjectMapper().readTree(exchange.getRequestBody());
                String leakedInput = request.path("stdin").asText();
                byte[] response = new ObjectMapper().writeValueAsBytes(Map.of(phase,
                        Map.of("code", 1, "status", "RE", "stderr", leakedInput, "output", leakedInput)));
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            sandbox.start();
            try {
                CPProblem cp = new CPProblem("CP", "Hidden", 100, Challenge.Difficulty.EASY, 1000, 256,
                        Path.of("unused"), List.of(CPTestCase.hidden("secret-input-sentinel", "secret-output-sentinel")));
                var result = new PistonJudgeEngine("http://127.0.0.1:" + sandbox.getAddress().getPort() + "/execute")
                        .judge(cp, "print(input())", "python");
                assertFalse(result.message().contains("secret-input-sentinel"));
                assertFalse(result.message().contains("secret-output-sentinel"));
                assertEquals(phase.equals("run") ? SubmissionResult.Status.RUNTIME_ERROR
                        : SubmissionResult.Status.COMPILATION_ERROR, result.status());
            } finally {
                sandbox.stop(0);
            }
        }
    }
}
