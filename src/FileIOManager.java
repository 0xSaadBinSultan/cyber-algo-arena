import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * CSV persistence boundary for all contest registries.
 * Performs schema validation and polymorphic challenge deserialization.
 */
public final class FileIOManager {

    public static final String CHALLENGES_CSV_HEADER = "TYPE,ID,TITLE,POINTS,DIFFICULTY,PARAM1,PARAM2,PARAM3,PARAM4";
    public static final String USERS_CSV_HEADER = "USER_ID,USERNAME,PASSWORD_HASH,ROLE,TEAM_ID";
    public static final String TEAMS_CSV_HEADER = "TEAM_ID,TEAM_NAME,MEMBER_IDS,TOTAL_SCORE,LAST_SOLVE_TIME";
    public static final String SUBMISSIONS_CSV_HEADER =
            "SUBMISSION_ID,TEAM_ID,CHALLENGE_ID,TIMESTAMP,STATUS,POINTS_AWARDED,"
                    + "USER_ID,PAYLOAD,WRONG_ATTEMPTS,HINTS_USED,EVALUATED_AT,RESULT_MESSAGE";

    /** Backward-compatible alias from Phase 1. */
    public static final String CSV_HEADER = CHALLENGES_CSV_HEADER;

    private final Path challengesCsvPath;
    private final Path usersCsvPath;
    private final Path teamsCsvPath;
    private final Path submissionsCsvPath;

    /** Derives sibling CSV paths beside challenges.csv. */
    public FileIOManager(Path challengesCsvPath) {
        this(
                challengesCsvPath,
                siblingPath(challengesCsvPath, "users.csv"),
                siblingPath(challengesCsvPath, "teams.csv"),
                siblingPath(challengesCsvPath, "submissions.csv"));
    }

    public FileIOManager(Path challengesCsvPath, Path usersCsvPath, Path teamsCsvPath, Path submissionsCsvPath) {
        this.challengesCsvPath = Objects.requireNonNull(challengesCsvPath, "challengesCsvPath must not be null");
        this.usersCsvPath = Objects.requireNonNull(usersCsvPath, "usersCsvPath must not be null");
        this.teamsCsvPath = Objects.requireNonNull(teamsCsvPath, "teamsCsvPath must not be null");
        this.submissionsCsvPath = Objects.requireNonNull(submissionsCsvPath, "submissionsCsvPath must not be null");
    }

    public List<Challenge> loadChallenges() throws IOException, CorruptedFileException {
        return loadRecordsFlex(
                challengesCsvPath,
                "challenges.csv",
                8,
                Challenge.CSV_FIELD_COUNT,
                "TYPE",
                "ID",
                FileIOManager::deserializeChallenge);
    }

    public Challenge loadChallengeById(String challengeId) throws IOException, CorruptedFileException {
        String normalizedId = requireArgument(challengeId, "challengeId");
        return loadChallenges().stream()
                .filter(challenge -> challenge.getId().equals(normalizedId))
                .findFirst()
                .orElseThrow(() -> new ChallengeNotFoundException(normalizedId));
    }

    public void saveChallenges(Collection<? extends Challenge> challenges) throws IOException {
        saveRecords(challengesCsvPath, CHALLENGES_CSV_HEADER, challenges);
    }

    public void appendChallenge(Challenge challenge) throws IOException, CorruptedFileException {
        Objects.requireNonNull(challenge, "challenge must not be null");
        List<Challenge> challenges = loadChallenges();
        challenges.add(challenge);
        saveChallenges(challenges);
    }

    public List<User> loadUsers() throws IOException, CorruptedFileException {
        return loadRecords(
                usersCsvPath,
                "users.csv",
                User.CSV_FIELD_COUNT,
                "USER_ID",
                "USERNAME",
                FileIOManager::deserializeUser);
    }

    public User loadUserById(String userId) throws IOException, CorruptedFileException {
        String normalizedId = requireArgument(userId, "userId");
        return loadUsers().stream()
                .filter(user -> user.getId().equals(normalizedId))
                .findFirst()
                .orElseThrow(() -> new UserNotFoundException(normalizedId));
    }

