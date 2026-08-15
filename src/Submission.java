import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One user attempt against one challenge. Result fields transition exactly once from PENDING. */
public final class Submission implements Persistable {

    public static final int CSV_FIELD_COUNT = 12;

    private final String id;
    private final String userId;
    private final String teamId;
    private final String challengeId;
    private final String payload;
    private final int wrongAttempts;
    private final int hintsUsed;
    private final Instant timestamp;
    private SubmissionResult result;

    public Submission(
            String id,
            String userId,
            String teamId,
            String challengeId,
            String payload,
            int wrongAttempts,
            int hintsUsed,
            Instant timestamp) {
        this(id, userId, teamId, challengeId, payload, wrongAttempts, hintsUsed, timestamp, SubmissionResult.pending(id));
    }

    public Submission(
            String id,
            String userId,
            String teamId,
            String challengeId,
            String payload,
            int wrongAttempts,
            int hintsUsed,
            Instant timestamp,
            SubmissionResult result) {
        this.id = requireText(id, "id");
        this.userId = requireText(userId, "userId");
        this.teamId = requireText(teamId, "teamId");
        this.challengeId = requireText(challengeId, "challengeId");
        this.payload = requireText(payload, "payload");
        if (wrongAttempts < 0) {
            throw new IllegalArgumentException("wrongAttempts must not be negative");
        }
        if (hintsUsed < 0) {
            throw new IllegalArgumentException("hintsUsed must not be negative");
        }
        this.wrongAttempts = wrongAttempts;
        this.hintsUsed = hintsUsed;
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.result = Objects.requireNonNull(result, "result must not be null");
        if (!result.getId().equals(this.id)) {
            throw new IllegalArgumentException("result submissionId does not match submission id");
        }
    }

    public void markAccepted(int pointsAwarded, Instant evaluatedAt) {
        transition(SubmissionResult.accepted(id, pointsAwarded, evaluatedAt));
    }

    public void markWrongAnswer(Instant evaluatedAt) {
        transition(SubmissionResult.wrongAnswer(id, evaluatedAt));
    }

    public void markInvalid(Instant evaluatedAt, String message) {
        transition(SubmissionResult.invalid(id, evaluatedAt, message));
    }

    private void transition(SubmissionResult nextResult) {
        if (result.getStatus() != SubmissionResult.Status.PENDING) {
            throw new IllegalStateException("Submission has already been evaluated: " + id);
        }
        result = nextResult;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getChallengeId() {
        return challengeId;
    }

    public String getPayload() {
        return payload;
    }

    public int getWrongAttempts() {
        return wrongAttempts;
    }

    public int getHintsUsed() {
        return hintsUsed;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    /** Returns a copy with engine-derived attempt and hint counts while preserving identity/result. */
    public Submission withAttemptAndHintCounts(int newWrongAttempts, int newHintsUsed) {
        if (newWrongAttempts < 0) {
            throw new IllegalArgumentException("newWrongAttempts must not be negative");
        }
        if (newHintsUsed < 0) {
            throw new IllegalArgumentException("newHintsUsed must not be negative");
        }
        if (newWrongAttempts == wrongAttempts && newHintsUsed == hintsUsed) {
            return this;
        }
        return new Submission(
                id,
                userId,
                teamId,
                challengeId,
                payload,
                newWrongAttempts,
                newHintsUsed,
                timestamp,
                result);
    }

    public SubmissionResult getResult() {
        return result;
    }

    public SubmissionResult.Status getStatus() {
        return result.getStatus();
    }

    /**
     * First six columns preserve the PRD contract:
     * SUBMISSION_ID,TEAM_ID,CHALLENGE_ID,TIMESTAMP,STATUS,POINTS_AWARDED.
     * Remaining columns retain user, payload, attempt, hint, and audit data.
     */
    @Override
    public String toCsvRow() {
        List<String> resultFields = result.toCsvFields();
        return CsvCodec.join(List.of(
                id,
                teamId,
                challengeId,
                timestamp.toString(),
                resultFields.get(0),
                resultFields.get(1),
                userId,
                payload,
                String.valueOf(wrongAttempts),
                String.valueOf(hintsUsed),
                resultFields.get(2),
                resultFields.get(3)));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }
}
