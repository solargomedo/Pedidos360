package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PedidoKafkaProcessor {
    private static final Logger log = LoggerFactory.getLogger(PedidoKafkaProcessor.class);
    private final Validator validator;

    public PedidoKafkaProcessor(Validator validator) {
        this.validator = validator;
    }

    public void procesarEvento(PedidoEvento evento) {
        validar(evento);
        log.info("Evento de pedido procesado id={} tipo={} productoId={} cantidad={} estado={}",
                evento.id(), evento.tipo(), evento.productoId(), evento.cantidad(), evento.estado());
    }

    public void procesarAnalitica(PedidoEvento evento) {
        validar(evento);
        log.info("Evento de analitica procesado id={} tipo={} productoId={} cantidad={} estado={}",
                evento.id(), evento.tipo(), evento.productoId(), evento.cantidad(), evento.estado());
    }

    private void validar(PedidoEvento evento) {
        Objects.requireNonNull(evento, "El evento de pedido es obligatorio");
        var violations = validator.validate(evento);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
