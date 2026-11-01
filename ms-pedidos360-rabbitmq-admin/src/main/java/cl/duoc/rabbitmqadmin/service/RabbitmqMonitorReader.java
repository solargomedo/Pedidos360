package cl.duoc.rabbitmqadmin.service;

import cl.duoc.rabbitmqadmin.dto.RabbitmqMonitor;
import cl.duoc.rabbitmqadmin.dto.RabbitmqMonitor.*;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Uses the existing authenticated Management client. Performs GET requests only. */
final class RabbitmqMonitorReader {
    private static final List<String> QUEUES = List.of(
            "pedidos.notificaciones", "pedidos.comprobantes", "pedidos.auditoria",
            "pedidos.notificaciones.dlq", "pedidos.comprobantes.dlq", "pedidos.auditoria.dlq");
    private static final Set<String> EXCHANGES = Set.of("pedidos.direct", "pedidos.topic", "pedidos.dlx");
    private final RestClient client;
    private final String vhost;

    RabbitmqMonitorReader(RestClient client, String encodedVhost) {
        this.client = client;
        this.vhost = encodedVhost;
    }

    RabbitmqMonitor read() {
        Instant queriedAt = Instant.now();
        var nodes = section("nodes", () -> list(get("/api/nodes", Node[].class)));
        var queues = QUEUES.stream().map(name -> section(name,
                () -> get("/api/queues/" + vhost + "/" + name, Queue.class))).toList();
        var exchanges = EXCHANGES.stream().sorted().map(name -> section(name,
                () -> get("/api/exchanges/" + vhost + "/" + name, Exchange.class))).toList();
        var bindings = section("bindings", () -> list(get("/api/bindings/" + vhost, Binding[].class))
                .stream().filter(b -> EXCHANGES.contains(b.source())).toList());
        long ok = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(nodes, bindings),
                java.util.stream.Stream.concat(queues.stream(), exchanges.stream()))
                .filter(s -> "available".equals(s.status())).count();
        String status = ok == 11 ? "available" : ok == 0 ? "unavailable" : "partial";
        return new RabbitmqMonitor(queriedAt, status, nodes, queues, exchanges, bindings);
    }

    private <T> T get(String path, Class<T> type) {
        T result = client.get().uri(URI.create(path)).retrieve().body(type);
        if (result == null) throw new IllegalStateException("Empty Management response");
        return result;
    }

    private static <T> List<T> list(T[] values) {
        return Arrays.asList(values);
    }

    private <T> Section<T> section(String resource, Supplier<T> operation) {
        try {
            return new Section<>(resource, "available", operation.get(), null);
        } catch (RestClientResponseException e) {
            return new Section<>(resource, "unavailable", null,
                    e.getStatusCode().value() == 404 ? "RESOURCE_NOT_FOUND" : "MANAGEMENT_HTTP_ERROR");
        } catch (Exception e) {
            // Never expose upstream bodies, URLs, credentials or exception messages.
            return new Section<>(resource, "unavailable", null, "MANAGEMENT_UNAVAILABLE_OR_INVALID_RESPONSE");
        }
    }
}
