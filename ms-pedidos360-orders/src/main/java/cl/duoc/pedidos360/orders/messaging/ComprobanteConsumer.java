
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
public class ComprobanteConsumer {

    @RabbitListener(queues = RabbitMQConfig.COMPROBANTES_QUEUE)
    public void recibirSolicitud(
            String mensaje,
            Message message,
            Channel channel) throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            RetryTemplate retryTemplate = new RetryTemplate();
            retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));

            retryTemplate.execute(context -> {

                System.out.println(
                        "Comprobantes - intento "
                                + (context.getRetryCount() + 1)
                                + ": " + mensaje);

                // Error temporal para probar los reintentos
                if (mensaje.contains("PRUEBA_ERROR")) {
                    throw new RuntimeException(
                            "Error simulado en comprobantes");
                }

                System.out.println(
                        "Comprobantes - solicitud procesada correctamente");

                return null;
            });

            channel.basicAck(deliveryTag, false);
            System.out.println("Comprobantes - ACK enviado a RabbitMQ");

        } catch (Exception e) {

            System.err.println(
                    "Comprobantes - fallaron los 3 intentos: "
                            + e.getMessage());

            channel.basicNack(deliveryTag, false, false);
            System.out.println("Comprobantes - NACK enviado a RabbitMQ");
        }
    }
}
