package com.cyberalgo;

import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Opt-in real MongoDB tests. Each test owns a randomly named database, never the application DB. */
class MongoPersistenceIT {
    private String databaseName;
    private MongoManager manager;
    private MongoRepository repo;
    @TempDir Path temp;

    @BeforeEach
    void connect() {
        databaseName = "arena_it_" + UUID.randomUUID().toString().replace("-", "");
        manager = connectFresh();
        assertTrue(manager.ping(), "Integration MongoDB unavailable (details withheld).");
        repo = new MongoRepository(manager, false);
    }

    private MongoManager connectFresh() {
        String uri = System.getenv("MONGODB_TEST_URI");
        assertNotNull(uri, "Set MONGODB_TEST_URI securely to run the integration profile.");
        return new MongoManager(null, null, Map.of("MONGODB_URI", uri, "MONGODB_DATABASE_NAME", databaseName));
    }

    @AfterEach
    void cleanup() {
        if (manager != null) manager.close();
        if (databaseName != null) {
            try (MongoManager cleanup = connectFresh()) {
                assertTrue(cleanup.ping(), "Unable to clean integration test database.");
                cleanup.getDatabase().drop();
            }
        }
    }

    private CPProblem problem(String id) {
        CPProblem cp = new CPProblem(id, "Weekly problem", 200, Challenge.Difficulty.EASY,
                1000, 256, temp.resolve("missing"), List.of(
                CPTestCase.sample("PUBLIC_SAMPLE", "PUBLIC_OUTPUT"),
                CPTestCase.hidden("HIDDEN_INPUT_SENTINEL", "HIDDEN_OUTPUT_SENTINEL")));
        cp.setDescription("Weekly administrator-created exercise");
        return cp;
    }

    @Test
    void allDomainDataAndHiddenTestsSurviveReinitialization() throws Exception {
        CPProblem cp = problem("WEEKLY-CP");
        repo.saveChallenge(cp);
        CTFChallenge ctf = new CTFChallenge("WEEKLY-CTF", "Weekly CTF", 100,
                Challenge.Difficulty.EASY, "CRYPTO", CTFChallenge.sha256Hex("flag{test}"), 0);
        repo.saveChallenge(ctf);
        User user = new User("USER", "test-user", "test@example.invalid", "test-hash", User.Role.PLAYER, "TEAM");
        user.recordSolve(cp.getId(), "CP", 200, "CP");
        repo.saveUser(user);
        Team team = new Team("TEAM", "test-team", "", user.getId(), List.of(user.getId()), 200,
                Instant.now(), Instant.now());
        repo.saveTeam(team);
        Contest contest = new Contest("CONTEST", "Weekly contest", "description", Instant.now(),
                Instant.now().plusSeconds(3600), true, List.of(team.getId()));
        repo.saveContest(contest);
        repo.recordParticipation(new ContestParticipation(contest.getId(), team.getId(), user.getId(), Instant.now()));
        Submission submission = attempt("SUBMISSION", user.getId(), cp.getId());
        submission.applyResult(new SubmissionResult(SubmissionResult.Status.ACCEPTED, 200, "Accepted"));
        repo.saveSubmission(submission);
        assertTrue(manager.ping());
        manager.close();

        manager = connectFresh();
        repo = new MongoRepository(manager, false);
        assertTrue(manager.ping());
        CPProblem loaded = (CPProblem) repo.getChallengeById(cp.getId()).orElseThrow();
        assertEquals(cp.getConfiguredTestCases(), loaded.getConfiguredTestCases());
        assertEquals(cp.getDescription(), loaded.getDescription());
        assertEquals(1, loaded.getHiddenTestCount());
        assertTrue(repo.getChallengeById(ctf.getId()).orElseThrow().evaluate("flag{test}"));
        User profile = repo.getUserById(user.getId()).orElseThrow();
        assertEquals(200, profile.getPersonalScore());
        assertEquals(200, profile.getCpScore());
        assertTrue(profile.isSolved(cp.getId()));
        assertEquals(200, repo.getTeamById(team.getId()).orElseThrow().getTotalScore());
        assertEquals(contest.getTitle(), repo.getContestById(contest.getId()).orElseThrow().getTitle());
        assertTrue(repo.getParticipation(contest.getId(), user.getId()).isPresent());
        assertEquals(1, repo.getParticipationsByUser(user.getId()).size());
        assertEquals(SubmissionResult.Status.ACCEPTED, repo.getAllSubmissions().getFirst().getStatus());
        ContestEngine engine = new ContestEngine(repo);
        engine.load();
        try (WebServer web = new WebServer(engine, 0)) {
            assertEquals(200, WebServerHealthTest.get(web, "/api/health/ready").statusCode());
            for (String path : List.of("/api/challenges", "/api/challenges/" + cp.getId(),
                    "/api/users/USER/profile")) {
                var response = WebServerHealthTest.get(web, path);
                assertEquals(200, response.statusCode());
                assertFalse(response.body().contains("HIDDEN_INPUT_SENTINEL"));
                assertFalse(response.body().contains("HIDDEN_OUTPUT_SENTINEL"));
                assertFalse(response.body().contains("flagHash"));
            }
            manager.close();
            assertEquals(503, WebServerHealthTest.get(web, "/api/health/ready").statusCode());
            assertEquals(200, WebServerHealthTest.get(web, "/api/health/live").statusCode());
        }
    }

