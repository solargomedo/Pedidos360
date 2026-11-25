package cl.duoc.pedidos360.orders.config;

import cl.duoc.pedidos360.orders.service.PedidoKafkaProcessor.EventoInvalidoException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
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
        var reintentoPersistencia = new FixedBackOff(interval, FixedBackOff.UNLIMITED_ATTEMPTS);
        var handler = new DefaultErrorHandler((record, error) ->
                log.error("Kafka descartado topic={} partition={} offset={} key={}",
                        record.topic(), record.partition(), record.offset(), record.key(), error),
                new FixedBackOff(interval, retries));
        handler.addNotRetryableExceptions(ConstraintViolationException.class, EventoInvalidoException.class);
        // Un fallo de BD debe conservar el offset pendiente hasta que la escritura funcione.
        handler.setBackOffFunction((record, error) -> {
            for (Throwable causa = error; causa != null; causa = causa.getCause()) {
                if (causa instanceof DataAccessException) {
                    return reintentoPersistencia;
                }
            }
            return null;
        });
        return handler;
    }
}