    public void saveUsers(Collection<? extends User> users) throws IOException {
        saveRecords(usersCsvPath, USERS_CSV_HEADER, users);
    }

    public List<Team> loadTeams() throws IOException, CorruptedFileException {
        return loadRecords(
                teamsCsvPath,
                "teams.csv",
                Team.CSV_FIELD_COUNT,
                "TEAM_ID",
                "TEAM_NAME",
                FileIOManager::deserializeTeam);
    }

    public Team loadTeamById(String teamId) throws IOException, CorruptedFileException {
        String normalizedId = requireArgument(teamId, "teamId");
        return loadTeams().stream()
                .filter(team -> team.getId().equals(normalizedId))
                .findFirst()
                .orElseThrow(() -> new TeamNotFoundException(normalizedId));
    }

    public void saveTeams(Collection<? extends Team> teams) throws IOException {
        saveRecords(teamsCsvPath, TEAMS_CSV_HEADER, teams);
    }

    public List<Submission> loadSubmissions() throws IOException, CorruptedFileException {
        return loadRecords(
                submissionsCsvPath,
                "submissions.csv",
                Submission.CSV_FIELD_COUNT,
                "SUBMISSION_ID",
                "TEAM_ID",
                FileIOManager::deserializeSubmission);
    }

    public void saveSubmissions(Collection<? extends Submission> submissions) throws IOException {
        saveRecords(submissionsCsvPath, SUBMISSIONS_CSV_HEADER, submissions);
    }

