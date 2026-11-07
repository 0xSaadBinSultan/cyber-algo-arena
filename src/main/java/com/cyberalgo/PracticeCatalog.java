package com.cyberalgo;

import java.nio.file.Path;
import java.util.List;

/** Complete, original practice material; safely adds missing entries to existing databases. */
public final class PracticeCatalog {
    private PracticeCatalog() {}

    public static CPProblem packetBudget() {
        CPProblem cp = new CPProblem("ARENA-SUM", "Packet Budget", 100, Challenge.Difficulty.EASY,
                2000, 256, Path.of("contest_data/testcases/ARENA-SUM"), List.of(
                CPTestCase.sample("5\n12 8 5 10 15\n", "50\n"),
                CPTestCase.sample("3\n0 0 7\n", "7\n"),
                CPTestCase.hidden("1\n0\n", "0\n"),
                CPTestCase.hidden("1\n1000000000\n", "1000000000\n"),
                CPTestCase.hidden("4\n1000000000 1000000000 1000000000 1000000000\n", "4000000000\n"),
                CPTestCase.hidden("6\n9 0 1 99 100 7\n", "216\n"),
                CPTestCase.hidden("5\n1\n2  3\n4\t5\n", "15\n"),
                CPTestCase.hidden("200000\n" + "1 ".repeat(200000) + "\n", "200000\n")));
        cp.setDescription("""
                ## Packet Budget
                Your university network records the size of every packet in a batch.
                Calculate the total number of bytes in the batch.

                ## Input
                The first integer is n, the number of packets (1 ≤ n ≤ 200000).
                The next n integers are packet sizes a_i (0 ≤ a_i ≤ 1000000000).
                Integers may be separated by any whitespace.

                ## Output
                Print one integer: the sum of all packet sizes.
                Use a 64-bit integer in C++ or Java: the answer can exceed 2147483647.

                ## Example explanation
                In the first sample, 12 + 8 + 5 + 10 + 15 = 50 bytes.
                Aim for O(n) time. No external files or network access are needed.
                """);
        return cp;
    }

    public static List<CTFChallenge> securityLabs() {
        CTFChallenge crypto = ctf("ARENA-CRYPTO", "A message between lectures", "CRYPTO", 100,
                "flag{one_decode_closer}", """
                ## The story
                A classmate left an encoded note in the club chat. It looks encrypted, but
                the trailing equals sign suggests a common text encoding.

                ## Evidence
                Decode this exact string to recover the flag:
                ```
                ZmxhZ3tvbmVfZGVjb2RlX2Nsb3Nlcn0=
                ```
                ## Submit
                Submit the decoded text, including flag{ and the closing brace.
                All evidence is supplied here. No external target is required.
                """);
        CTFChallenge forensic = ctf("ARENA-LOG", "The midnight request", "FORENSICS", 150,
                "flag{follow_the_request}", """
                ## The story
                The campus server received one unusual request after midnight.
                Download campus-access.log and inspect the successful request to /backup.

                ## Objective
                A query parameter contains a URL-encoded note. Decode its percent escapes
                and submit the recovered flag. The log is synthetic training evidence.
                """);
        forensic = new CTFChallenge(forensic.getId(), forensic.getTitle(), forensic.getBasePoints(),
                forensic.getDifficulty(), "FORENSICS", forensic.getFlagHash(), 10, "campus-access.log.txt");
        forensic.setDescription("""
                ## The midnight request
                A campus server received one unusual request after midnight.
                Download campus-access.log.txt. Find the successful GET request to /backup,
                decode the percent escapes in its note parameter, and submit the flag.
                This synthetic log is the complete evidence; no live target is required.
                """);
        CTFChallenge reverse = ctf("ARENA-REV", "Read the checker", "REVERSE_ENGINEERING", 200,
                "flag{read_then_reverse}", """
                ## The story
                A tiny flag checker has been recovered from an old club exercise.
                Read the Python below; you do not need to execute untrusted code.

                ## Checker
                ```python
                def check(candidate):
                    return candidate[::-1] == "}esrever_neht_daer{galf"
                ```
                ## Objective
                Find the string for which check returns True. Submit that string as the flag.
                """);
        return List.of(crypto, forensic, reverse);
    }

    private static CTFChallenge ctf(String id, String title, String category, int points, String flag, String description) {
        CTFChallenge c = new CTFChallenge(id, title, points, Challenge.Difficulty.EASY, category,
                CTFChallenge.sha256Hex(flag), 10);
        c.setDescription(description);
        return c;
    }

    public static void ensureInstalled(MongoRepository repo) {
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Challenge c : repo.getAllChallenges()) ids.add(c.getId());
        if (!ids.contains("ARENA-SUM")) repo.saveChallenge(packetBudget());
        for (CTFChallenge c : securityLabs()) if (!ids.contains(c.getId())) repo.saveChallenge(c);
    }
}
