package com.cyberalgo;

/** Original, spoiler-free teaching material. Never presented as a generated AI answer. */
final class CtfStudyGuide {
    private CtfStudyGuide() {}

    static String explain(Challenge challenge, String mode, int level) {
        if (!(challenge instanceof CTFChallenge ctf)) {
            throw new IllegalArgumentException("Study guides are available for CTF labs only");
        }
        String concept;
        String strategy;
        switch (ctf.getCategory()) {
            case CRYPTO -> {
                concept = "Encoding changes how data is represented; encryption normally requires a key. Start by identifying which one the evidence uses.";
                strategy = "Inspect the alphabet, padding and length of the supplied text. Try a matching decoder, inspect the result, and verify that encoding it again reproduces the evidence.";
            }
            case FORENSICS -> {
                concept = "Forensics builds an explanation from evidence. In a log, timestamps, request paths, status codes and query parameters each tell part of the story.";
                strategy = "Work on a copy of the supplied attachment. Compare ordinary records with unusual ones, follow the path named in the story, and inspect encoded query values one layer at a time.";
            }
            case REVERSE_ENGINEERING -> {
                concept = "A checker transforms an input and compares it with an expected value. Understanding the transformation is more useful than guessing inputs.";
                strategy = "Trace the checker with a short harmless example. Write down each transformation in order, then consider how to undo those steps in reverse order. Check punctuation and character order carefully.";
            }
            case WEB -> {
                concept = "HTTP headers and cookies can carry structured text. An encoded value is not necessarily encrypted or trusted.";
                strategy = "Separate the cookie name, its value, and the attributes after semicolons. Examine only the supplied training evidence, identify the value's encoding, and inspect any decoded structure locally.";
            }
            default -> {
                concept = "CTF puzzles reward careful observation. Separate the story, the evidence you have, and the exact result the lab asks you to recover.";
                strategy = "List the supplied clues. Test one interpretation at a time on a copy of the evidence and record what each step changes. Stay within the lab's supplied material.";
            }
        }
        if ("CODE_REVIEW".equals(mode)) {
            return "## Review checklist\nLive AI code review is unavailable. This checklist does not analyze your code.\n\n"
                    + concept + "\n\nUse a small known example to check each transformation, preserve whitespace where it matters, and compare the final format with the lab instructions.";
        }
        if ("EXPLAIN".equals(mode)) return "## Core concept\n" + concept + "\n\n## Investigation plan\n" + strategy;
        if (level <= 1) return "## Concept nudge\n" + concept;
        return "## Investigation strategy\n" + strategy + (level >= 3
                ? "\n\n## Before submitting\nCheck for extra spaces or newlines, confirm that every step follows from the supplied evidence, and keep the required flag punctuation exactly as shown in the instructions."
                : "");
    }
}
