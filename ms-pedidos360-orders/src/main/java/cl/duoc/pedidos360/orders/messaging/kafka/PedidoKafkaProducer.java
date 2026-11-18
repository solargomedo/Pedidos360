package cl.duoc.pedidos360.orders.messaging.kafka;

import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidoKafkaProducer {
    private static final Logger log = LoggerFactory.getLogger(PedidoKafkaProducer.class);
    private final KafkaTemplate<String, PedidoEvento> template;
    private final Validator validator;
    private final String eventos;
    private final String analitica;

    public PedidoKafkaProducer(KafkaTemplate<String, PedidoEvento> template, Validator validator,
            @Value("${pedidos360.kafka.topics.eventos}") String eventos,
            @Value("${pedidos360.kafka.topics.analitica}") String analitica) {
        this.template = template;
        this.validator = validator;
        this.eventos = eventos;
        this.analitica = analitica;
    }

    // Kafka es adicional: sus fallos no revierten ni repiten la operacion de negocio.
    public void publicar(PedidoEvento evento) {
        var violations = validator.validate(evento);
        if (!violations.isEmpty()) {
            log.error("Evento Kafka invalido para pedido {}", evento.id(),
                    new ConstraintViolationException(violations));
            return;
        }
        enviar(eventos, evento);
        enviar(analitica, evento);
    }

    private void enviar(String topic, PedidoEvento evento) {
        try {
            template.send(topic, evento.id().toString(), evento).whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Publicacion Kafka fallida topic={} pedido={} tipo={}",
                            topic, evento.id(), evento.tipo(), error);
                } else {
                    var metadata = result.getRecordMetadata();
                    log.info("Kafka publicado topic={} partition={} offset={} pedido={} tipo={}",
                            topic, metadata.partition(), metadata.offset(), evento.id(), evento.tipo());
                }
            });
        } catch (RuntimeException error) {
            log.error("No se pudo enviar a Kafka topic={} pedido={} tipo={}",
                    topic, evento.id(), evento.tipo(), error);
        }
    }
}
