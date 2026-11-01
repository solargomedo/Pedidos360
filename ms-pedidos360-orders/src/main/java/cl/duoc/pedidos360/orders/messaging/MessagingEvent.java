package cl.duoc.pedidos360.orders.messaging;

import java.time.Instant;
import java.util.UUID;

public record MessagingEvent(UUID eventId, long sequence, Instant timestamp, Long pedidoId,
        String exchange, String routingKey, String queue, String consumer,
        Integer attempt, Type type, Result result) {
    public enum Type { PUBLICATION_REQUESTED, RECEIVED, PROCESSING_ATTEMPT,
        PROCESSING_SUCCEEDED, ACK_SENT, NACK_SENT, PROCESSING_ERROR }
    public enum Result { REQUESTED, OBSERVED, STARTED, SUCCEEDED, SENT, FAILED }
}
