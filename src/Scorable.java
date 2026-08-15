/**
 * Contract for objects that participate in contest scoring.
 */
public interface Scorable {

    /**
     * Returns the maximum points before penalties and deductions.
     *
     * @return positive base point value
     */
    int getBasePoints();

    /**
     * Calculates points after challenge-specific penalties.
     *
     * @param wrongAttempts number of failed attempts before the solve
     * @param hintsUsed number of paid hints consumed
     * @param elapsedMillis solve duration in milliseconds
     * @return non-negative awarded points
     */
    int calculateScore(int wrongAttempts, int hintsUsed, long elapsedMillis);
}
