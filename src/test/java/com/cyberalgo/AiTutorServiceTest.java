package com.cyberalgo;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AiTutorServiceTest {

    @Test
    void ctfPromptExcludesFlagHash() {
        String secretHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        CTFChallenge challenge = new CTFChallenge(
                "CTF-AI-1",
                "Tutor Crypto",
                100,
                Challenge.Difficulty.EASY,
                "CRYPTO",
                secretHash,
                10,
                "public.zip");
        challenge.setDescription("Decode the public artifact.");

        String prompt = AiTutorService.buildPrompt(challenge, "HINT", 1, "", "", "");

        assertFalse(prompt.contains(secretHash));
        assertFalse(prompt.toLowerCase().contains("flaghash"));
        assertTrue(prompt.contains("Category: CRYPTO"));
        assertTrue(prompt.contains("Public attachment name: public.zip"));
    }

    @Test
    void cpPromptExcludesTestcaseDirectory() {
        CPProblem challenge = new CPProblem(
                "CP-AI-1",
                "Tutor DP",
                200,
                Challenge.Difficulty.MEDIUM,
                1000,
                256,
                Path.of("contest_data/private/testcases/CP-AI-1"));
        challenge.setDescription("Find the optimal substructure.");

        String prompt = AiTutorService.buildPrompt(challenge, "EXPLAIN", 1, "", "", "");

        assertFalse(prompt.contains("contest_data/private/testcases"));
        assertTrue(prompt.contains("Time limit: 1000 ms"));
        assertTrue(prompt.contains("Memory limit: 256 MB"));
    }

    @Test
    void progressiveHintLevelIsRepresented() {
        CTFChallenge challenge = new CTFChallenge(
                "CTF-AI-2",
                "Tutor Web",
                100,
                Challenge.Difficulty.MEDIUM,
                "WEB",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                0,
                null);

        String levelThree = AiTutorService.buildPrompt(challenge, "HINT", 3, "Where should I look?", "", "");

        assertTrue(levelThree.contains("HINT LEVEL: 3/3"));
        assertTrue(levelThree.contains("pseudocode/debugging direction"));
    }
}
