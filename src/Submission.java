import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable submission record.
 * Tracks user, team, contest context, payload, timestamp, and evaluation status.
 */
public final class Submission implements Persistable {

    public static final int CSV_FIELD_COUNT = 12;

    private final String id;
    private final String contestId;
    private final String userId;
    private final String teamId;
    private final String challengeId;
    private final String payload;
    private final int wrongAttempts;
    private final int hintsUsed;
    private final Instant timestamp;
    private SubmissionResult.Status status;
    private int pointsAwarded;
    private String resultMessage;
    private Instant evaluatedAt;

    public Submission(
            String id,
            String contestId,
            String userId,
            String teamId,
            String challengeId,
            String payload,
            int wrongAttempts,
            int hintsUsed,
            Instant timestamp,
            SubmissionResult.Status status,
            int pointsAwarded,
            String resultMessage,
            Instant evaluatedAt) {
        this.id = requireArgument(id, "id");
        this.contestId = contestId != null ? contestId : "GLOBAL";
        this.userId = requireArgument(userId, "userId");
        this.teamId = requireArgument(teamId, "teamId");
        this.challengeId = requireArgument(challengeId, "challengeId");
        this.payload = payload != null ? payload : "";
        this.wrongAttempts = Math.max(0, wrongAttempts);
        this.hintsUsed = Math.max(0, hintsUsed);
        this.timestamp = timestamp != null ? timestamp : Instant.now();
        this.status = status != null ? status : SubmissionResult.Status.INVALID;
        this.pointsAwarded = pointsAwarded;
        this.resultMessage = resultMessage != null ? resultMessage : "";
        this.evaluatedAt = evaluatedAt != null ? evaluatedAt : this.timestamp;
    }

    public Submission(
            String id,
            String contestId,
            String userId,
            String teamId,
            String challengeId,
            String payload,
            int wrongAttempts,
            int hintsUsed,
            Instant timestamp) {
        this(id, contestId, userId, teamId, challengeId, payload, wrongAttempts, hintsUsed, timestamp,
                SubmissionResult.Status.PENDING, 0, "", timestamp);
    }

    public Submission(
            String id,
            String userId,
            String teamId,
            String challengeId,
            String payload,
            int wrongAttempts,
            int hintsUsed,
            Instant timestamp) {
        this(id, "GLOBAL", userId, teamId, challengeId, payload, wrongAttempts, hintsUsed, timestamp);
    }

    public Submission(
            String id,
            String teamId,
            String challengeId,
            String userId,
            String payload,
            int wrongAttempts,
            int hintsUsed,
            Instant timestamp,
            SubmissionResult result) {
        this(id, "GLOBAL", userId, teamId, challengeId, payload, wrongAttempts, hintsUsed, timestamp,
                result != null ? result.getStatus() : SubmissionResult.Status.INVALID,
                result != null ? result.getPointsAwarded() : 0,
                result != null ? result.getMessage() : "",
                result != null ? result.getEvaluatedAt() : timestamp);
    }

    public void applyResult(SubmissionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        this.status = result.getStatus();
        this.pointsAwarded = result.getPointsAwarded();
        this.resultMessage = result.getMessage();
        this.evaluatedAt = result.getEvaluatedAt() != null ? result.getEvaluatedAt() : Instant.now();
    }

    @Override
    public String getId() {
        return id;
    }

    public String getContestId() {
        return contestId;
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

    public SubmissionResult.Status getStatus() {
        return status;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public String getResultMessage() {
        return resultMessage;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public SubmissionResult getResult() {
        return new SubmissionResult(id, status, pointsAwarded, evaluatedAt, resultMessage);
    }

    @Override
    public String toCsvRow() {
        return CsvCodec.join(List.of(
                id,
                teamId,
                challengeId,
                timestamp.toString(),
                status.name(),
                String.valueOf(pointsAwarded),
                userId,
                payload,
                String.valueOf(wrongAttempts),
                String.valueOf(hintsUsed),
                evaluatedAt != null ? evaluatedAt.toString() : "",
                resultMessage));
    }

    private static String requireArgument(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value.trim();
    }
}
