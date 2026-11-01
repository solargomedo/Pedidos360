package cl.duoc.pedidos360.bff.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MessagingEventsPage(UUID instanceId, Integer capacity, Long earliestSequence,
        Long lastSequence, Long nextAfter, Boolean truncated, List<Event> events) {
    public record Event(UUID eventId, Long sequence, Instant timestamp, Long pedidoId,
            String exchange, String routingKey, String queue, String consumer,
            Integer attempt, String type, String result) {}
}
