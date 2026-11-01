
package cl.duoc.pedidos360.orders.messaging;

import cl.duoc.pedidos360.orders.config.RabbitMQConfig;
import static cl.duoc.pedidos360.orders.messaging.MessagingEvent.Type.*;
import static cl.duoc.pedidos360.orders.messaging.MessagingEvent.Result.*;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

@Component
public class PedidoConsumer {

    private final MessagingEventHistory history;

    public PedidoConsumer(MessagingEventHistory history) { this.history = history; }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICACIONES_QUEUE)
    public void recibirPedidoCreado(String mensaje, Message message, Channel channel)
            throws IOException {

        var observation = history.receivedContext(mensaje, message, RabbitMQConfig.NOTIFICACIONES_QUEUE, "PedidoConsumer");
        history.record(observation, null, RECEIVED, OBSERVED);
        int[] attempt = {0};

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            RetryTemplate retryTemplate = new RetryTemplate();

            // Maximo 3 intentos en total
            retryTemplate.setRetryPolicy(new SimpleRetryPolicy(3));
            retryTemplate.registerListener(new org.springframework.retry.RetryListener() {
                @Override
                public <T, E extends Throwable> void onError(org.springframework.retry.RetryContext context,
                        org.springframework.retry.RetryCallback<T, E> callback, Throwable throwable) {
                    history.record(observation, attempt[0], PROCESSING_ERROR, FAILED);
                }
            });

            retryTemplate.execute(context -> {
                attempt[0] = context.getRetryCount() + 1;
                history.record(observation, attempt[0], PROCESSING_ATTEMPT, STARTED);

                System.out.println(
                        "Intento " + (context.getRetryCount() + 1)
                                + " de procesar: " + mensaje);

                // Error temporal para probar los reintentos
                if (mensaje.contains("PRUEBA_ERROR")) {
                    throw new RuntimeException("Error simulado");
                }

                System.out.println("Mensaje procesado correctamente");

                history.record(observation, attempt[0], PROCESSING_SUCCEEDED, SUCCEEDED);
                return null;
            });

            // Solo confirmamos cuando el procesamiento termina bien
            channel.basicAck(deliveryTag, false);
            history.record(observation, attempt[0], ACK_SENT, SENT);
            System.out.println("ACK enviado a RabbitMQ");

        } catch (Exception e) {

            System.err.println(
                    "Fallaron los 3 intentos: " + e.getMessage());

            // Rechazamos sin reencolar; esto no demuestra su llegada a la DLQ.
            channel.basicNack(deliveryTag, false, false);
            history.record(observation, attempt[0], NACK_SENT, SENT);

            System.out.println("NACK enviado a RabbitMQ");
        }
    }
}
