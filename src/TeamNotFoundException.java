/** Thrown when a team ID is absent from the contest registry. */
public class TeamNotFoundException extends RuntimeException {

    public TeamNotFoundException(String teamId) {
        super("Team not found: " + teamId);
    }
}
