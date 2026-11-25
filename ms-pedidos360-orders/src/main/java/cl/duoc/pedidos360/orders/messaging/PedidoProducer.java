
package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.orders.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidoProducer {

    private final RabbitTemplate rabbitTemplate;
    private final MessagingEventHistory history;

    public PedidoProducer(RabbitTemplate rabbitTemplate, MessagingEventHistory history) {
        this.rabbitTemplate = rabbitTemplate;
        this.history = history;
    }

    public void enviarPedidoCreado(Long pedidoId) {

        String mensaje = "Se ha creado el pedido con ID: " + pedidoId;

        // 1. Enviar mensaje a notificaciones (Direct)
        history.record(new MessagingEventHistory.Context(pedidoId, RabbitMQConfig.PEDIDOS_EXCHANGE, "pedido.creado", null, null),
                null, MessagingEvent.Type.PUBLICATION_REQUESTED, MessagingEvent.Result.REQUESTED);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PEDIDOS_EXCHANGE,
                "pedido.creado",
                mensaje);

        System.out.println("Notificacion enviada: " + mensaje);

        // 2. Enviar mensaje a auditoria (Topic)
        history.record(new MessagingEventHistory.Context(pedidoId, RabbitMQConfig.PEDIDOS_TOPIC_EXCHANGE, "pedido.creado", null, null),
                null, MessagingEvent.Type.PUBLICATION_REQUESTED, MessagingEvent.Result.REQUESTED);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PEDIDOS_TOPIC_EXCHANGE,
                "pedido.creado",
                mensaje);

        System.out.println("Evento de auditoria enviado: " + mensaje);

        // 3. Enviar mensaje a comprobantes (Direct)
        history.record(new MessagingEventHistory.Context(pedidoId, RabbitMQConfig.PEDIDOS_EXCHANGE, "pedido.comprobante", null, null),
                null, MessagingEvent.Type.PUBLICATION_REQUESTED, MessagingEvent.Result.REQUESTED);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PEDIDOS_EXCHANGE,
                "pedido.comprobante",
                mensaje);

        System.out.println("Solicitud de comprobante enviada: " + mensaje);
    }
}
