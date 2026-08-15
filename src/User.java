import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Contest account. Only password hashes are stored; clear-text credentials never enter this entity. */
public final class User implements Persistable {

    public static final int CSV_FIELD_COUNT = 5;

    public enum Role {
        ADMIN,
        PLAYER;

        public static Role fromToken(String token) {
            if (token == null || token.trim().isEmpty()) {
                throw new IllegalArgumentException("role must not be null or blank");
            }
            try {
                return Role.valueOf(token.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Unsupported user role: " + token, ex);
            }
        }
    }

    private final String id;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private String teamId;

    public User(String id, String username, String passwordHash, Role role, String teamId) {
        this.id = requireText(id, "id");
        this.username = requireText(username, "username");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.teamId = normalizeOptionalTeamId(teamId);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public void assignToTeam(String teamId) {
        this.teamId = requireText(teamId, "teamId");
    }

    public void removeFromTeam() {
        this.teamId = null;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    /** Returns null for an admin or unassigned player. */
    public String getTeamId() {
        return teamId;
    }

    @Override
    public String toCsvRow() {
        return CsvCodec.join(List.of(id, username, passwordHash, role.name(), teamId == null ? "" : teamId));
    }

    private static String normalizeOptionalTeamId(String teamId) {
        return teamId == null || teamId.trim().isEmpty() ? null : teamId;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }
}
