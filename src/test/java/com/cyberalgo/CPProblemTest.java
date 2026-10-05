package com.cyberalgo;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CPProblemTest {

    @Test
    void configuredCasesKeepHiddenCasesPrivateFromSamples() {
        CPProblem problem = new CPProblem(
                "CP-WEEK-01",
                "Weekly Two Sum",
                200,
                Challenge.Difficulty.EASY,
                1000,
                256,
                Path.of("unused"),
                List.of(
                        CPTestCase.sample("2 3\n", "5\n"),
                        CPTestCase.hidden("100 200\n", "300\n"),
                        CPTestCase.hidden("-5 7\n", "2\n")));

        assertEquals(3, problem.getJudgeTestCases().size());
        assertEquals(1, problem.getPublicSampleCases().size());
        assertEquals(2, problem.getHiddenTestCount());
        assertTrue(problem.hasJudgeTestCases());
        assertFalse(problem.getPublicSampleCases().stream().anyMatch(CPTestCase::hidden));
    }

    @Test
    void validatesProductionResourceLimits() {
        assertThrows(IllegalArgumentException.class, () -> new CPProblem(
                "CP-BAD-TIME", "Bad", 100, Challenge.Difficulty.EASY,
                0, 256, Path.of("unused")));

        assertThrows(IllegalArgumentException.class, () -> new CPProblem(
                "CP-BAD-MEM", "Bad", 100, Challenge.Difficulty.EASY,
                1000, 0, Path.of("unused")));
    }
}
