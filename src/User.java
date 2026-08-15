import java.time.Instant;
import java.util.*;

/**
 * User account domain model.
 * Supports roles (PLAYER, ADMIN), profile stats (totalScore, solvesCount, category breakdown),
 * and personal solve history.
 */
public final class User implements Persistable {

    public static final int CSV_FIELD_COUNT = 5;

    public enum Role {
        PLAYER,
        ADMIN;

        public static Role fromToken(String token) {
            String normalized = requireArgument(token, "role").trim().toUpperCase(Locale.ROOT);
            try {
                return Role.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unsupported role: " + token, ex);
            }
        }
    }

    private final String id;
    private final String username;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private String teamId;
    private final Instant createdAt;
    private int personalScore;
    private int solvesCount;
    private final Map<String, Integer> categoryBreakdown;
    private final Set<String> solvedChallengeIds;

    public User(
            String id,
            String username,
            String email,
            String passwordHash,
            Role role,
            String teamId,
            Instant createdAt,
            int personalScore,
            int solvesCount,
            Map<String, Integer> categoryBreakdown,
            Collection<String> solvedChallengeIds) {
        this.id = requireArgument(id, "id");
        this.username = requireArgument(username, "username");
        this.email = (email != null && !email.isBlank()) ? email.trim() : username.toLowerCase() + "@cyberarena.local";
        this.passwordHash = requireArgument(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.teamId = normalizeTeamId(teamId);
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.personalScore = Math.max(0, personalScore);
        this.solvesCount = Math.max(0, solvesCount);
        this.categoryBreakdown = new LinkedHashMap<>(categoryBreakdown != null ? categoryBreakdown : Map.of());
        this.solvedChallengeIds = new LinkedHashSet<>(solvedChallengeIds != null ? solvedChallengeIds : List.of());
    }

    public User(String id, String username, String passwordHash, Role role, String teamId) {
        this(id, username, null, passwordHash, role, teamId, Instant.now(), 0, 0, new LinkedHashMap<>(), new LinkedHashSet<>());
    }

    public User(String id, String username, String email, String passwordHash, Role role, String teamId) {
        this(id, username, email, passwordHash, role, teamId, Instant.now(), 0, 0, new LinkedHashMap<>(), new LinkedHashSet<>());
    }

    public synchronized void recordSolve(String challengeId, String category, int points) {
        if (challengeId != null && !solvedChallengeIds.contains(challengeId)) {
            solvedChallengeIds.add(challengeId);
            solvesCount++;
            personalScore += Math.max(0, points);
            String cat = (category != null && !category.isBlank()) ? category.toUpperCase(Locale.ROOT) : "MISC";
            categoryBreakdown.put(cat, categoryBreakdown.getOrDefault(cat, 0) + 1);
        }
    }

    public boolean isSolved(String challengeId) {
        return challengeId != null && solvedChallengeIds.contains(challengeId);
    }

    public boolean verifyPassword(String rawPassword) {
        if (rawPassword == null) {
            return false;
        }
        String candidateHash = CTFChallenge.sha256Hex(rawPassword);
        return candidateHash.equals(passwordHash);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = normalizeTeamId(teamId);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getPersonalScore() {
        return personalScore;
    }

    public int getSolvesCount() {
        return solvesCount;
    }

    public Map<String, Integer> getCategoryBreakdown() {
        return Collections.unmodifiableMap(categoryBreakdown);
    }

    public Set<String> getSolvedChallengeIds() {
        return Collections.unmodifiableSet(solvedChallengeIds);
    }

    @Override
    public String toCsvRow() {
        return CsvCodec.join(List.of(
                id,
                username,
                passwordHash,
                role.name(),
                teamId == null ? "" : teamId));
    }

    private static String requireArgument(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value.trim();
    }

    private static String normalizeTeamId(String teamId) {
        if (teamId == null || teamId.trim().isEmpty()) {
            return null;
        }
        return teamId.trim();
    }
}
