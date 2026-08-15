/**
 * Checked exception raised when a persisted CSV record violates the file contract.
 */
public class CorruptedFileException extends Exception {

    public CorruptedFileException(String message) {
        super(message);
    }

    public CorruptedFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
