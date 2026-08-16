import java.util.*;

public final class SecurityPuzzleSyncService {

    public record SyncResult(int syncedCount, List<Map<String, Object>> problems) {}

    private record CuratedPuzzle(
        String id,
        String title,
        String description,
        String category,
        int basePoints,
        CTFChallenge.Difficulty difficulty,
        String rawFlag,
        String hintText,
        int hintCost
    ) {}

    private static final List<CuratedPuzzle> PUZZLES = List.of(
        // CRYPTOGRAPHY
        new CuratedPuzzle("SEC-CRYPTO-001", "Caesar Cipher Basics", "Learn the basics of Caesar cipher.", "CRYPTOGRAPHY", 50, CTFChallenge.Difficulty.EASY, "flag{julius_would_be_proud}", "Try shifting each letter by a fixed number", 10),
        new CuratedPuzzle("SEC-CRYPTO-002", "Base64 Decode Challenge", "Learn the difference between encoding and encryption.", "CRYPTOGRAPHY", 75, CTFChallenge.Difficulty.EASY, "flag{base64_is_not_encryption}", "Base64 is encoding, not encryption", 10),
        new CuratedPuzzle("SEC-CRYPTO-003", "RSA Key Recovery", "Recover RSA key with small exponent.", "CRYPTOGRAPHY", 300, CTFChallenge.Difficulty.HARD, "flag{small_exponent_big_problem}", "Check if the public exponent is unusually small", 30),
        new CuratedPuzzle("SEC-CRYPTO-004", "XOR Cipher Analysis", "Analyze XOR cipher.", "CRYPTOGRAPHY", 150, CTFChallenge.Difficulty.MEDIUM, "flag{xor_the_known_plaintext}", "XOR is its own inverse", 20),
        
        // FORENSICS
        new CuratedPuzzle("SEC-FORENSICS-001", "Hidden in Plain Sight", "Check metadata.", "FORENSICS", 100, CTFChallenge.Difficulty.EASY, "flag{metadata_tells_all}", "Check file metadata with exiftool", 15),
        new CuratedPuzzle("SEC-FORENSICS-002", "Packet Capture Analysis", "Analyze PCAP file.", "FORENSICS", 200, CTFChallenge.Difficulty.MEDIUM, "flag{follow_the_tcp_stream}", "Filter HTTP traffic in Wireshark", 25),
        new CuratedPuzzle("SEC-FORENSICS-003", "Memory Dump Investigation", "Investigate memory dump.", "FORENSICS", 350, CTFChallenge.Difficulty.HARD, "flag{volatility_is_your_friend}", "Use Volatility framework for analysis", 35),
        new CuratedPuzzle("SEC-FORENSICS-004", "Steganography Detection", "Detect steganography.", "FORENSICS", 150, CTFChallenge.Difficulty.MEDIUM, "flag{hidden_in_the_pixels}", "LSB steganography is common", 20),

        // WEB_SECURITY
        new CuratedPuzzle("SEC-WEB-001", "SQL Injection Basics", "Learn SQLi.", "WEB_SECURITY", 100, CTFChallenge.Difficulty.EASY, "flag{always_parameterize_queries}", "Try a single quote in the input field", 15),
        new CuratedPuzzle("SEC-WEB-002", "XSS Reflected Attack", "Learn XSS.", "WEB_SECURITY", 150, CTFChallenge.Difficulty.MEDIUM, "flag{sanitize_user_input}", "Check if input is reflected in the page", 20),
        new CuratedPuzzle("SEC-WEB-003", "IDOR Vulnerability", "Learn IDOR.", "WEB_SECURITY", 200, CTFChallenge.Difficulty.MEDIUM, "flag{check_authorization_not_authentication}", "Try changing the user ID parameter", 25),
        new CuratedPuzzle("SEC-WEB-004", "JWT Token Forgery", "Forge JWT.", "WEB_SECURITY", 400, CTFChallenge.Difficulty.HARD, "flag{none_algorithm_is_dangerous}", "What happens with alg: none?", 40),

        // SYSTEMS_SECURITY
        new CuratedPuzzle("SEC-SYS-001", "Buffer Overflow 101", "Learn buffer overflows.", "SYSTEMS_SECURITY", 150, CTFChallenge.Difficulty.MEDIUM, "flag{stack_smashing_detected}", "Exceed the buffer size to overwrite return address", 20),
        new CuratedPuzzle("SEC-SYS-002", "Format String Exploit", "Learn format string exploits.", "SYSTEMS_SECURITY", 250, CTFChallenge.Difficulty.MEDIUM, "flag{printf_needs_format_string}", "What if printf takes user input directly?", 25),
        new CuratedPuzzle("SEC-SYS-003", "Return Oriented Programming", "Learn ROP.", "SYSTEMS_SECURITY", 450, CTFChallenge.Difficulty.HARD, "flag{gadgets_chain_for_the_win}", "Find useful gadgets with ROPgadget", 45),
        new CuratedPuzzle("SEC-SYS-004", "Privilege Escalation", "Learn privesc.", "SYSTEMS_SECURITY", 300, CTFChallenge.Difficulty.HARD, "flag{suid_binaries_are_gold}", "Check for SUID binaries", 30),

        // GENERAL_SKILLS
        new CuratedPuzzle("SEC-GEN-001", "Linux Command Mastery", "Master Linux commands.", "GENERAL_SKILLS", 50, CTFChallenge.Difficulty.EASY, "flag{grep_is_your_best_friend}", "Try piping commands together", 10),
        new CuratedPuzzle("SEC-GEN-002", "Network Reconnaissance", "Learn network recon.", "GENERAL_SKILLS", 100, CTFChallenge.Difficulty.EASY, "flag{nmap_reveals_all}", "Start with a basic port scan", 15),
        new CuratedPuzzle("SEC-GEN-003", "Git History Secrets", "Find secrets in Git.", "GENERAL_SKILLS", 200, CTFChallenge.Difficulty.MEDIUM, "flag{git_log_shows_everything}", "Check previous commits for sensitive data", 20),
        new CuratedPuzzle("SEC-GEN-004", "Binary Analysis Intro", "Intro to binary analysis.", "GENERAL_SKILLS", 250, CTFChallenge.Difficulty.MEDIUM, "flag{strings_command_finds_secrets}", "Run the strings command first", 25)
    );

