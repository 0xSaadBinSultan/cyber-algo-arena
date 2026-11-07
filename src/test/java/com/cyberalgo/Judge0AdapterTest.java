package com.cyberalgo;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class Judge0AdapterTest {
    @Test void submitsWithoutNetworkAccessAndNormalizesBase64Result() throws Exception {
        ObjectMapper mapper=new ObjectMapper();
        AtomicReference<JsonNode> captured=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/submissions",ex->{
            String reply;
            if("POST".equals(ex.getRequestMethod())){
                captured.set(mapper.readTree(ex.getRequestBody()));reply="{\"token\":\"demo-token\"}";
            }else reply="{\"status\":{\"id\":3},\"stdout\":\"NDJcbiA=\",\"time\":\"0.01\",\"wall_time\":\"0.02\",\"memory\":1024,\"exit_code\":0}";
            byte[] b=reply.getBytes();ex.sendResponseHeaders(200,b.length);ex.getResponseBody().write(b);ex.close();
        });server.start();
        try{
            JsonNode normalized=new Judge0Adapter("http://127.0.0.1:"+server.getAddress().getPort(),"").execute(Map.of("language","python","files",List.of(Map.of("content","print(42)")),"stdin","","run_cpu_time",1000,"run_timeout",1000,"run_memory_limit",268435456));
            assertFalse(captured.get().path("enable_network").asBoolean());
            assertEquals(71,captured.get().path("language_id").asInt());
            assertEquals("print(42)",new String(Base64.getDecoder().decode(captured.get().path("source_code").asText())));
            assertEquals(0,normalized.path("run").path("code").asInt());
            assertEquals(20,normalized.path("run").path("wall_time").asInt());
            assertEquals(1048576,normalized.path("run").path("memory").asLong());
        }finally{server.stop(0);}
    }
}
