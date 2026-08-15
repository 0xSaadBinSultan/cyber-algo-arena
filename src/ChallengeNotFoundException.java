/**
 * Thrown when a submission references a challenge ID absent from the contest registry.
 */
public class ChallengeNotFoundException extends RuntimeException {

    public ChallengeNotFoundException(String challengeId) {
        super("Challenge not found: " + challengeId);
    }
}
