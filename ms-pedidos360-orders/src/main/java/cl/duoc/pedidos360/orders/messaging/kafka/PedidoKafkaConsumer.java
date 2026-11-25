package cl.duoc.pedidos360.orders.messaging.kafka;

import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import cl.duoc.pedidos360.orders.service.PedidoKafkaProcessor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoKafkaConsumer {
    private final PedidoKafkaProcessor processor;

    public PedidoKafkaConsumer(PedidoKafkaProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(id = "pedidos-eventos", topics = "${pedidos360.kafka.topics.eventos}",
            groupId = "${pedidos360.kafka.groups.eventos}")
    public void recibirEvento(PedidoEvento evento) {
        processor.procesarEvento(evento);
    }

    @KafkaListener(id = "pedidos-analitica", topics = "${pedidos360.kafka.topics.analitica}",
            groupId = "${pedidos360.kafka.groups.analitica}")
    public void recibirAnalitica(ConsumerRecord<String, PedidoEvento> registro) {
        processor.procesarAnalitica(registro);
    }

    @KafkaListener(id = "pedidos-seguimiento", topics = "${pedidos360.kafka.topics.eventos}",
            groupId = "${pedidos360.kafka.groups.seguimiento}",
            autoStartup = "#{${spring.kafka.listener.auto-startup} && ${pedidos360.kafka.seguimiento.enabled}}")
    public void recibirSeguimiento(ConsumerRecord<String, PedidoEvento> registro) {
        processor.procesarSeguimiento(registro);
    }
}
