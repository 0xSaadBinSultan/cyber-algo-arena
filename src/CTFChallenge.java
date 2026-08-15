import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Objects;

/**
 * CTF challenge that validates raw flags through SHA-256 hex comparison.
 * The clear-text flag is never retained by this class.
 * Supports categorization (Crypto, Web, Reverse Engineering, Pwn, OSINT, Misc)
 * and optional challenge file attachments.
 */
public final class CTFChallenge extends Challenge {

    private static final int WRONG_ATTEMPT_PENALTY = 10;

    /** Supported CTF challenge categories. */
    public enum Category {
        CRYPTO,
        WEB,
        REVERSE_ENGINEERING,
        PWN,
        OSINT,
        FORENSICS,
        GENERAL_SKILLS,
        MISC;

        public static Category fromToken(String token) {
            if (token == null || token.trim().isEmpty()) {
                return MISC;
            }
            String normalized = token.trim().toUpperCase(Locale.ROOT).replace(" ", "_").replace("-", "_");
            if (normalized.equals("REVERSE") || normalized.equals("REV")) {
                return REVERSE_ENGINEERING;
            }
            if (normalized.equals("CRYPTOGRAPHY")) {
                return CRYPTO;
            }
            if (normalized.equals("WEB_SECURITY")) {
                return WEB;
            }
            if (normalized.equals("SYSTEMS_SECURITY")) {
                return PWN;
            }
            try {
                return Category.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                return MISC;
            }
        }
    }

    private final Category category;
    private final String flagHash;
    private final int hintCost;
    private final String attachmentFileName;

    public CTFChallenge(
            String id,
            String title,
            int basePoints,
            Difficulty difficulty,
            Category category,
            String flagHash,
            int hintCost,
            String attachmentFileName) {
        super(id, title, basePoints, difficulty);
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.flagHash = normalizeHash(flagHash);
        this.hintCost = requireNonNegative(hintCost, "hintCost");
        this.attachmentFileName = normalizeAttachment(attachmentFileName);
    }

    public CTFChallenge(
            String id,
            String title,
            int basePoints,
            Difficulty difficulty,
            String category,
            String flagHash,
            int hintCost,
            String attachmentFileName) {
        this(id, title, basePoints, difficulty, Category.fromToken(category), flagHash, hintCost, attachmentFileName);
    }

    public CTFChallenge(
            String id,
            String title,
            int basePoints,
            Difficulty difficulty,
            String category,
            String flagHash,
            int hintCost) {
        this(id, title, basePoints, difficulty, Category.fromToken(category), flagHash, hintCost, null);
    }

    @Override
    public String getType() {
        return "CTF";
    }

    /**
     * Hashes the submitted flag and compares digest bytes in constant time.
     *
     * @param submissionPayload raw flag text
     * @return true when the submitted flag matches the stored SHA-256 digest
     */
    @Override
    public boolean evaluate(String submissionPayload) throws InvalidFlagException {
        if (submissionPayload == null || submissionPayload.isEmpty()) {
            throw new InvalidFlagException("Flag must not be null or empty");
        }

        String submittedHash = sha256Hex(submissionPayload);
        return MessageDigest.isEqual(
                flagHash.getBytes(StandardCharsets.UTF_8),
                submittedHash.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int calculateScore(int wrongAttempts, int hintsUsed, long elapsedMillis) {
        requireNonNegative(wrongAttempts, "wrongAttempts");
        requireNonNegative(hintsUsed, "hintsUsed");
        requireNonNegative(elapsedMillis, "elapsedMillis");

        long penalty = (long) wrongAttempts * WRONG_ATTEMPT_PENALTY + (long) hintsUsed * hintCost;
        return clampScore(getBasePoints() - penalty);
    }

    @Override
    protected String[] getTypeSpecificCsvFields() {
        return new String[] {
            category.name(),
            flagHash,
            String.valueOf(hintCost),
            attachmentFileName == null ? "" : attachmentFileName
        };
    }

    public Category getCategory() {
        return category;
    }

    public String getCategoryName() {
        return category.name();
    }

    /** Returns only the stored digest; no raw-flag accessor exists by design. */
    public String getFlagHash() {
        return flagHash;
    }

    public String getAttachmentFileName() {
        return attachmentFileName;
    }

    public boolean hasAttachment() {
        return attachmentFileName != null && !attachmentFileName.isBlank();
    }

    @Override
    public String getHintText() {
        return "Category signal: " + category + ". Validate the exact flag format before hashing.";
    }

    @Override
    public int getHintCost() {
        return hintCost;
    }

    /** Produces the lowercase SHA-256 hex encoding used by wrangle_data.py. */
    public static String sha256Hex(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                hex.append(Character.forDigit((current >> 4) & 0x0F, 16));
                hex.append(Character.forDigit(current & 0x0F, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", ex);
        }
    }

    private static String normalizeHash(String flagHash) {
        String normalized = requireText(flagHash, "flagHash").trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("flagHash must be a 64-character SHA-256 hex string");
        }
        return normalized;
    }

    private static String normalizeAttachment(String attachment) {
        if (attachment == null || attachment.trim().isEmpty()) {
            return null;
        }
        return attachment.trim();
    }
}
