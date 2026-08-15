import java.time.Instant;
import java.util.Objects;

/**
 * Maps a player's participation in a specific contest under a specific team.
 * Enforces the CTFtime invariant: 1 player belongs to at most 1 team per contest.
 */
public final class ContestParticipation {

    private final String contestId;
    private final String teamId;
    private final String userId;
    private final Instant joinedAt;

    public ContestParticipation(String contestId, String teamId, String userId, Instant joinedAt) {
        this.contestId = Objects.requireNonNull(contestId, "contestId must not be null");
        this.teamId = Objects.requireNonNull(teamId, "teamId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.joinedAt = joinedAt != null ? joinedAt : Instant.now();
    }

    public String getContestId() {
        return contestId;
    }

    public String getTeamId() {
        return teamId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
