package com.cyberalgo;

import java.util.Locale;

/**
 * Builds privacy-minimized prompts for the Cyber-Algo AI Tutor.
 * Sensitive challenge verification data is deliberately excluded.
 */
public final class AiTutorService {

    private static final int MAX_QUESTION = 1200;
    private static final int MAX_CODE = 6000;
    private static final int MAX_ERROR = 2000;

    private final GeminiService gemini;

    public AiTutorService(GeminiService gemini) {
        this.gemini = gemini;
    }

    public boolean isConfigured() {
        return gemini.isConfigured();
    }

    public String model() {
        return gemini.getModel();
    }

    public String tutor(
            Challenge challenge,
            String requestedMode,
            int requestedHintLevel,
            String question,
            String userCode,
            String compilerError) {

        String mode = normalizeMode(requestedMode);
        int hintLevel = Math.max(1, Math.min(3, requestedHintLevel));
        String prompt = buildPrompt(
                challenge,
                mode,
                hintLevel,
                limit(question, MAX_QUESTION),
                limit(userCode, MAX_CODE),
                limit(compilerError, MAX_ERROR));

        return gemini.generate(systemInstruction(), prompt);
    }

    static String buildPrompt(
            Challenge challenge,
            String mode,
            int hintLevel,
            String question,
            String userCode,
            String compilerError) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("MODE: ").append(mode).append('\n');
        prompt.append("HINT LEVEL: ").append(hintLevel).append("/3\n\n");
        prompt.append("PUBLIC CHALLENGE CONTEXT\n");
        prompt.append("ID: ").append(challenge.getId()).append('\n');
        prompt.append("Title: ").append(challenge.getTitle()).append('\n');
        prompt.append("Type: ").append(challenge.getType()).append('\n');
        prompt.append("Difficulty: ").append(challenge.getDifficulty()).append('\n');
        prompt.append("Description: ").append(challenge.getDescription()).append('\n');

        if (challenge instanceof CTFChallenge ctf) {
            prompt.append("Category: ").append(ctf.getCategoryName()).append('\n');
            prompt.append("Hint cost: ").append(ctf.getHintCost()).append('\n');
            if (ctf.hasAttachment()) {
                prompt.append("Public attachment name: ").append(ctf.getAttachmentFileName()).append('\n');
            }
        } else if (challenge instanceof CPProblem cp) {
            prompt.append("Time limit: ").append(cp.getTimeLimitMillis()).append(" ms\n");
            prompt.append("Memory limit: ").append(cp.getMemoryLimitMb()).append(" MB\n");
        }

        if (!question.isBlank()) {
            prompt.append("\nUSER QUESTION\n").append(question).append('\n');
        }
        if (!userCode.isBlank()) {
            prompt.append("\nUSER CODE\n").append(userCode).append('\n');
        }
        if (!compilerError.isBlank()) {
            prompt.append("\nCOMPILER/RUNTIME ERROR\n").append(compilerError).append('\n');
        }

        prompt.append("\nRESPONSE RULES\n");
        if ("EXPLAIN".equals(mode)) {
            prompt.append("Explain the core concept and what the challenge is testing. Do not reveal a final flag or hidden answer.\n");
        } else if ("CODE_REVIEW".equals(mode)) {
            prompt.append("Review the user's code, identify likely issues, and suggest bounded fixes. Do not reveal hidden test outputs or a CTF flag.\n");
        } else {
            switch (hintLevel) {
                case 1 -> prompt.append("Give only a conceptual nudge: what topic or primitive should the learner recognize?\n");
                case 2 -> prompt.append("Give a concrete strategy with ordered reasoning, but stop before a final answer or flag.\n");
                default -> prompt.append("Give detailed pseudocode/debugging direction and checks, but do not provide a final CTF flag or hidden challenge answer.\n");
            }
        }

        return prompt.toString();
    }

    private static String systemInstruction() {
        return """
                You are Cyber-Algo AI Tutor for an educational CTF and competitive-programming sandbox.
                Help the learner understand concepts, debug their own work, and progress through the challenge.
                Treat all CTF activity as authorized and sandboxed. Do not provide instructions for attacking real-world
                systems, stealing credentials, deploying malware, evading monitoring, or targeting third parties.
                Never invent, request, infer, or reveal raw CTF flags, stored flag hashes, hidden testcase outputs,
                administrator credentials, database URIs, API keys, or other secrets.
                Keep answers concise, practical, and beginner-friendly. Use the requested progressive hint level.
                """;
    }

    private static String normalizeMode(String requestedMode) {
        String mode = requestedMode == null ? "HINT" : requestedMode.trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "HINT", "EXPLAIN", "CODE_REVIEW" -> mode;
            default -> throw new IllegalArgumentException("Unsupported AI tutor mode.");
        };
    }

    private static String limit(String value, int maxLength) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, maxLength);
    }
}
