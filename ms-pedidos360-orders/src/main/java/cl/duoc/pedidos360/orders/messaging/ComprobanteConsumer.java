
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
public class ComprobanteConsumer {

    private final MessagingEventHistory history;

    public ComprobanteConsumer(MessagingEventHistory history) { this.history = history; }

    @RabbitListener(queues = RabbitMQConfig.COMPROBANTES_QUEUE)
    public void recibirSolicitud(
            String mensaje,
            Message message,
            Channel channel) throws IOException {

        var observation = history.receivedContext(mensaje, message, RabbitMQConfig.COMPROBANTES_QUEUE, "ComprobanteConsumer");
        history.record(observation, null, RECEIVED, OBSERVED);
        int[] attempt = {0};

        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            RetryTemplate retryTemplate = new RetryTemplate();
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

                history.record(observation, attempt[0], PROCESSING_SUCCEEDED, SUCCEEDED);
                return null;
            });

            channel.basicAck(deliveryTag, false);
            history.record(observation, attempt[0], ACK_SENT, SENT);
            System.out.println("Comprobantes - ACK enviado a RabbitMQ");

        } catch (Exception e) {

            System.err.println(
                    "Comprobantes - fallaron los 3 intentos: "
                            + e.getMessage());

            channel.basicNack(deliveryTag, false, false);
            history.record(observation, attempt[0], NACK_SENT, SENT);
            System.out.println("Comprobantes - NACK enviado a RabbitMQ");
        }
    }
}
