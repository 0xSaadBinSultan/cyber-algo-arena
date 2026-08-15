import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Team aggregate with score state used by the leaderboard comparator. */
public final class Team implements Persistable {

    public static final int CSV_FIELD_COUNT = 5;

    private final String id;
    private final String teamName;
    private final List<String> memberIds = new ArrayList<>();
    private int totalScore;
    private Instant lastSolveTime;

    public Team(String id, String teamName, Collection<String> memberIds, int totalScore, Instant lastSolveTime) {
        this.id = requireText(id, "id");
        this.teamName = requireText(teamName, "teamName");
        Objects.requireNonNull(memberIds, "memberIds must not be null").forEach(this::addMember);
        if (totalScore < 0) {
            throw new IllegalArgumentException("totalScore must not be negative");
        }
        this.totalScore = totalScore;
        this.lastSolveTime = lastSolveTime;
    }

    public Team(String id, String teamName) {
        this(id, teamName, List.of(), 0, null);
    }

    public void addMember(String userId) {
        String normalizedUserId = requireText(userId, "userId");
        if (!memberIds.contains(normalizedUserId)) {
            memberIds.add(normalizedUserId);
        }
    }

    public boolean removeMember(String userId) {
        return memberIds.remove(userId);
    }

    /** Applies an accepted solve. Accepted zero-point solves still update the tie-break timestamp. */
    public void recordSolve(int pointsAwarded, Instant solveTime) {
        if (pointsAwarded < 0) {
            throw new IllegalArgumentException("pointsAwarded must not be negative");
        }
        this.totalScore = Math.addExact(totalScore, pointsAwarded);
        this.lastSolveTime = Objects.requireNonNull(solveTime, "solveTime must not be null");
    }

    @Override
    public String getId() {
        return id;
    }

    public String getTeamName() {
        return teamName;
    }

    public List<String> getMemberIds() {
        return Collections.unmodifiableList(memberIds);
    }

    public int getTotalScore() {
        return totalScore;
    }

    /** Returns null before the team's first accepted solve. */
    public Instant getLastSolveTime() {
        return lastSolveTime;
    }

    @Override
    public String toCsvRow() {
        return CsvCodec.join(List.of(
                id,
                teamName,
                String.join(";", memberIds),
                String.valueOf(totalScore),
                lastSolveTime == null ? "" : lastSolveTime.toString()));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }
}
