import java.time.Instant;
import java.util.*;

/**
 * Contest entity representing a competition event (CTF / CP).
 */
public final class Contest {

    private final String id;
    private final String title;
    private final String description;
    private final Instant startTime;
    private final Instant endTime;
    private boolean isRunning;
    private final Set<String> registeredTeamIds;

    public Contest(
            String id,
            String title,
            String description,
            Instant startTime,
            Instant endTime,
            boolean isRunning,
            Collection<String> registeredTeamIds) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.description = description != null ? description : "";
        this.startTime = startTime != null ? startTime : Instant.now();
        this.endTime = endTime != null ? endTime : Instant.now().plusSeconds(86400);
        this.isRunning = isRunning;
        this.registeredTeamIds = new LinkedHashSet<>(registeredTeamIds != null ? registeredTeamIds : List.of());
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public Set<String> getRegisteredTeamIds() {
        return Collections.unmodifiableSet(registeredTeamIds);
    }

    public void registerTeam(String teamId) {
        if (teamId != null && !teamId.isBlank()) {
            registeredTeamIds.add(teamId);
        }
    }

    private boolean scoreboardFrozen = false;
    private long freezeTimestamp = 0;

    public boolean isScoreboardFrozen() {
        return scoreboardFrozen;
    }

    public long getFreezeTimestamp() {
        return freezeTimestamp;
    }

    public void setFreezeTimestamp(long timestamp) {
        this.freezeTimestamp = timestamp;
    }

    public void toggleFreeze(boolean freeze) {
        this.scoreboardFrozen = freeze;
        if (freeze) {
            this.freezeTimestamp = System.currentTimeMillis();
        }
    }
}
