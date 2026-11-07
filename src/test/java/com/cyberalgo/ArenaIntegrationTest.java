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
    @Test void playerCannotCreateAContestAndCanReadOwnHistory() throws Exception {
        String name="http"+UUID.randomUUID();
        var registered=req("/api/auth/register",json.writeValueAsString(java.util.Map.of("username",name,"password","PlayerOnly!2026")));
        assertEquals(201,registered.statusCode());
        assertEquals(403,req("/api/admin/contests","{}").statusCode());
        assertEquals(200,req("/api/submissions/me",null).statusCode());
        assertEquals(200,req("/api/standings/practice",null).statusCode());
    }
}