    @Test
    void legacyDiskTestcasesAreMigratedAndRemainAfterFilesDisappear() throws Exception {
        Files.writeString(temp.resolve("input_1.txt"), "sample");
        Files.writeString(temp.resolve("output_1.txt"), "sample-output");
        Files.writeString(temp.resolve("input_2.txt"), "hidden");
        Files.writeString(temp.resolve("output_2.txt"), "hidden-output");
        manager.getChallengesCollection().insertOne(new Document("id", "LEGACY").append("type", "CP")
                .append("title", "Legacy").append("difficulty", "EASY")
                .append("testcaseDir", temp.toString()));
        repo.migrateLegacyTestCases();
        for (String file : List.of("input_1.txt", "output_1.txt", "input_2.txt", "output_2.txt")) {
            Files.delete(temp.resolve(file));
        }
        manager.close();
        manager = connectFresh();
        CPProblem loaded = (CPProblem) new MongoRepository(manager, false).getChallengeById("LEGACY").orElseThrow();
        assertEquals(List.of(CPTestCase.sample("sample", "sample-output"),
                CPTestCase.hidden("hidden", "hidden-output")), loaded.getJudgeTestCases());
    }

    @Test
    void migratesBadIndexesAndAllowsAllFailedVerdictsBeforeOneAcceptedSolve() {
        manager.getSubmissionsCollection().dropIndexes();
        manager.getSubmissionsCollection().createIndex(Indexes.ascending("contestId", "challengeId", "teamId"),
                new IndexOptions().unique(true).name("legacy_team_constraint"));
        manager.getSubmissionsCollection().createIndex(Indexes.ascending("contestId", "challengeId", "userId"),
                new IndexOptions().unique(true).name("legacy_user_constraint"));
        manager.close();
        manager = connectFresh();
        assertTrue(manager.ping());
        repo = new MongoRepository(manager, false);
        repo.saveChallenge(problem("CP"));
        repo.saveUser(new User("USER", "retry-user", null, "hash", User.Role.PLAYER, null));
        PistonJudgeEngine judge = mock(PistonJudgeEngine.class);
        ContestEngine engine = new ContestEngine(repo, judge);
        engine.load();
        for (SubmissionResult.Status verdict : List.of(SubmissionResult.Status.WRONG_ANSWER,
                SubmissionResult.Status.TIME_LIMIT_EXCEEDED, SubmissionResult.Status.MEMORY_LIMIT_EXCEEDED,
                SubmissionResult.Status.RUNTIME_ERROR, SubmissionResult.Status.COMPILATION_ERROR,
                SubmissionResult.Status.ACCEPTED)) {
            when(judge.judge(any(CPProblem.class), anyString(), anyString())).thenReturn(
                    new PistonJudgeEngine.ExecutionResult(verdict, verdict.name(), 0, 2, 0, 0, "Python"));
            assertEquals(verdict, engine.submit(attempt("S-" + verdict, "USER", "CP"), "python").getStatus());
        }
        assertEquals(6, repo.getAllSubmissions().size());
        assertThrows(DuplicateSubmissionException.class, () -> engine.submit(attempt("AFTER-AC", "USER", "CP"), "python"));
        assertEquals(1, repo.getUserById("USER").orElseThrow().getCpSolvesCount());
        // ID uniqueness rejects insertion instead of overwriting an existing attempt.
        assertThrows(DatabaseUnavailableException.class,
                () -> repo.saveSubmission(attempt("S-WRONG_ANSWER", "USER", "CP")));
        manager.close();
        manager = connectFresh();
        repo = new MongoRepository(manager, false);
        ContestEngine restarted = new ContestEngine(repo, judge);
        restarted.load();
        assertThrows(DuplicateSubmissionException.class, () -> restarted.submit(attempt("RESTART-AC", "USER", "CP"), "python"));
    }

