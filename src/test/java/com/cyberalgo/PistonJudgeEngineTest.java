package com.cyberalgo;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PistonJudgeEngineTest {

    @Test
    void refusesToAcceptProblemWithoutServerSideTests() {
        CPProblem problem = new CPProblem(
                "CP-NO-TESTS",
                "Misconfigured",
                100,
                Challenge.Difficulty.EASY,
                1000,
                256,
                Path.of("definitely-not-present"));

        PistonJudgeEngine.ExecutionResult result =
                new PistonJudgeEngine().judge(problem, "print(42)", "python");

        assertEquals(SubmissionResult.Status.INVALID, result.status());
        assertTrue(result.message().contains("no server-side testcases"));
        assertEquals(0, result.totalTestcases());
    }

    @Test
    void memoryLimitVerdictIsAFirstClassStatus() {
        assertEquals(
                SubmissionResult.Status.MEMORY_LIMIT_EXCEEDED,
                SubmissionResult.Status.fromToken("memory_limit_exceeded"));
    }
}
