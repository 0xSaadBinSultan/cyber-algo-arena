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

    public static CPProblem signalSurge() {
        CPProblem cp = new CPProblem("ARENA-MAXSUB", "Signal Surge", 150, Challenge.Difficulty.MEDIUM,
                2000, 256, Path.of("contest_data/testcases/ARENA-MAXSUB"), List.of(
                CPTestCase.sample("9\n-2 1 -3 4 -1 2 1 -5 4\n", "6\n"),
                CPTestCase.sample("5\n5 4 -1 7 8\n", "23\n"),
                CPTestCase.hidden("1\n-42\n", "-42\n"),
                CPTestCase.hidden("1\n1000000000\n", "1000000000\n"),
                CPTestCase.hidden("5\n-10 -20 -3 -50 -100\n", "-3\n"),
                CPTestCase.hidden("3\n1000000000 1000000000 1000000000\n", "3000000000\n"),
                CPTestCase.hidden("6\n-1 2 -1 3 -2 4\n", "6\n"),
                CPTestCase.hidden("50000\n" + "1 -1 ".repeat(25000) + "\n", "1\n")));
        cp.setDescription("""
                ## Signal Surge
                A satellite transceiver receives a continuous telemetry stream of signal delta measurements.
                A power surge occurs over the contiguous transmission interval that maximizes the cumulative sum of signal deltas.
                Find the maximum possible sum of a non-empty contiguous subarray.

                ## Input
                The first line contains an integer n (1 ≤ n ≤ 100000), the number of measurements.
                The second line contains n space-separated integers a_i (-10^9 ≤ a_i ≤ 10^9).

                ## Output
                Print a single integer: the maximum sum among all non-empty contiguous subarrays.
                Use a 64-bit integer (long in C++/Java) as cumulative sums can exceed 32-bit limits.

                ## Example explanation
                In the first sample, the subarray [4, -1, 2, 1] yields 4 + (-1) + 2 + 1 = 6.
                Aim for an O(n) algorithm (such as Kadane's algorithm).
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
        CTFChallenge web = ctf("ARENA-WEB", "The Session Cookie", "WEB", 150,
                "flag{jwt_cookie_decoded_success}", """
                ## The story
                A security audit of the club's staging API captured an HTTP response header during an administrator session:
                ```http
                HTTP/1.1 200 OK
                Set-Cookie: session=eyJ1c2VyIjoiZ3Vlc3QiLCJyb2xlIjoiZ3Vlc3QiLCJmbGFnIjoiZmxhZ3tqd3RfY29va2llX2RlY29kZWRfc3VjY2Vzc30ifQ==; Path=/; HttpOnly
                ```

                ## Objective
                Inspect the base64-encoded cookie payload, decode its JSON content, and recover the flag.

                ## Submit
                Submit the decoded flag string: `flag{...}`.
                """);
        return List.of(crypto, forensic, reverse, web);
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
        if (!ids.contains("ARENA-MAXSUB")) repo.saveChallenge(signalSurge());
        for (CTFChallenge c : securityLabs()) if (!ids.contains(c.getId())) repo.saveChallenge(c);
    }
}