    public SyncResult sync(ContestEngine engine, String categoryFilter) {
        int syncedCount = 0;
        List<Map<String, Object>> problems = new ArrayList<>();

        for (CuratedPuzzle puzzle : PUZZLES) {
            if (!"ALL".equals(categoryFilter) && !puzzle.category().equals(categoryFilter)) {
                continue;
            }

            try {
                try {
                    engine.getChallenge(puzzle.id());
                    // Challenge already exists, skip
                    continue;
                } catch (ChallengeNotFoundException e) {
                    // Expected, continue to add
                }

                CTFChallenge.Category mappedCategory = mapCategory(puzzle.category());
                String flagHash = CTFChallenge.sha256Hex(puzzle.rawFlag());
                
                CTFChallenge challenge = new CTFChallenge(
                    puzzle.id(),
                    puzzle.title(),
                    puzzle.basePoints(),
                    puzzle.difficulty(),
                    mappedCategory.name(),
                    flagHash,
                    puzzle.hintCost()
                );
                challenge.setDescription(puzzle.description());
                
                engine.addChallenge(challenge);
                syncedCount++;

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("id", puzzle.id());
                info.put("title", puzzle.title());
                info.put("category", puzzle.category());
                info.put("points", puzzle.basePoints());
                info.put("difficulty", puzzle.difficulty().name());
                problems.add(info);
            } catch (Exception e) {
                System.err.println("[SecurityPuzzleSyncService] Error syncing " + puzzle.id() + ": " + e.getMessage());
            }
        }

        System.out.println("[SecurityPuzzleSyncService] Synchronized " + syncedCount + " security exercises into MongoDB.");
        return new SyncResult(syncedCount, problems);
    }

    private CTFChallenge.Category mapCategory(String category) {
        return switch (category) {
            case "CRYPTOGRAPHY" -> CTFChallenge.Category.CRYPTO;
            case "FORENSICS" -> CTFChallenge.Category.MISC;
            case "WEB_SECURITY" -> CTFChallenge.Category.WEB;
            case "SYSTEMS_SECURITY" -> CTFChallenge.Category.PWN;
            case "GENERAL_SKILLS" -> CTFChallenge.Category.MISC;
            default -> throw new IllegalArgumentException("Unknown category: " + category);
        };
    }
}
