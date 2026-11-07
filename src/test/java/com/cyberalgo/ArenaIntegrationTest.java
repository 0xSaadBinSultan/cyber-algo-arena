package com.cyberalgo;

import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.*;
import static org.junit.jupiter.api.Assertions.*;

class ArenaIntegrationTest {
    static MongoManager mongo;
    static ContestEngine engine;
    static WebServer web;
    static HttpClient client;
    static String url;
    static final ObjectMapper json=new ObjectMapper();
    @BeforeAll static void setup() throws Exception {
        System.setProperty("arena.admin.password","IntegrationAdminOnly!2026");
        mongo=new MongoManager("mongodb://localhost:27017","arena_test_"+UUID.randomUUID().toString().replace("-",""));
        if ("true".equals(System.getenv("ARENA_TEST_REQUIRE_MONGO"))) {
            assertTrue(mongo.isConnected(), "CI integration tests require persistent MongoDB");
        }
        engine=new ContestEngine(new MongoRepository(mongo)); engine.load();
        int port;try(java.net.ServerSocket socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
        web=new WebServer(engine,port);url="http://localhost:"+port;
        client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
    }
    @AfterAll static void cleanup() {
        if(web!=null)web.stop();
        if(mongo!=null){if(mongo.getDatabase()!=null)mongo.getDatabase().drop();mongo.close();}
        System.clearProperty("arena.admin.password");
    }
    static HttpResponse<String> req(String path,String body) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(url+path));
        if(body!=null)builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    @Test void publicApisContainSamplesButNeverHiddenInputsOrHashes() throws Exception {
        JsonNode p=json.readTree(req("/api/challenges/ARENA-SUM",null).body());
        assertEquals(6,p.path("hiddenTestCount").asInt());assertEquals(2,p.path("sampleCases").size());
        String wire=p.toString(); assertFalse(wire.contains("4000000000"));assertFalse(wire.contains("testcaseDir"));
        assertFalse(req("/api/challenges/ARENA-CRYPTO",null).body().contains("flagHash"));
        assertEquals(401,HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url+"/api/admin/contests")).POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    @Test void contestChecksRegistrationTimeProblemAndDuplicateAndSeparatesPractice() {
        String suffix=UUID.randomUUID().toString();
        User u=engine.registerUser("player"+suffix,"test@example.test","TestPassword!2026");
        Team team=engine.createTeam("team"+suffix,"TeamPassword!2026",u.getId());
        Contest live=new Contest("live"+suffix,"Live","",Instant.now().minusSeconds(30),Instant.now().plusSeconds(600),true,List.of());
        live.setChallengeIds(List.of("ARENA-CRYPTO"));engine.createContest(live);
        Submission accepted=new Submission("s1"+suffix,live.getId(),u.getId(),team.getId(),"ARENA-CRYPTO","flag{one_decode_closer}",0,0,Instant.now());
        assertThrows(InvalidSubmissionException.class,()->engine.submit(accepted));
        engine.registerPlayerInContest(live.getId(),team.getId(),u.getId());
        assertEquals(SubmissionResult.Status.ACCEPTED,engine.submit(accepted).getStatus());
        assertEquals(100,accepted.getPointsAwarded());assertEquals(0,u.getPersonalScore());
        assertThrows(DuplicateSubmissionException.class,()->engine.submit(accepted));
        Submission outside=new Submission("s2"+suffix,live.getId(),u.getId(),team.getId(),"ARENA-LOG","flag{follow_the_request}",0,0,Instant.now());
        assertThrows(InvalidSubmissionException.class,()->engine.submit(outside));
        Contest future=new Contest("future"+suffix,"Later","",Instant.now().plusSeconds(300),Instant.now().plusSeconds(600),true,List.of());
        future.setChallengeIds(List.of("ARENA-CRYPTO"));engine.createContest(future);
        engine.registerPlayerInContest(future.getId(),team.getId(),u.getId());
        assertThrows(InvalidSubmissionException.class,()->engine.submit(new Submission("s3"+suffix,future.getId(),u.getId(),team.getId(),"ARENA-CRYPTO","flag{one_decode_closer}",0,0,Instant.now())));
        assertThrows(IllegalArgumentException.class,()->engine.registerPlayerInContest("missing",team.getId(),u.getId()));
        assertEquals(SubmissionResult.Status.ACCEPTED,engine.submit(new Submission("s4"+suffix,"GLOBAL",u.getId(),team.getId(),"ARENA-CRYPTO","flag{one_decode_closer}",0,0,Instant.now())).getStatus());
        assertTrue(u.getPersonalScore()>0);
    }
    @Test void acceptedCpResubmissionsAreJudgedWithoutAwardingPointsTwice() throws Exception {
        var mock=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        mock.createContext("/api/v2/execute",exchange->{
            exchange.getRequestBody().readAllBytes();
            byte[] body="{\"run\":{\"code\":0,\"stdout\":\"42\\n\"}}".getBytes();
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });mock.start();
        try {
            var replayEngine=new ContestEngine(engine.getRepository(),new PistonJudgeEngine("http://127.0.0.1:"+mock.getAddress().getPort()));
            replayEngine.load();
            String key=UUID.randomUUID().toString();
            User user=replayEngine.registerUser("repeat"+key,"repeat@example.test","RepeatPassword!2026");
            CPProblem cp=new CPProblem("CP-REPLAY"+key,"Replay",100,Challenge.Difficulty.EASY,1000,256,
                    java.nio.file.Path.of("unused"),List.of(CPTestCase.sample("","42"),CPTestCase.hidden("","42")));
            replayEngine.addChallenge(cp);
            var first=replayEngine.submit(new Submission("first"+key,"GLOBAL",user.getId(),"",cp.getId(),"print(42)",0,0,Instant.now()),"python");
            int score=user.getPersonalScore();
            assertTrue(first.getPointsAwarded()>0);
            var second=replayEngine.submit(new Submission("second"+key,"GLOBAL",user.getId(),"",cp.getId(),"print(42)",0,0,Instant.now()),"python");
            assertEquals(SubmissionResult.Status.ACCEPTED,second.getStatus());assertEquals(0,second.getPointsAwarded());
            assertEquals(score,user.getPersonalScore());assertEquals(1,user.getSolvesCount());assertEquals(1,cp.getSolveCount());
        }finally{mock.stop(0);}
    }
    @Test void editingPointsPreservesProblemContentAndScoringHistory() {
        String id = "EDIT-" + UUID.randomUUID();
        CPProblem cp = PracticeCatalog.packetBudget();
        CPProblem editable = new CPProblem(id, cp.getTitle(), cp.getBasePoints(), cp.getDifficulty(),
                cp.getTimeLimitMillis(), cp.getMemoryLimitMb(), cp.getTestcaseDirectory(), cp.getConfiguredTestCases());
        editable.setDescription(cp.getDescription()); editable.setSolveCount(7);
        editable.setDecayLimit(30); editable.setMinimumPoints(20); editable.setFirstBlood("team", "user");
        engine.addChallenge(editable);
        engine.updateChallengeBasePoints(id, 250);
        CPProblem saved = (CPProblem) engine.getRepository().getChallengeById(id).orElseThrow();
        assertEquals(250, saved.getBasePoints()); assertEquals(cp.getDescription(), saved.getDescription());
        assertEquals(7, saved.getSolveCount()); assertEquals(30, saved.getDecayLimit());
        assertEquals(20, saved.getMinimumPoints()); assertEquals("team", saved.getFirstBloodTeamId());
        assertEquals("user", saved.getFirstBloodUserId()); assertEquals(8, saved.getJudgeTestCases().size());
    }

