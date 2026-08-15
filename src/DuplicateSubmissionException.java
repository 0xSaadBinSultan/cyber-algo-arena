/** Thrown when a submission ID is replayed or reused. */
public class DuplicateSubmissionException extends RuntimeException {

    public DuplicateSubmissionException(String submissionId) {
        super("Duplicate submission: " + submissionId);
    }
}
