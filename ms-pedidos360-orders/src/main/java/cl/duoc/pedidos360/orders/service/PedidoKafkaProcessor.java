package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import cl.duoc.pedidos360.orders.repository.PedidoProyeccionRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PedidoKafkaProcessor {
    private static final Logger log = LoggerFactory.getLogger(PedidoKafkaProcessor.class);
    private final Validator validator;
    private final PedidoProyeccionRepository proyecciones;

    public PedidoKafkaProcessor(Validator validator, PedidoProyeccionRepository proyecciones) {
        this.validator = validator;
        this.proyecciones = proyecciones;
    }

    public void procesarEvento(PedidoEvento evento) {
        validar(evento);
        log.info("Evento de pedido procesado id={} tipo={} productoId={} cantidad={} estado={}",
                evento.id(), evento.tipo(), evento.productoId(), evento.cantidad(), evento.estado());
    }

    public void procesarAnalitica(ConsumerRecord<String, PedidoEvento> registro) {
        PedidoEvento evento = validarRegistro(registro);
        proyecciones.guardarAnalitica(evento, registro.partition(), registro.offset());
    }

    public void procesarSeguimiento(ConsumerRecord<String, PedidoEvento> registro) {
        PedidoEvento evento = validarRegistro(registro);
        proyecciones.guardarSeguimiento(evento, registro.partition(), registro.offset());
    }

    private PedidoEvento validarRegistro(ConsumerRecord<String, PedidoEvento> registro) {
        PedidoEvento evento = registro.value();
        if (evento == null) {
            throw new EventoInvalidoException("El registro no contiene un evento de pedido");
        }
        validar(evento);
        if (!evento.id().toString().equals(registro.key())) {
            throw new EventoInvalidoException("La clave Kafka debe coincidir con el ID del pedido");
        }
        return evento;
    }

    public static class EventoInvalidoException extends RuntimeException {
        public EventoInvalidoException(String mensaje) {
            super(mensaje);
        }
    }

    private void validar(PedidoEvento evento) {
        Objects.requireNonNull(evento, "El evento de pedido es obligatorio");
        var violations = validator.validate(evento);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
