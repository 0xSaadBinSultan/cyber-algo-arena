import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** In-memory real-time team ranking. Deterministic even when score and solve time tie. */
public final class Leaderboard {

    /** Higher score wins; earlier lastSolveTime breaks ties; team ID keeps ordering deterministic. */
    public static final Comparator<Team> RANKING_ORDER = Comparator
            .comparingInt(Team::getTotalScore)
            .reversed()
            .thenComparing(Team::getLastSolveTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Team::getId);

    private final List<Team> rankedTeams = new ArrayList<>();

    public synchronized void recalculate(Collection<Team> teams) {
        Objects.requireNonNull(teams, "teams must not be null");
        rankedTeams.clear();
        teams.forEach(team -> rankedTeams.add(Objects.requireNonNull(team, "team must not be null")));
        rankedTeams.sort(RANKING_ORDER);
    }

    public synchronized void update(Collection<Team> teams) {
        recalculate(teams);
    }

    public synchronized List<Team> getRanking() {
        return Collections.unmodifiableList(new ArrayList<>(rankedTeams));
    }

    public synchronized List<Team> getTop(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        return Collections.unmodifiableList(new ArrayList<>(rankedTeams.subList(0, Math.min(count, rankedTeams.size()))));
    }

    /** Returns one-based rank, or -1 when the team is absent. */
    public synchronized int getRank(String teamId) {
        if (teamId == null || teamId.trim().isEmpty()) {
            throw new IllegalArgumentException("teamId must not be null or blank");
        }
        for (int i = 0; i < rankedTeams.size(); i++) {
            if (rankedTeams.get(i).getId().equals(teamId)) {
                return i + 1;
            }
        }
        return -1;
    }
}
