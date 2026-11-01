package cl.duoc.pedidos360.bff;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"rabbitmq.admin.read-timeout=1s", "debug=false"})
@AutoConfigureMockMvc
class RabbitmqMonitorIntegrationTest {
    static final String PATH = "/api/bff/rabbitmq/monitor";
    static final HttpServer SERVER;
    static volatile int upstreamStatus = 200;
    static volatile String upstreamBody;
    static volatile long delay;
    static volatile String authorization;
    static final AtomicInteger calls = new AtomicInteger();

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r); thread.setDaemon(true); return thread;
            }));
            SERVER.createContext("/api/admin/rabbitmq/monitor", exchange -> {
                calls.incrementAndGet();
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                int status = upstreamStatus;
                String body = upstreamBody;
                long wait = delay;
                try {
                    Thread.sleep(wait);
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (var output = exchange.getResponseBody()) { output.write(bytes); }
                } catch (Exception ignored) { exchange.close(); }
            });
            SERVER.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("rabbitmq.admin.service.url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @AfterAll static void stop() { SERVER.stop(0); }

    void admin() {
        delay = 0; upstreamStatus = 200; authorization = null;
        upstreamBody = body("available");
        when(decoder.decode("test-admin")).thenReturn(jwt("test-admin", "Admin"));
    }

    static String body(String status) {
        return """
                {"queriedAt":"2026-11-01T20:30:00Z","status":"%s",
                 "nodes":{"resource":"nodes","status":"available","data":[
                   {"name":"rabbit@rabbit1","running":true,"mem_alarm":false,"disk_free_alarm":false,"partitions":[]}],"error":null},
                 "queues":[{"resource":"pedidos.notificaciones","status":"unavailable","data":null,"error":"RESOURCE_NOT_FOUND"}],
                 "exchanges":[],"bindings":{"resource":"bindings","status":"available","data":[],"error":null}}
                """.formatted(status);
    }

    static Jwt jwt(String token, String role) {
        return Jwt.withTokenValue(token).header("alg", "RS256").subject("local-test")
                .issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(300))
                .claim("roles", List.of(role)).build();
    }

    @Test void requiresJwtWithoutCallingAdmin() throws Exception {
        int before = calls.get();
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        assertEquals(before, calls.get());
    }

    @Test void rejectsNonAdminWithoutCallingAdmin() throws Exception {
        when(decoder.decode("operator")).thenReturn(jwt("operator", "Operador"));
        int before = calls.get();
        mvc.perform(get(PATH).header("Authorization", "Bearer operator")).andExpect(status().isForbidden());
        assertEquals(before, calls.get());
    }

    @Test void adminCanReadAndTokenIsForwarded() throws Exception {
        admin();
        mvc.perform(get(PATH).header("Authorization", "Bearer test-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("available"))
                .andExpect(jsonPath("$.nodes.data[0].mem_alarm").value(false))
                .andExpect(header().string("Cache-Control", "no-store"));
        assertEquals("Bearer test-admin", authorization);
    }

    @Test void preservesPartialAndNullMetrics() throws Exception {
        admin(); upstreamBody = body("partial");
        mvc.perform(get(PATH).header("Authorization", "Bearer test-admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("partial"))
                .andExpect(jsonPath("$.queues[0].data").isEmpty())
                .andExpect(jsonPath("$.queues[0].error").value("RESOURCE_NOT_FOUND"));
    }

    @Test void preservesStructuredAdminUnavailableResponse() throws Exception {
        admin(); upstreamStatus = 503; upstreamBody = body("unavailable");
        mvc.perform(get(PATH).header("Authorization", "Bearer test-admin"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.queues[0].error").value("RESOURCE_NOT_FOUND"));
    }

    @Test void sanitizesUpstreamError() throws Exception {
        admin(); upstreamStatus = 500; upstreamBody = "internal-sensitive-information";
        mvc.perform(get(PATH).header("Authorization", "Bearer test-admin"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("unavailable"))
                .andExpect(jsonPath("$.queues").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("internal-sensitive-information"))));
    }

    @Test void handlesTimeout() throws Exception {
        admin(); delay = 2000;
        mvc.perform(get(PATH).header("Authorization", "Bearer test-admin"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("unavailable"));
    }

    @Test void handlesInvalidJson() throws Exception {
        admin(); upstreamBody = "{invalid";
        mvc.perform(get(PATH).header("Authorization", "Bearer test-admin"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test void existingCorsAllowsFrontendPreflight() throws Exception {
        mvc.perform(options(PATH).header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