    /** Appends one evaluated submission without rewriting the full audit log. */
    public void appendSubmission(Submission submission) throws IOException {
        Objects.requireNonNull(submission, "submission must not be null");
        Path parent = submissionsCsvPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        boolean writeHeader = Files.notExists(submissionsCsvPath) || Files.size(submissionsCsvPath) == 0L;
        try (BufferedWriter writer = Files.newBufferedWriter(
                submissionsCsvPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            if (writeHeader) {
                writer.write(SUBMISSIONS_CSV_HEADER);
                writer.newLine();
            }
            writer.write(submission.toCsvRow());
            writer.newLine();
        }
    }

    private static Challenge deserializeChallenge(List<String> fields, int lineNumber) throws CorruptedFileException {
        String type = requiredField(fields, 0, lineNumber, "TYPE").toUpperCase(Locale.ROOT);
        String id = requiredField(fields, 1, lineNumber, "ID");
        String title = requiredField(fields, 2, lineNumber, "TITLE");
        int points = parseIntField(fields.get(3), lineNumber, "POINTS");
        Challenge.Difficulty difficulty = parseDifficulty(fields.get(4), lineNumber);
        String param4 = fields.size() > 8 ? optionalField(fields, 8) : null;

        try {
            switch (type) {
                case "CTF":
                    return new CTFChallenge(
                            id,
                            title,
                            points,
                            difficulty,
                            requiredField(fields, 5, lineNumber, "CATEGORY"),
                            requiredField(fields, 6, lineNumber, "FLAG_HASH"),
                            parseIntField(fields.get(7), lineNumber, "HINT_COST"),
                            param4);
                case "CP":
                    return new CPProblem(
                            id,
                            title,
                            points,
                            difficulty,
                            parseLongField(fields.get(5), lineNumber, "TIME_LIMIT_MS"),
                            parseIntField(fields.get(6), lineNumber, "MEMORY_LIMIT_MB"),
                            Path.of(requiredField(fields, 7, lineNumber, "TESTCASE_DIR")));
                default:
                    throw new CorruptedFileException("Unsupported challenge type '" + type + "' at line " + lineNumber);
            }
        } catch (IllegalArgumentException ex) {
            throw invalidRow(lineNumber, ex);
        }
    }

    private static User deserializeUser(List<String> fields, int lineNumber) throws CorruptedFileException {
        try {
            return new User(
                    requiredField(fields, 0, lineNumber, "USER_ID"),
                    requiredField(fields, 1, lineNumber, "USERNAME"),
                    requiredField(fields, 2, lineNumber, "PASSWORD_HASH"),
                    parseUserRole(fields.get(3), lineNumber),
                    optionalField(fields, 4));
        } catch (IllegalArgumentException ex) {
            throw invalidRow(lineNumber, ex);
        }
    }

    private static Team deserializeTeam(List<String> fields, int lineNumber) throws CorruptedFileException {
        try {
            return new Team(
                    requiredField(fields, 0, lineNumber, "TEAM_ID"),
                    requiredField(fields, 1, lineNumber, "TEAM_NAME"),
                    parseMemberIds(fields.get(2)),
                    parseIntField(fields.get(3), lineNumber, "TOTAL_SCORE"),
                    parseOptionalInstantField(fields.get(4), lineNumber, "LAST_SOLVE_TIME"));
        } catch (IllegalArgumentException ex) {
            throw invalidRow(lineNumber, ex);
        }
    }

    private static Submission deserializeSubmission(List<String> fields, int lineNumber) throws CorruptedFileException {
        try {
            String submissionId = requiredField(fields, 0, lineNumber, "SUBMISSION_ID");
            SubmissionResult result = new SubmissionResult(
                    submissionId,
                    parseSubmissionStatus(fields.get(4), lineNumber),
                    parseIntField(fields.get(5), lineNumber, "POINTS_AWARDED"),
                    parseOptionalInstantField(fields.get(10), lineNumber, "EVALUATED_AT"),
                    optionalField(fields, 11));

            return new Submission(
                    submissionId,
                    requiredField(fields, 6, lineNumber, "USER_ID"),
                    requiredField(fields, 1, lineNumber, "TEAM_ID"),
                    requiredField(fields, 2, lineNumber, "CHALLENGE_ID"),
                    requiredField(fields, 7, lineNumber, "PAYLOAD"),
                    parseIntField(fields.get(8), lineNumber, "WRONG_ATTEMPTS"),
                    parseIntField(fields.get(9), lineNumber, "HINTS_USED"),
                    parseInstantField(fields.get(3), lineNumber, "TIMESTAMP"),
                    result);
        } catch (IllegalArgumentException ex) {
            throw invalidRow(lineNumber, ex);
        }
    }

    private static <T> List<T> loadRecords(
            Path path,
            String label,
            int expectedFieldCount,
            String firstHeader,
            String secondHeader,
            RowParser<T> parser) throws IOException, CorruptedFileException {
        List<T> records = new ArrayList<>();
        if (Files.notExists(path)) {
            return records;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) {
                return records;
            }
            if (!isValidHeader(line, expectedFieldCount, firstHeader, secondHeader)) {
                throw new CorruptedFileException("Invalid " + label + " header at line 1");
            }

            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> fields = CsvCodec.parseLine(line, lineNumber);
                if (fields.size() != expectedFieldCount) {
                    throw new CorruptedFileException(
                            "Expected " + expectedFieldCount + " CSV fields at line " + lineNumber
                                    + " but found " + fields.size());
                }
                records.add(parser.parse(fields, lineNumber));
            }
        }
        return records;
    }

    private static <T> List<T> loadRecordsFlex(
            Path path,
            String label,
            int minFieldCount,
            int maxFieldCount,
            String firstHeader,
            String secondHeader,
            RowParser<T> parser) throws IOException, CorruptedFileException {
        List<T> records = new ArrayList<>();
        if (Files.notExists(path)) {
            return records;
        }

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            if (line == null) {
                return records;
            }
            List<String> headerFields = CsvCodec.parseLine(line, 1);
            if (headerFields.size() < minFieldCount || headerFields.size() > maxFieldCount
                    || !headerFields.get(0).trim().equalsIgnoreCase(firstHeader)
                    || !headerFields.get(1).trim().equalsIgnoreCase(secondHeader)) {
                throw new CorruptedFileException("Invalid " + label + " header at line 1");
            }

            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                List<String> fields = CsvCodec.parseLine(line, lineNumber);
                if (fields.size() < minFieldCount || fields.size() > maxFieldCount) {
                    throw new CorruptedFileException(
                            "Expected " + minFieldCount + "-" + maxFieldCount + " CSV fields at line " + lineNumber
                                    + " but found " + fields.size());
                }
                records.add(parser.parse(fields, lineNumber));
            }
        }
        return records;
    }

    private static <T extends Persistable> void saveRecords(
            Path path,
            String header,
            Collection<? extends T> records) throws IOException {
        Objects.requireNonNull(records, "records must not be null");
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            writer.write(header);
            writer.newLine();
            for (T record : records) {
                Objects.requireNonNull(record, "record must not be null");
                writer.write(record.toCsvRow());
                writer.newLine();
            }
        }
    }

    private static boolean isValidHeader(
            String line,
            int expectedFieldCount,
            String firstHeader,
            String secondHeader) throws CorruptedFileException {
        List<String> fields = CsvCodec.parseLine(line, 1);
        return fields.size() == expectedFieldCount
                && fields.get(0).trim().equalsIgnoreCase(firstHeader)
                && fields.get(1).trim().equalsIgnoreCase(secondHeader);
    }

    private static Challenge.Difficulty parseDifficulty(String token, int lineNumber) throws CorruptedFileException {
        try {
            return Challenge.Difficulty.fromToken(token);
        } catch (IllegalArgumentException ex) {
            throw new CorruptedFileException("Invalid DIFFICULTY at line " + lineNumber + ": " + token, ex);
        }
    }

    private static User.Role parseUserRole(String token, int lineNumber) throws CorruptedFileException {
        try {
            return User.Role.fromToken(token);
        } catch (IllegalArgumentException ex) {
            throw new CorruptedFileException("Invalid ROLE at line " + lineNumber + ": " + token, ex);
        }
    }

    private static SubmissionResult.Status parseSubmissionStatus(String token, int lineNumber)
            throws CorruptedFileException {
        try {
            return SubmissionResult.Status.fromToken(token);
        } catch (IllegalArgumentException ex) {
            throw new CorruptedFileException("Invalid STATUS at line " + lineNumber + ": " + token, ex);
        }
    }

    private static List<String> parseMemberIds(String token) {
        if (token == null || token.trim().isEmpty()) {
            return List.of();
        }
        String[] parts = token.split(";");
        List<String> memberIds = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                memberIds.add(part);
            }
        }
        return memberIds;
    }

    private static Instant parseInstantField(String value, int lineNumber, String name) throws CorruptedFileException {
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            throw new CorruptedFileException("Invalid instant for " + name + " at line " + lineNumber + ": " + value, ex);
        }
    }

    private static Instant parseOptionalInstantField(String value, int lineNumber, String name)
            throws CorruptedFileException {
        return value == null || value.trim().isEmpty() ? null : parseInstantField(value, lineNumber, name);
    }

    private static String requiredField(List<String> fields, int index, int lineNumber, String name)
            throws CorruptedFileException {
        String value = fields.get(index);
        if (value == null || value.trim().isEmpty()) {
            throw new CorruptedFileException("Missing " + name + " at line " + lineNumber);
        }
        return value;
    }

    private static String optionalField(List<String> fields, int index) {
        String value = fields.get(index);
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static int parseIntField(String value, int lineNumber, String name) throws CorruptedFileException {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new CorruptedFileException("Invalid integer for " + name + " at line " + lineNumber + ": " + value, ex);
        }
    }

    private static long parseLongField(String value, int lineNumber, String name) throws CorruptedFileException {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new CorruptedFileException("Invalid long for " + name + " at line " + lineNumber + ": " + value, ex);
        }
    }

    private static CorruptedFileException invalidRow(int lineNumber, IllegalArgumentException ex) {
        return new CorruptedFileException("Invalid data at line " + lineNumber + ": " + ex.getMessage(), ex);
    }

    private static String requireArgument(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be null or blank");
        }
        return value;
    }

    private static Path siblingPath(Path referencePath, String fileName) {
        Path absoluteParent = referencePath.toAbsolutePath().getParent();
        return absoluteParent == null ? Path.of(fileName) : absoluteParent.resolve(fileName);
    }

    @FunctionalInterface
    private interface RowParser<T> {
        T parse(List<String> fields, int lineNumber) throws CorruptedFileException;
    }
}
