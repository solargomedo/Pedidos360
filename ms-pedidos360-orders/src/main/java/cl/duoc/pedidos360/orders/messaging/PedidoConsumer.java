
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
public class PedidoConsumer {

    @RabbitListener(queues = RabbitMQConfig.NOTIFICACIONES_QUEUE)
    public void recibirPedidoCreado(String mensaje, Message message, Channel channel)
            throws IOException {

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            RetryTemplate retryTemplate = new RetryTemplate();

            // Maximo 3 intentos en total
            retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));

            retryTemplate.execute(context -> {

                System.out.println(
                        "Intento " + (context.getRetryCount() + 1)
                                + " de procesar: " + mensaje);

                // Error temporal para probar los reintentos
                if (mensaje.contains("PRUEBA_ERROR")) {
                    throw new RuntimeException("Error simulado");
                }

                System.out.println("Mensaje procesado correctamente");

                return null;
            });

            // Solo confirmamos cuando el procesamiento termina bien
            channel.basicAck(deliveryTag, false);
            System.out.println("ACK enviado a RabbitMQ");

        } catch (Exception e) {

            System.err.println(
                    "Fallaron los 3 intentos: " + e.getMessage());

            // Rechazamos el mensaje y RabbitMQ lo envia a la DLQ
            channel.basicNack(deliveryTag, false, false);

            System.out.println("NACK enviado a RabbitMQ");
        }
    }
}
