import java.time.Instant;
import java.util.*;

/**
 * Team entity representing a competitive group.
 * Supports team password verification, captain identification, member management,
 * and contest scoring.
 */
public final class Team implements Persistable {

    public static final int CSV_FIELD_COUNT = 5;

    private final String id;
    private final String teamName;
    private final String teamPasswordHash;
    private String captainUserId;
    private final Set<String> memberUserIds;
    private int totalScore;
    private Instant lastSolveTime;
    private final Instant createdAt;

    public Team(
            String id,
            String teamName,
            String teamPasswordHash,
            String captainUserId,
            Collection<String> memberUserIds,
            int totalScore,
            Instant lastSolveTime,
            Instant createdAt) {
        this.id = requireArgument(id, "id");
        this.teamName = requireArgument(teamName, "teamName");
        this.teamPasswordHash = teamPasswordHash != null ? teamPasswordHash : "";
        this.captainUserId = captainUserId;
        this.memberUserIds = new LinkedHashSet<>(memberUserIds != null ? memberUserIds : List.of());
        this.totalScore = Math.max(0, totalScore);
        this.lastSolveTime = lastSolveTime;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public Team(String id, String teamName, Collection<String> memberUserIds, int totalScore, Instant lastSolveTime) {
        this(id, teamName, "", memberUserIds != null && !memberUserIds.isEmpty() ? memberUserIds.iterator().next() : null,
                memberUserIds, totalScore, lastSolveTime, Instant.now());
    }

    public Team(String id, String teamName) {
        this(id, teamName, List.of(), 0, null);
    }

    public boolean verifyPassword(String rawPassword) {
        if (teamPasswordHash == null || teamPasswordHash.isBlank()) {
            return true; // No password required
        }
        if (rawPassword == null) {
            return false;
        }
        String candidateHash = CTFChallenge.sha256Hex(rawPassword);
        return candidateHash.equals(teamPasswordHash);
    }

    public synchronized void addMember(String userId) {
        if (userId != null && !userId.isBlank()) {
            memberUserIds.add(userId.trim());
            if (captainUserId == null) {
                captainUserId = userId.trim();
            }
        }
    }

    public boolean isMember(String userId) {
        return userId != null && memberUserIds.contains(userId.trim());
    }

    public synchronized void applyScore(int pointsAwarded, Instant solveTime) {
        if (pointsAwarded > 0) {
            this.totalScore += pointsAwarded;
            this.lastSolveTime = Objects.requireNonNull(solveTime, "solveTime must not be null");
        }
    }

    public synchronized void setScore(int score, Instant solveTime) {
        this.totalScore = Math.max(0, score);
        this.lastSolveTime = solveTime;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getTeamName() {
        return teamName;
    }

    public String getTeamPasswordHash() {
        return teamPasswordHash;
    }

    public String getCaptainUserId() {
        return captainUserId;
    }

    public void setCaptainUserId(String captainUserId) {
        this.captainUserId = captainUserId;
    }

    public Set<String> getMemberUserIds() {
        return Collections.unmodifiableSet(memberUserIds);
    }

    public int getTotalScore() {
        return totalScore;
    }

    public Instant getLastSolveTime() {
        return lastSolveTime;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toCsvRow() {
        return CsvCodec.join(List.of(
                id,
                teamName,
                String.join(";", memberUserIds),
                String.valueOf(totalScore),
                lastSolveTime == null ? "" : lastSolveTime.toString()));
    }

    private static String requireArgument(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value.trim();
    }
}
