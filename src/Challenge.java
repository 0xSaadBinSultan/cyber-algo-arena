import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Abstract base for every contest challenge.
 * Owns common identity, scoring metadata, CSV serialization, and validation.
 */
public abstract class Challenge implements Persistable, Scorable {

    public static final int CSV_FIELD_COUNT = 9;

    /** Normalized difficulty values persisted in challenges.csv. */
    public enum Difficulty {
        EASY,
        MEDIUM,
        HARD;

        public static Difficulty fromToken(String token) {
            String normalized = requireText(token, "difficulty").trim().toUpperCase(Locale.ROOT);
            try {
                return Difficulty.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unsupported difficulty: " + token, ex);
            }
        }
    }

    private final String id;
    private final String title;
    private final int basePoints;
    private final Difficulty difficulty;
    private String description = "";

    protected Challenge(String id, String title, int basePoints, Difficulty difficulty) {
        this.id = requireText(id, "id");
        this.title = requireText(title, "title");
        if (basePoints <= 0) {
            throw new IllegalArgumentException("basePoints must be positive");
        }
        this.basePoints = basePoints;
        this.difficulty = Objects.requireNonNull(difficulty, "difficulty must not be null");
    }

    /** Returns the polymorphic CSV discriminator: CTF or CP. */
    public abstract String getType();

    /**
     * Evaluates a type-specific submission payload.
     * CTF consumes a raw flag; CP consumes a candidate output directory path.
     */
    public abstract boolean evaluate(String submissionPayload) throws InvalidSubmissionException;

    /** Returns a non-sensitive hint safe to display after confirmation. */
    public abstract String getHintText();

    /** Returns points deducted per hint; zero means hints are free or unsupported. */
    public abstract int getHintCost();

    /** Supplies EXTRA_PARAM_1..4 for the concrete challenge type. */
    protected abstract String[] getTypeSpecificCsvFields();

    @Override
    public final String toCsvRow() {
        String[] specificFields = getTypeSpecificCsvFields();
        if (specificFields == null || specificFields.length != 4) {
            throw new IllegalStateException("Challenge must provide exactly four type-specific CSV fields");
        }

        List<String> fields = new ArrayList<>(CSV_FIELD_COUNT);
        fields.add(getType());
        fields.add(id);
        fields.add(title);
        fields.add(String.valueOf(basePoints));
        fields.add(difficulty.name());
        fields.add(specificFields[0]);
        fields.add(specificFields[1]);
        fields.add(specificFields[2]);
        fields.add(specificFields[3]);

        return CsvCodec.join(fields);
    }

    @Override
    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public int getBasePoints() {
        return basePoints;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description != null ? description : "";
    }

    protected static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    protected static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    protected static long requireNonNegative(long value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return value;
    }

    protected static int clampScore(long score) {
        return (int) Math.max(0L, score);
    }
}
