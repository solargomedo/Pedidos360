package cl.duoc.rabbitmqadmin.dto;

import java.time.Instant;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Nullable metrics mean unavailable, never zero by default. */
public record RabbitmqMonitor(Instant queriedAt, String status,
        Section<List<Node>> nodes, List<Section<Queue>> queues,
        List<Section<Exchange>> exchanges, Section<List<Binding>> bindings) {
    public record Section<T>(String resource, String status, T data, String error) {}
    public record Node(String name, Boolean running,
            @JsonProperty("mem_alarm") Boolean memoryAlarm,
            @JsonProperty("disk_free_alarm") Boolean diskAlarm,
            List<String> partitions) {}
    public record Queue(String name,
            @JsonProperty("messages_ready") Long messagesReady,
            @JsonProperty("messages_unacknowledged") Long messagesUnacknowledged,
            Long messages, Long consumers) {}
    public record Exchange(String name, String type, Boolean durable) {}
    public record Binding(String source, String destination,
            @JsonProperty("destination_type") String destinationType,
            @JsonProperty("routing_key") String routingKey) {}
}
