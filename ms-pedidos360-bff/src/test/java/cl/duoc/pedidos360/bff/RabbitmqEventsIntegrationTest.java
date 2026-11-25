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

@SpringBootTest(properties = {"orders.events.read-timeout=1s", "debug=false"})
@AutoConfigureMockMvc
class RabbitmqEventsIntegrationTest {
    static final String PATH = "/api/bff/rabbitmq/events";
    static final HttpServer SERVER;
    static volatile int upstreamStatus = 200;
    static volatile String upstreamBody;
    static volatile long delay;
    static volatile String authorization;
    static volatile String query;
    static final AtomicInteger calls = new AtomicInteger();

    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.setExecutor(java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread thread = new Thread(r); thread.setDaemon(true); return thread;
            }));
            SERVER.createContext("/api/orders/messaging/events", exchange -> {
                calls.incrementAndGet();
                query = exchange.getRequestURI().getRawQuery();
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
        registry.add("orders.service.url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @Autowired MockMvc mvc;
    @MockitoBean JwtDecoder decoder;
    @AfterAll static void stop() { SERVER.stop(0); }

    void admin() {
        delay = 0; upstreamStatus = 200; authorization = null;
        upstreamBody = body("available");
        when(decoder.decode("test-admin")).thenReturn(jwt("test-admin", "Admin"));
    }

    static String body(String ignored) {
        return """
          {"instanceId":"12345678-1234-1234-1234-123456789012","capacity":1000,
           "earliestSequence":5,"lastSequence":10,"nextAfter":6,"truncated":true,
           "events":[{"eventId":"12345678-1234-1234-1234-123456789013","sequence":6,
           "timestamp":"2026-11-01T20:30:00Z","pedidoId":42,"exchange":"pedidos.direct",
           "routingKey":"pedido.creado","queue":null,"consumer":null,"attempt":null,
           "type":"PUBLICATION_REQUESTED","result":"REQUESTED"}]}
          """;
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

    @Test void forwardsTokenAndPaginationWithoutChangingHistory() throws Exception {
        admin();
        mvc.perform(get(PATH).param("after","5").param("limit","1").param("pedidoId","42")
                .header("Authorization","Bearer test-admin"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.instanceId").value("12345678-1234-1234-1234-123456789012"))
                .andExpect(jsonPath("$.nextAfter").value(6)).andExpect(jsonPath("$.earliestSequence").value(5))
                .andExpect(jsonPath("$.lastSequence").value(10)).andExpect(jsonPath("$.truncated").value(true))
                .andExpect(jsonPath("$.events[0].pedidoId").value(42))
                .andExpect(jsonPath("$.events[0].type").value("PUBLICATION_REQUESTED"))
                .andExpect(jsonPath("$.events[0].queue").isEmpty());
        assertEquals("Bearer test-admin", authorization);
        assertEquals("after=5&limit=1&pedidoId=42",query);
    }

    @Test void defaultsAndSuccessfulEmptyPageArePreserved() throws Exception {
        admin();
        upstreamBody = """
          {"instanceId":"12345678-1234-1234-1234-123456789012","capacity":1000,
          "earliestSequence":1,"lastSequence":0,"nextAfter":0,"truncated":false,"events":[]}
          """;
        mvc.perform(get(PATH).header("Authorization","Bearer test-admin"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.events.length()").value(0));
        assertEquals("after=0&limit=100",query);
    }

    @Test void invalidParametersDoNotReachOrders() throws Exception {
        admin(); int before=calls.get();
        for(String params:List.of("after=-1","limit=0","limit=1001","pedidoId=0","pedidoId=-1",
                "after=private-input","limit=99999999999999999999")) {
            mvc.perform(get(PATH+"?"+params).header("Authorization","Bearer test-admin"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_PARAMETERS"))
                .andExpect(header().string("Cache-Control","no-store"));
        }
        assertEquals(before,calls.get());
    }

    @Test void upstreamErrorsArePreservedOrMappedAndSanitized() throws Exception {
        admin();
        for(int code:List.of(400,401,403,500,503,302)) {
            upstreamStatus=code; upstreamBody="private-upstream-detail";
            mvc.perform(get(PATH).header("Authorization","Bearer test-admin"))
                .andExpect(status().is(code<500 && code!=302 ? code : 503))
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-upstream-detail"))));
        }
    }

    @Test void invalidOrMissingUpstreamDataIsNotSuccessfulEmptyHistory() throws Exception {
        admin();
        for(String value:List.of("{invalid","{}","null")) {
            upstreamBody=value;
            mvc.perform(get(PATH).header("Authorization","Bearer test-admin"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("ORDERS_UNAVAILABLE"));
        }
    }

    @Test void timeoutIs503() throws Exception {
        admin(); delay=2000;
        mvc.perform(get(PATH).header("Authorization","Bearer test-admin"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error").value("ORDERS_UNAVAILABLE"));
    }

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    cl.duoc.pedidos360.bff.client.OrdersClient orders;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    cl.duoc.pedidos360.bff.client.CatalogClient catalog;

    @Test void existingOrdersAndCatalogRoutesStillDelegate() throws Exception {
        admin();
        when(orders.getOrders()).thenReturn(org.springframework.http.ResponseEntity.ok("[]"));
        when(orders.getOrder("1")).thenReturn(org.springframework.http.ResponseEntity.ok("{}"));
        when(orders.createOrder("{}")).thenReturn(org.springframework.http.ResponseEntity.status(201).body("{}"));
        when(orders.updateOrder("1","{}")).thenReturn(org.springframework.http.ResponseEntity.ok("{}"));
        when(orders.updateOrderStatus("1","ACEPTADO")).thenReturn(org.springframework.http.ResponseEntity.ok("{}"));
        when(orders.deleteOrder("1")).thenReturn(org.springframework.http.ResponseEntity.noContent().build());
        when(catalog.getProducts()).thenReturn(org.springframework.http.ResponseEntity.ok("[]"));
        mvc.perform(get("/api/bff/orders").header("Authorization","Bearer test-admin")).andExpect(status().isOk());
        mvc.perform(get("/api/bff/orders/1").header("Authorization","Bearer test-admin")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/bff/orders")
            .contentType("application/json").content("{}").header("Authorization","Bearer test-admin")).andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/bff/orders/1")
            .contentType("application/json").content("{}").header("Authorization","Bearer test-admin")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/bff/orders/1/estado")
            .param("estado","ACEPTADO").header("Authorization","Bearer test-admin")).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/bff/orders/1")
            .header("Authorization","Bearer test-admin")).andExpect(status().isNoContent());
        mvc.perform(get("/api/bff/catalog").header("Authorization","Bearer test-admin")).andExpect(status().isOk());
        org.mockito.Mockito.verify(orders).createOrder("{}");
        org.mockito.Mockito.verify(orders).deleteOrder("1");
    }
}
