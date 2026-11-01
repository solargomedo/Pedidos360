package cl.duoc.rabbitmqadmin.service;

import cl.duoc.rabbitmqadmin.dto.RabbitmqMonitor;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;

class RabbitmqMonitorReaderTest {
    @Test
    void readsMetricsAndPreservesMissingValuesAndFiltersBindings() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> {
            assertEquals("GET", exchange.getRequestMethod());
            String path = exchange.getRequestURI().getPath();
            String body;
            int status = 200;
            if (path.endsWith("/nodes")) {
                body = "[{\"name\":\"rabbit@rabbit1\",\"running\":true,\"mem_alarm\":false,\"disk_free_alarm\":true,\"partitions\":[]},{\"name\":\"rabbit@rabbit2\",\"running\":false}]";
            } else if (path.contains("notificaciones.dlq")) {
                body = "not found"; status = 404;
            } else if (path.contains("/queues/")) {
                body = "{\"name\":\"pedidos.notificaciones\",\"messages_ready\":7,\"messages_unacknowledged\":2,\"messages\":9}";
            } else if (path.contains("/exchanges/")) {
                body = "{\"name\":\"pedidos.direct\",\"type\":\"direct\",\"durable\":true}";
            } else {
                body = "[{\"source\":\"pedidos.direct\",\"destination\":\"pedidos.notificaciones\",\"destination_type\":\"queue\",\"routing_key\":\"pedido.creado\"},{\"source\":\"other\"}]";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var monitor = new RabbitmqMonitorReader(RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build(), "%2F").read();
            assertEquals("partial", monitor.status());
            assertNotNull(monitor.queriedAt());
            assertEquals(2, monitor.nodes().data().size());
            assertTrue(monitor.nodes().data().get(0).diskAlarm());
            assertEquals(6, monitor.queues().size());
            assertEquals(7L, monitor.queues().get(0).data().messagesReady());
            assertNull(monitor.queues().get(0).data().consumers());
            assertNull(monitor.queues().get(3).data());
            assertEquals("RESOURCE_NOT_FOUND", monitor.queues().get(3).error());
            assertEquals(3, monitor.exchanges().size());
            assertEquals(1, monitor.bindings().data().size());
        } finally { server.stop(0); }
    }

    @Test
    void unavailableManagementReturnsNullDataWithoutLeakingUpstreamBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/", exchange -> {
            byte[] bytes = "sensitive upstream detail".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(503, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            RabbitmqMonitor monitor = new RabbitmqMonitorReader(RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).build(), "vhost").read();
            assertEquals("unavailable", monitor.status());
            assertNull(monitor.nodes().data());
            assertEquals("MANAGEMENT_HTTP_ERROR", monitor.nodes().error());
            assertTrue(monitor.queues().stream().allMatch(q -> q.data() == null));
            assertFalse(monitor.toString().contains("sensitive"));
        } finally { server.stop(0); }
    }
}
