package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.orders.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(DeadLetterConsumer.class);

    @RabbitListener(
            queues = RabbitMQConfig.NOTIFICACIONES_DLQ,
            autoStartup = "${pedidos360.dlq.consumer.enabled:false}")
    public void recibirNotificacionFallida(
            String mensaje, Message message, Channel channel) throws IOException {

        log.warn("Mensaje fallido en cola {}: {}",
                RabbitMQConfig.NOTIFICACIONES_DLQ, mensaje);

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(
            queues = RabbitMQConfig.COMPROBANTES_DLQ,
            autoStartup = "${pedidos360.dlq.consumer.enabled:false}")
    public void recibirComprobanteFallido(
            String mensaje, Message message, Channel channel) throws IOException {

        log.warn("Mensaje fallido en cola {}: {}",
                RabbitMQConfig.COMPROBANTES_DLQ, mensaje);

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    @RabbitListener(
            queues = RabbitMQConfig.AUDITORIA_DLQ,
            autoStartup = "${pedidos360.dlq.consumer.enabled:false}")
    public void recibirEventoFallido(
            String mensaje, Message message, Channel channel) throws IOException {

        log.warn("Mensaje fallido en cola {}: {}",
                RabbitMQConfig.AUDITORIA_DLQ, mensaje);

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
