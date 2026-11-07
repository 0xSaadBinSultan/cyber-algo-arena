package com.cyberalgo;

import org.junit.jupiter.api.Test;
import java.util.Scanner;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PracticeCatalogTest {
    @Test void everyPacketBudgetTestHasTheCorrect64BitSum() {
        CPProblem p = PracticeCatalog.packetBudget();
        assertEquals(2, p.getPublicSampleCases().size());
        assertEquals(6, p.getHiddenTestCount());
        for (CPTestCase tc : p.getJudgeTestCases()) {
            Scanner input = new Scanner(tc.input());
            int n = input.nextInt();
            long sum = 0;
            for (int i = 0; i < n; i++) sum += input.nextLong();
            assertFalse(input.hasNext());
            assertEquals(Long.parseLong(tc.expectedOutput().trim()), sum);
        }
        assertTrue(p.isJudgeReady());
    }
    @Test void labsAreSolvableFromTheirSuppliedEvidence() throws Exception {
        var labs = PracticeCatalog.securityLabs();
        assertTrue(labs.get(0).evaluate(new String(java.util.Base64.getDecoder().decode("ZmxhZ3tvbmVfZGVjb2RlX2Nsb3Nlcn0="))));
        String log = Files.readString(Path.of("contest_data/attachments/campus-access.log.txt"));
        assertTrue(log.contains("flag%7Bfollow_the_request%7D"));
        assertTrue(labs.get(1).evaluate("flag{follow_the_request}"));
        assertTrue(labs.get(2).evaluate(new StringBuilder("}esrever_neht_daer{galf").reverse().toString()));
        assertTrue(labs.get(3).evaluate("flag{jwt_cookie_decoded_success}"));
        assertFalse(labs.get(0).evaluate("flag{wrong}"));
    }

    @Test void everySignalSurgeTestMatchesKadaneAlgorithm() {
        CPProblem p = PracticeCatalog.signalSurge();
        assertEquals(2, p.getPublicSampleCases().size());
        assertEquals(6, p.getHiddenTestCount());
        for (CPTestCase tc : p.getJudgeTestCases()) {
            Scanner input = new Scanner(tc.input());
            int n = input.nextInt();
            long first = input.nextLong();
            long maxSoFar = first;
            long currMax = first;
            for (int i = 1; i < n; i++) {
                long x = input.nextLong();
                currMax = Math.max(x, currMax + x);
                maxSoFar = Math.max(maxSoFar, currMax);
            }
            assertFalse(input.hasNext());
            assertEquals(Long.parseLong(tc.expectedOutput().trim()), maxSoFar);
        }
        assertTrue(p.isJudgeReady());
    }
}
