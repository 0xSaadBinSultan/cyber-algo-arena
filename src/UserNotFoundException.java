/** Thrown when a user ID is absent from the contest registry. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String userId) {
        super("User not found: " + userId);
    }
}
