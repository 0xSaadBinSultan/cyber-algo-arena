/**
 * Thrown when a CTF flag payload is empty, malformed, or cannot be evaluated.
 */
public class InvalidFlagException extends InvalidSubmissionException {

    public InvalidFlagException(String message) {
        super(message);
    }

    public InvalidFlagException(String message, Throwable cause) {
        super(message, cause);
    }
}
