
package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.orders.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidoProducer {

    private final RabbitTemplate rabbitTemplate;

    public PedidoProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void enviarPedidoCreado(Long pedidoId) {

        String mensaje = "Se ha creado el pedido con ID: " + pedidoId;

        // 1. Enviar mensaje a notificaciones (Direct)
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PEDIDOS_EXCHANGE,
                "pedido.creado",
                mensaje);

        System.out.println("Notificacion enviada: " + mensaje);

        // 2. Enviar mensaje a auditoria (Topic)
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PEDIDOS_TOPIC_EXCHANGE,
                "pedido.creado",
                mensaje);

        System.out.println("Evento de auditoria enviado: " + mensaje);

        // 3. Enviar mensaje a comprobantes (Direct)
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.PEDIDOS_EXCHANGE,
                "pedido.comprobante",
                mensaje);

        System.out.println("Solicitud de comprobante enviada: " + mensaje);
    }
}
