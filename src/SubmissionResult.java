import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Immutable evaluation outcome attached to one submission. */
public final class SubmissionResult implements Persistable {

    public enum Status {
        PENDING,
        ACCEPTED,
        WRONG_ANSWER,
        INVALID;

        public static Status fromToken(String token) {
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalArgumentException("status must not be null or blank");
            }
            try {
                return Status.valueOf(token.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unsupported submission status: " + token, ex);
            }
        }
    }

    private final String submissionId;
    private final Status status;
    private final int pointsAwarded;
    private final Instant evaluatedAt;
    private final String message;

    public SubmissionResult(
            String submissionId,
            Status status,
            int pointsAwarded,
            Instant evaluatedAt,
            String message) {
        this.submissionId = requireText(submissionId, "submissionId");
        this.status = Objects.requireNonNull(status, "status must not be null");
        if (pointsAwarded < 0) {
            throw new IllegalArgumentException("pointsAwarded must not be negative");
        }
        this.pointsAwarded = pointsAwarded;
        this.evaluatedAt = evaluatedAt;
        this.message = message == null ? "" : message;
    }

    public static SubmissionResult pending(String submissionId) {
        return new SubmissionResult(submissionId, Status.PENDING, 0, null, "");
    }

    public static SubmissionResult accepted(String submissionId, int pointsAwarded, Instant evaluatedAt) {
        return new SubmissionResult(submissionId, Status.ACCEPTED, pointsAwarded, evaluatedAt, "Accepted");
    }

    public static SubmissionResult wrongAnswer(String submissionId, Instant evaluatedAt) {
        return new SubmissionResult(submissionId, Status.WRONG_ANSWER, 0, evaluatedAt, "Wrong answer");
    }

    public static SubmissionResult invalid(String submissionId, Instant evaluatedAt, String message) {
        return new SubmissionResult(submissionId, Status.INVALID, 0, evaluatedAt, message);
    }

    public boolean isAccepted() {
        return status == Status.ACCEPTED;
    }

    @Override
    public String getId() {
        return submissionId;
    }

    public Status getStatus() {
        return status;
    }

    public int getPointsAwarded() {
        return pointsAwarded;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public String getMessage() {
        return message;
    }

    /** Serializes only result fields; Submission owns the complete persisted record. */
    @Override
    public String toCsvRow() {
        return CsvCodec.join(toCsvFields());
    }

    List<String> toCsvFields() {
        return List.of(
                status.name(),
                String.valueOf(pointsAwarded),
                evaluatedAt == null ? "" : evaluatedAt.toString(),
                message);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }
}
