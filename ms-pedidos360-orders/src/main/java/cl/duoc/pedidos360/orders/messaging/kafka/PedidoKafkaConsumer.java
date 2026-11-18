package cl.duoc.pedidos360.orders.messaging.kafka;

import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import cl.duoc.pedidos360.orders.service.PedidoKafkaProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoKafkaConsumer {
    private final PedidoKafkaProcessor processor;

    public PedidoKafkaConsumer(PedidoKafkaProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "${pedidos360.kafka.topics.eventos}",
            groupId = "${pedidos360.kafka.groups.eventos}")
    public void recibirEvento(PedidoEvento evento) {
        processor.procesarEvento(evento);
    }

    @KafkaListener(topics = "${pedidos360.kafka.topics.analitica}",
            groupId = "${pedidos360.kafka.groups.analitica}")
    public void recibirAnalitica(PedidoEvento evento) {
        processor.procesarAnalitica(evento);
    }
}
