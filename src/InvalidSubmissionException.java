/**
 * Base unchecked exception for malformed or unacceptable contest submissions.
 */
public class InvalidSubmissionException extends RuntimeException {

    public InvalidSubmissionException(String message) {
        super(message);
    }

    public InvalidSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
