import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class DatabaseSeeder {

    public static void seedIfEmpty(MongoRepository repo) {
        if (!repo.getAllChallenges().isEmpty()) return;

        System.out.println("[DatabaseSeeder] Seeding 16 rich challenges...");

        // --- CTF CHALLENGES (8) ---
        seedCtf(repo, "ctf-crypto-1", "The Forgotten RSA Key", 200, "Medium", "Crypto",
                "## Scenario\nDuring a recent raid on a cyber syndicate, we recovered an encrypted transmission. The syndicate's crypto operator made a fatal flaw: they reused the same primes for two different keys!\n\n## Objective\nFactorize the modulus by finding the GCD of the two public keys, then decrypt the ciphertext to recover the secret.\n\n## Data\n`N1 = ...`\n`N2 = ...`\n`C = ...`\n\n```python\n# Hint:\nimport math\np = math.gcd(N1, N2)\n```\n",
                "flag{gcd_is_the_magic_key}");

        seedCtf(repo, "ctf-web-1", "SQL Injection Playground", 150, "Easy", "Web",
                "## Scenario\nAn old legacy web portal is still active on our network. It is using an outdated ORM that might be vulnerable to union-based SQL injection.\n\n## Objective\nBypass the login prompt and extract the admin's secret key from the `secrets` table.\n\n* Target URL: `http://challenges.local/web/1`\n* Vulnerable Parameter: `?id=`\n\nSubmit the extracted key wrapped in the flag format.",
                "flag{union_select_1_2_3}");

        seedCtf(repo, "ctf-for-1", "Hidden in the Headers", 300, "Hard", "Forensics",
                "## Scenario\nWe intercepted a strange PNG file. Standard image viewers say it's corrupted, but our hex editor tells a different story. \n\n## Objective\nAnalyze the file headers and identify the hidden chunk containing the payload.\n\n* Fix the magic bytes.\n* Extract the zlib compressed stream.\n\nSubmit the plaintext string found inside.",
                "flag{magic_bytes_restored}");

        seedCtf(repo, "ctf-sys-1", "Buffer Overflow 101", 400, "Hard", "Systems",
                "## Scenario\nYou've been granted SSH access to a low-privileged container. There's a setuid binary `/opt/vuln` that copies user input without checking bounds.\n\n## Objective\nExploit the buffer overflow to overwrite the return address and jump to the `win()` function.\n\n```c\nvoid win() {\n    system(\"/bin/sh\");\n}\n```\n\nThe flag is located at `/root/flag.txt`.",
                "flag{ret2win_success}");

        seedCtf(repo, "ctf-osint-1", "Ghost on the Web", 100, "Easy", "OSINT",
                "## Scenario\nA hacker going by the handle `DarkPhantom99` has been bragging about their latest breach on an obscure forum. \n\n## Objective\nTrace their digital footprint. Find their GitHub repository, inspect the commit history, and locate the accidentally committed API key.\n\nSubmit the API key as the flag.",
                "flag{always_check_git_history}");

        seedCtf(repo, "ctf-crypto-2", "XOR Cipher Breakdown", 250, "Medium", "Crypto",
                "## Scenario\nWe found a ciphertext encrypted with a repeating-key XOR. We know the key is exactly 4 bytes long, and the plaintext starts with `flag{`.\n\n## Objective\nDetermine the key using the known plaintext attack method, then decrypt the rest of the message.\n\n`Ciphertext (hex): 1a2b3c4d...`",
                "flag{xor_is_weak_with_known_pt}");

        seedCtf(repo, "ctf-web-2", "JWT Forgery", 350, "Hard", "Web",
                "## Scenario\nThis web app uses JSON Web Tokens (JWT) for authentication. The developer left the `/jwks.json` endpoint exposed and is using a weak signing algorithm.\n\n## Objective\nForge a JWT with `\"role\": \"admin\"` and sign it using the exposed public key by changing the `alg` header from RS256 to HS256.\n\nSubmit the flag retrieved from the `/admin` endpoint.",
                "flag{jwt_alg_confusion}");

        seedCtf(repo, "ctf-for-2", "Memory Dump Analysis", 450, "Expert", "Forensics",
                "## Scenario\nA workstation was compromised. We took a raw memory dump before pulling the plug.\n\n## Objective\nUse Volatility to analyze `memdump.raw`. Find the malicious process, dump its memory, and extract the attacker's C2 domain.\n\nSubmit the domain name.",
                "flag{evil-c2-domain.local}");

        // --- CP CHALLENGES (8) ---
        seedCp(repo, "cp-dp-1", "Knapsack Optimizer", 200, "Medium", "Dynamic Programming",
                "## Problem Statement\nYou are given $N$ items, each with a weight $W_i$ and a value $V_i$. You have a knapsack with a maximum capacity of $K$. Find the maximum value you can carry.\n\n## Input Specification\n* First line: Two integers $N$ and $K$ ($1 \\le N \\le 1000$, $1 \\le K \\le 10000$).\n* Next $N$ lines: Two integers $W_i$ and $V_i$.\n\n## Output Specification\n* A single integer: the maximum value.",
                "4 5\n1 8\n2 4\n3 0\n2 5\n2 3", "13");

        seedCp(repo, "cp-graph-1", "Shortest Path Matrix", 300, "Hard", "Graphs",
                "## Problem Statement\nYou are trapped in an $N \\times M$ grid. Some cells are blocked (`#`) and others are free (`.`). Find the shortest path from `(0,0)` to `(N-1, M-1)`.\n\n## Input Specification\n* First line: $N, M$.\n* Next $N$ lines: grid strings.\n\n## Output Specification\n* The minimum number of steps, or `-1` if impossible.",
                "3 3\n...\n.#.\n...\n", "4");

        seedCp(repo, "cp-str-1", "Longest Palindromic Substring", 150, "Easy", "Strings",
                "## Problem Statement\nGiven a string $S$, find the length of the longest contiguous substring that is a palindrome.\n\n## Input Specification\n* A single string $S$ consisting of lowercase English letters ($1 \\le |S| \\le 1000$).\n\n## Output Specification\n* A single integer representing the length.",
                "abacabad", "7");

        seedCp(repo, "cp-math-1", "Prime Factor Counting", 200, "Medium", "Math",
                "## Problem Statement\nGiven an integer $N$, print the number of distinct prime factors of $N$.\n\n## Input Specification\n* $1 \\le N \\le 10^{12}$\n\n## Output Specification\n* A single integer.",
                "12", "2");

        seedCp(repo, "cp-dp-2", "Edit Distance", 350, "Hard", "Dynamic Programming",
                "## Problem Statement\nFind the minimum number of operations (insert, delete, replace) required to convert string A into string B.\n\n## Input Specification\n* Two lines, string A and string B.\n\n## Output Specification\n* An integer representing the Levenshtein distance.",
                "kitten\nsitting", "3");

        seedCp(repo, "cp-graph-2", "Network Flow Routing", 500, "Expert", "Graphs",
                "## Problem Statement\nGiven a directed graph with capacities, find the maximum flow from the source $S$ to the sink $T$.\n\n## Input Specification\n* $V, E, S, T$.\n* $E$ lines with $U, V, C$.\n\n## Output Specification\n* Maximum flow integer.",
                "4 5 1 4\n1 2 40\n1 3 20\n2 4 20\n2 3 20\n3 4 20", "40");

        seedCp(repo, "cp-str-2", "Suffix Array Construction", 450, "Expert", "Strings",
                "## Problem Statement\nConstruct the suffix array for string $S$. Print the starting indices of the sorted suffixes.\n\n## Input Specification\n* String $S$.\n\n## Output Specification\n* Space-separated integers.",
                "banana", "5 3 1 0 4 2");

        seedCp(repo, "cp-math-2", "Modular Exponentiation", 100, "Easy", "Math",
                "## Problem Statement\nCalculate $A^B \\pmod M$.\n\n## Input Specification\n* $A, B, M$ ($1 \\le A, B, M \\le 10^9$).\n\n## Output Specification\n* Integer result.",
                "2 10 1000", "24");
                
        System.out.println("[DatabaseSeeder] Done seeding 16 challenges.");
    }

    private static void seedCtf(MongoRepository repo, String id, String title, int pts, String diff, String cat, String desc, String flag) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(flag.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            String flagHash = sb.toString();
            CTFChallenge c = new CTFChallenge(id, title, pts, Challenge.Difficulty.valueOf(diff.toUpperCase()), cat, flagHash, 10);
            c.setDescription(desc);
            repo.saveChallenge(c);
        } catch (Exception e) {}
    }

    private static void seedCp(MongoRepository repo, String id, String title, int pts, String diff, String cat, String desc, String sampleIn, String sampleOut) {
        try {
            String fullDesc = desc + "\n\n### Sample Input\n```\n" + sampleIn + "\n```\n\n### Sample Output\n```\n" + sampleOut + "\n```\n";
            Path testDir = Paths.get("contest_data", "testcases", id);
            Files.createDirectories(testDir);
            Files.writeString(testDir.resolve("input_1.txt"), sampleIn + "\n");
            Files.writeString(testDir.resolve("output_1.txt"), sampleOut + "\n");
            CPProblem c = new CPProblem(id, title, pts, Challenge.Difficulty.valueOf(diff.toUpperCase()), 2000, 256, testDir);
            c.setDescription(fullDesc);
            repo.saveChallenge(c);
        } catch (Exception e) {}
    }
}
