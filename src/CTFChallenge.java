import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * CTF challenge that validates raw flags through SHA-256 hex comparison.
 * The clear-text flag is never retained by this class.
 */
public final class CTFChallenge extends Challenge {

    private static final int WRONG_ATTEMPT_PENALTY = 10;

    private final String category;
    private final String flagHash;
    private final int hintCost;

    public CTFChallenge(
            String id,
            String title,
            int basePoints,
            Difficulty difficulty,
            String category,
            String flagHash,
            int hintCost) {
        super(id, title, basePoints, difficulty);
        this.category = requireText(category, "category");
        this.flagHash = normalizeHash(flagHash);
        this.hintCost = requireNonNegative(hintCost, "hintCost");
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
        return new String[] {category, flagHash, String.valueOf(hintCost)};
    }

    public String getCategory() {
        return category;
    }

    /** Returns only the stored digest; no raw-flag accessor exists by design. */
    public String getFlagHash() {
        return flagHash;
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
}
