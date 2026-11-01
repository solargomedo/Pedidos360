package cl.duoc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.amqp.core.Message;
import cl.duoc.pedidos360.orders.messaging.MessagingEvent.*;

/** Single-instance, volatile, bounded observation history. Never stores message bodies. */
@Component
public class MessagingEventHistory {
    private static final int CAPACITY = 1000;
    private static final Pattern ORDER_TEXT = Pattern.compile("^Se ha creado el pedido con ID: ([0-9]{1,19})$");
    private final ArrayDeque<MessagingEvent> events = new ArrayDeque<>();
    private long sequence;
    private final UUID instanceId = UUID.randomUUID();

    public record Page(UUID instanceId, int capacity, long earliestSequence, long lastSequence,
            long nextAfter, boolean truncated, List<MessagingEvent> events) {}

    public synchronized Page page(Long pedidoId, long after, int limit) {
        var snapshot = snapshot(pedidoId, after, limit);
        long earliest = events.isEmpty() ? sequence + 1 : events.getFirst().sequence();
        long next = snapshot.events().isEmpty() ? after
                : snapshot.events().get(snapshot.events().size() - 1).sequence();
        return new Page(instanceId, CAPACITY, earliest, sequence, next,
                after < earliest - 1, snapshot.events());
    }

    public record Context(Long pedidoId, String exchange, String routingKey, String queue, String consumer) {}
    public record Snapshot(int capacity, long lastSequence, List<MessagingEvent> events) {}

    public Context receivedContext(String text, Message message, String queue, String consumer) {
        Long id = null;
        if (text != null) {
            var matcher = ORDER_TEXT.matcher(text);
            if (matcher.matches()) {
                try { id = Long.valueOf(matcher.group(1)); } catch (NumberFormatException ignored) { }
            }
        }
        var properties = message.getMessageProperties();
        return new Context(id, properties.getReceivedExchange(), properties.getReceivedRoutingKey(), queue, consumer);
    }

    /** Observation failures must not affect publication, retries or acknowledgements. */
    public synchronized void record(Context context, Integer attempt, Type type, Result result) {
        try {
            var event = new MessagingEvent(UUID.randomUUID(), ++sequence, Instant.now(), context.pedidoId(),
                    context.exchange(), context.routingKey(), context.queue(), context.consumer(), attempt, type, result);
            if (events.size() == CAPACITY) events.removeFirst();
            events.addLast(event);
        } catch (RuntimeException ignored) {
            // Deliberately do not log payloads, exceptions or credentials.
        }
    }

    /** Immutable, oldest-first snapshot. Cursor and limit apply before returning data. */
    public synchronized Snapshot snapshot(Long pedidoId, long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1 || limit > CAPACITY) throw new IllegalArgumentException("Invalid cursor or limit");
        return new Snapshot(CAPACITY, sequence, events.stream()
                .filter(e -> e.sequence() > afterSequence)
                .filter(e -> pedidoId == null || pedidoId.equals(e.pedidoId()))
                .limit(limit).toList());
    }
}