    @Test void hintUnlockSurvivesReloadBeforeAnySubmissionAndChargesOnce() {
        String key = UUID.randomUUID().toString();
        User user = engine.registerUser("hint" + key, "hint@example.test", "HintPassword!2026");
        Team team = engine.createTeam("hintTeam" + key, "HintPassword!2026", user.getId());
        String hint = engine.requestHintForUser(user.getId(), "ARENA-CRYPTO");
        assertFalse(hint.isBlank());
        engine.requestHintForUser(user.getId(), "ARENA-CRYPTO");
        engine.requestHint(team.getId(), "ARENA-LOG");
        // A new repository also proves this is persisted, rather than just cached.
        ContestEngine reloaded = new ContestEngine(new MongoRepository(mongo)); reloaded.load();
        assertEquals(1, reloaded.getUserHintUsageCount(user.getId(), "ARENA-CRYPTO"));
        assertEquals(1, reloaded.getHintUsageCount(team.getId(), "ARENA-LOG"));
    }

    @Test void concurrentCatalogReadsNeverObservePartialReloads() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(3);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var reload = executor.submit(() -> { start.await(); for (int i=0;i<10;i++) engine.load(); return true; });
            var reader = executor.submit(() -> {
                start.await();
                for (int i=0;i<1000;i++) {
                    assertFalse(engine.getUsers().isEmpty());
                    assertNotNull(engine.getChallenge("ARENA-SUM"));
                    assertFalse(engine.getChallenges().isEmpty());
                }
                return true;
            });
            start.countDown();
            assertTrue(reload.get(60, java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(reader.get(60, java.util.concurrent.TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }

    @Test void ctfTutorReturnsClearlyLabeledGuidanceWithoutProviderConfiguration() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("GEMINI_API_KEY") == null
                || System.getenv("GEMINI_API_KEY").isBlank());
        assertEquals(201, req("/api/auth/register", json.writeValueAsString(java.util.Map.of(
                "username", "guide" + UUID.randomUUID(), "password", "GuidePassword!2026"))).statusCode());
        var response = req("/api/ai/tutor", "{\"challengeId\":\"ARENA-CRYPTO\",\"mode\":\"EXPLAIN\"}");
        assertEquals(200, response.statusCode());
        var body = json.readTree(response.body());
        assertEquals("study_guide", body.path("source").asText());
        assertTrue(body.path("notice").asText().contains("unavailable"));
        assertFalse(body.path("message").asText().isBlank());
        assertEquals("", body.path("model").asText());
        assertFalse(response.body().contains("flag{"));
        assertEquals(400, req("/api/ai/tutor", "{\"challengeId\":\"ARENA-SUM\"}").statusCode());
    }

    @Test void playerCannotCreateAContestAndCanReadOwnHistory() throws Exception {
        String name="http"+UUID.randomUUID();
        var registered=req("/api/auth/register",json.writeValueAsString(java.util.Map.of("username",name,"password","PlayerOnly!2026")));
        assertEquals(201,registered.statusCode());
        assertEquals(403,req("/api/admin/contests","{}").statusCode());
        assertEquals(200,req("/api/submissions/me",null).statusCode());
        assertEquals(200,req("/api/standings/practice",null).statusCode());
    }
    @Test void aiTutorIsDisabledForCpProblems() throws Exception {
        String name = "aitutor" + UUID.randomUUID();
        var registered = req("/api/auth/register", json.writeValueAsString(java.util.Map.of("username", name, "password", "AiPass!2026")));
        assertEquals(201, registered.statusCode());
        var resp = req("/api/ai/tutor", json.writeValueAsString(java.util.Map.of(
                "challengeId", "ARENA-SUM",
                "mode", "HINT"
        )));
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("AI Tutor is disabled for Competitive Programming problems"));
    }
}
