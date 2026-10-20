
package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.orders.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuditoriaConsumer {

    @RabbitListener(queues = RabbitMQConfig.AUDITORIA_QUEUE)
    public void recibirEvento(
            String mensaje,
            Message message,
            Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            RetryTemplate retryTemplate = new RetryTemplate();
            retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));

            retryTemplate.execute(context -> {

                System.out.println(
                        "Auditoria - intento "
                                + (context.getRetryCount() + 1)
                                + ": " + mensaje);

                // Error simulado para probar los reintentos
                if (mensaje.contains("PRUEBA_ERROR")) {
                    throw new RuntimeException("Error simulado en auditoria");
                }

                System.out.println(
                        "Auditoria - evento procesado correctamente");

                return null;
            });

            channel.basicAck(deliveryTag, false);
            System.out.println("Auditoria - ACK enviado a RabbitMQ");

        } catch (Exception e) {

            System.err.println(
                    "Auditoria - fallaron los 3 intentos: "
                            + e.getMessage());

            channel.basicNack(deliveryTag, false, false);
            System.out.println("Auditoria - NACK enviado a RabbitMQ");
        }
    }
}
