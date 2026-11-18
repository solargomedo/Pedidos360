package cl.duoc.pedidos360.orders.config;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            @Value("${pedidos360.kafka.retry.interval-ms}") long interval,
            @Value("${pedidos360.kafka.retry.max-retries}") long retries) {
        if (interval < 0 || retries < 0) {
            throw new IllegalArgumentException("Los limites de reintentos Kafka deben ser no negativos");
        }
        // Al agotar reintentos se registra el fallo y se avanza el offset.
        // No se crean topicos adicionales ni se repiten operaciones de pedidos.
        var handler = new DefaultErrorHandler((record, error) ->
                log.error("Kafka descartado topic={} partition={} offset={} key={}",
                        record.topic(), record.partition(), record.offset(), record.key(), error),
                new FixedBackOff(interval, retries));
        handler.addNotRetryableExceptions(ConstraintViolationException.class);
        return handler;
    }
}