    @Test
    void adminWeeklyProblemSurvivesRestartAndPublicApiOmitsHiddenData() throws Exception {
        repo.saveUser(new User("ADMIN", "weekly-admin", null, User.hashPassword("test-password-only"), User.Role.ADMIN, null));
        repo.saveChallenge(problem("EXISTING"));
        ContestEngine engine = new ContestEngine(repo);
        engine.load();
        try (WebServer web = new WebServer(engine, 0);
             HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build()) {
            String base = "http://127.0.0.1:" + web.port();
            assertEquals(200, post(client, base + "/api/auth/admin-login", Map.of(
                    "username", "weekly-admin", "password", "test-password-only")).statusCode());
            var response = post(client, base + "/api/admin/challenges", Map.of(
                    "id", "NEW-WEEK", "title", "Admin weekly", "type", "CP", "basePoints", "100",
                    "difficulty", "EASY", "description", "Persist this statement", "sampleInput", "1",
                    "sampleOutput", "2", "hiddenTests", "[{\"input\":\"HIDDEN_INPUT_SENTINEL\",\"output\":\"HIDDEN_OUTPUT_SENTINEL\"}]"));
            assertTrue(response.statusCode() == 200 || response.statusCode() == 201, response.body());
            assertFalse(response.body().contains("HIDDEN_INPUT_SENTINEL"));
        }
        manager.close();
        manager = connectFresh();
        CPProblem cp = (CPProblem) new MongoRepository(manager, false).getChallengeById("NEW-WEEK").orElseThrow();
        assertEquals("Persist this statement", cp.getDescription());
        assertEquals("HIDDEN_INPUT_SENTINEL", cp.getConfiguredTestCases().get(1).input());
        assertEquals("HIDDEN_OUTPUT_SENTINEL", cp.getConfiguredTestCases().get(1).expectedOutput());
    }

    @Test
    void transactionRollsBackPartialScores() {
        org.junit.jupiter.api.Assumptions.assumeTrue(manager.supportsTransactions(), "Replica set needed for transaction verification");
        repo.saveUser(new User("USER", "atomic-user", null, "hash", User.Role.PLAYER, null));
        assertThrows(DatabaseUnavailableException.class, () -> repo.atomic(() -> {
            User user = repo.getUserById("USER").orElseThrow();
            user.recordSolve("CP", "CP", 100, "CP");
            repo.saveUser(user);
            throw new IllegalStateException("Abort test transaction");
        }));
        assertEquals(0, repo.getUserById("USER").orElseThrow().getCpScore());
        assertFalse(repo.getUserById("USER").orElseThrow().isSolved("CP"));
    }

    @Test
    void concurrentAcceptedAttemptsOnlyAwardOneSolveAcrossInstances() throws Exception {
        assertTrue(manager.supportsTransactions(), "Run this profile against a MongoDB replica set");
        repo.saveUser(new User("USER", "concurrent-user", null, "hash", User.Role.PLAYER, null));
        repo.saveChallenge(problem("CP"));
        CountDownLatch judging = new CountDownLatch(2);
        PistonJudgeEngine judge = mock(PistonJudgeEngine.class);
        when(judge.judge(any(CPProblem.class), anyString(), anyString())).thenAnswer(invocation -> {
            judging.countDown();
            assertTrue(judging.await(10, java.util.concurrent.TimeUnit.SECONDS));
            return new PistonJudgeEngine.ExecutionResult(SubmissionResult.Status.ACCEPTED, "Accepted", 2, 2, 1, 0, "Python");
        });
        ContestEngine first = new ContestEngine(repo, judge);
        ContestEngine second = new ContestEngine(repo, judge);
        first.load();
        second.load();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(executor.submit(() -> submitAccepted(first, "FIRST")),
                    executor.submit(() -> submitAccepted(second, "SECOND")));
            int accepted = 0;
            for (var future : futures) if (future.get(20, java.util.concurrent.TimeUnit.SECONDS)) accepted++;
            assertEquals(1, accepted);
        }
        assertEquals(1, repo.getAllSubmissions().size());
        assertEquals(1, repo.getUserById("USER").orElseThrow().getCpSolvesCount());
        assertEquals(1, repo.getChallengeById("CP").orElseThrow().getSolveCount());
    }

    private boolean submitAccepted(ContestEngine engine, String id) {
        try {
            engine.submit(attempt(id, "USER", "CP"), "python");
            return true;
        } catch (DuplicateSubmissionException expected) {
            return false;
        }
    }

    static Submission attempt(String id, String user, String problem) {
        return new Submission(id, "CONTEST", user, "", problem, "print(1)", 0, 0, Instant.now());
    }

    static HttpResponse<String> post(HttpClient client, String uri, Object body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(uri)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
