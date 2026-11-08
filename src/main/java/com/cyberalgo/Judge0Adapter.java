package com.cyberalgo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Converts an authorized Judge0 sandbox result to the common execution protocol. */
final class Judge0Adapter {
    static final String DEFAULT_JUDGE0_URL = "https://ce.judge0.com";
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String base;
    private final String apiKey;
    Judge0Adapter() { this(System.getenv("JUDGE0_URL"), System.getenv("JUDGE0_API_KEY")); }
    Judge0Adapter(String base, String apiKey) {
        String resolved = (base == null || base.isBlank()) ? DEFAULT_JUDGE0_URL : base.trim();
        this.base = resolved.replaceAll("/+$", "");
        this.apiKey = apiKey != null && !apiKey.isBlank() ? apiKey.trim() : null;
    }
    JsonNode execute(Map<String,Object> piston) throws Exception {
        int lang = switch (String.valueOf(piston.get("language"))) {case "c++" -> 54; case "java" -> 62; default -> 71;};
        @SuppressWarnings("unchecked") List<Map<String,String>> files=(List<Map<String,String>>)piston.get("files");
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("language_id",lang);
        request.put("source_code",files.get(0).get("content"));
        request.put("stdin",piston.get("stdin"));
        request.put("cpu_time_limit", ((Number)piston.get("run_cpu_time")).doubleValue()/1000);
        request.put("wall_time_limit", Math.max(5, ((Number)piston.get("run_timeout")).doubleValue()/1000+2));
        request.put("memory_limit",((Number)piston.get("run_memory_limit")).longValue()/1024);
        request.put("enable_network",false);
        // Base64 avoids invalid UTF-8 from submitted programs breaking JSON decoding.
        request.put("source_code",b64(String.valueOf(request.get("source_code"))));
        request.put("stdin",b64(String.valueOf(request.get("stdin"))));
        JsonNode created = send("/submissions?base64_encoded=true&wait=true", request);
        String token = created.path("token").asText("");
        if (!token.matches("[a-zA-Z0-9-]+")) throw new IllegalStateException("Judge did not return a submission token");
        JsonNode result = created;
        if (result.path("status").path("id").asInt(0) <= 2) {
            for (int i=0; i<40; i++) {
                Thread.sleep(500);
                result = send("/submissions/"+token+"?base64_encoded=true&fields=status,stdout,stderr,compile_output,time,wall_time,memory,exit_code,exit_signal",null);
                if (result.path("status").path("id").asInt() > 2) break;
            }
        }
        int status=result.path("status").path("id").asInt();
        if (status <= 2) throw new IllegalStateException("Judge queue timed out; try again shortly");
        if (status == 13 || status >=14) throw new IllegalStateException("Judge infrastructure error");
        if (status == 6) return mapper.valueToTree(Map.of("compile",Map.of("code",1,"stderr",decoded(result,"compile_output"))));
        Map<String,Object> run=new LinkedHashMap<>();
        run.put("code",status==3||status==4?0:result.path("exit_code").asInt(1));
        run.put("stdout",decoded(result,"stdout")); run.put("stderr",decoded(result,"stderr"));
        run.put("wall_time",Math.round(result.path("wall_time").asDouble(0)*1000));
        run.put("cpu_time",Math.round(result.path("time").asDouble(0)*1000));
        run.put("memory",result.path("memory").asLong(0)*1024);
        if(status==5) run.put("status","TO"); else if(status>=7)run.put("status","RE");
        return mapper.valueToTree(Map.of("run",run));
    }
    private JsonNode send(String path,Map<String,Object> body) throws Exception {
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(base+path))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type","application/json")
            .header("User-Agent","CyberAlgoArena-Judge/1.0");
        if(apiKey!=null&&!apiKey.isBlank()) {
            URI u = URI.create(base);
            if(u.getHost()!=null&&u.getHost().endsWith("rapidapi.com")) {
                builder.header("X-RapidAPI-Key",apiKey).header("X-RapidAPI-Host",u.getHost());
            } else builder.header("X-Auth-Token",apiKey);
        }
        if(body==null)builder.GET();else builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        HttpResponse<String> response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Judge0 HTTP "+response.statusCode()+"; check judge credentials, quota and language support");
        return mapper.readTree(response.body());
    }
    private static String b64(String value){return Base64.getEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String decoded(JsonNode result,String key){String value=result.path(key).asText("");return value.isBlank()?"":new String(Base64.getDecoder().decode(value),java.nio.charset.StandardCharsets.UTF_8);}
}
