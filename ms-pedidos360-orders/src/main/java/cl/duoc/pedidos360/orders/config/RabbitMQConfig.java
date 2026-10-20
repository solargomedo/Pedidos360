
package cl.duoc.pedidos360.orders.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.amqp.core.TopicExchange;

@Configuration
public class RabbitMQConfig {

    public static final String PEDIDOS_EXCHANGE = "pedidos.direct";
    public static final String PEDIDOS_TOPIC_EXCHANGE = "pedidos.topic";

    public static final String NOTIFICACIONES_QUEUE = "pedidos.notificaciones";
    public static final String NOTIFICACIONES_DLQ = "pedidos.notificaciones.dlq";

    public static final String COMPROBANTES_QUEUE = "pedidos.comprobantes";
    public static final String COMPROBANTES_DLQ = "pedidos.comprobantes.dlq";

    public static final String AUDITORIA_QUEUE = "pedidos.auditoria";
    public static final String AUDITORIA_DLQ = "pedidos.auditoria.dlq";

    public static final String PEDIDOS_DLX = "pedidos.dlx";

    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(PEDIDOS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange pedidosTopicExchange() {
        return new TopicExchange(PEDIDOS_TOPIC_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificacionesQueue() {
        return QueueBuilder.durable(NOTIFICACIONES_QUEUE)
                .withArgument("x-dead-letter-exchange", PEDIDOS_DLX)
                .withArgument("x-dead-letter-routing-key", NOTIFICACIONES_DLQ)
                .build();
    }

    @Bean
    public DirectExchange pedidosDeadLetterExchange() {
        return new DirectExchange(PEDIDOS_DLX, true, false);
    }

    @Bean
    public Queue notificacionesDeadLetterQueue() {
        return QueueBuilder.durable(NOTIFICACIONES_DLQ)
                .build();
    }

    // Cola principal de comprobantes
    @Bean
    public Queue comprobantesQueue() {
        return QueueBuilder.durable(COMPROBANTES_QUEUE)
                .withArgument("x-dead-letter-exchange", PEDIDOS_DLX)
                .withArgument("x-dead-letter-routing-key", COMPROBANTES_DLQ)
                .build();
    }

    // Cola de errores de comprobantes
    @Bean
    public Queue comprobantesDeadLetterQueue() {
        return QueueBuilder.durable(COMPROBANTES_DLQ)
                .build();
    }

    // Cola principal de auditoria
    @Bean
    public Queue auditoriaQueue() {
        return QueueBuilder.durable(AUDITORIA_QUEUE)
                .withArgument("x-dead-letter-exchange", PEDIDOS_DLX)
                .withArgument("x-dead-letter-routing-key", AUDITORIA_DLQ)
                .build();
    }

    // Cola de errores de auditoria
    @Bean
    public Queue auditoriaDeadLetterQueue() {
        return QueueBuilder.durable(AUDITORIA_DLQ)
                .build();
    }

    @Bean
    public Binding notificacionesBinding() {
        return BindingBuilder
                .bind(notificacionesQueue())
                .to(pedidosExchange())
                .with("pedido.creado");
    }

    @Bean
    public Binding notificacionesDeadLetterBinding() {
        return BindingBuilder
                .bind(notificacionesDeadLetterQueue())
                .to(pedidosDeadLetterExchange())
                .with(NOTIFICACIONES_DLQ);
    }

    // Conecta comprobantes al exchange Direct
    @Bean
    public Binding comprobantesBinding() {
        return BindingBuilder
                .bind(comprobantesQueue())
                .to(pedidosExchange())
                .with("pedido.comprobante");
    }

    // Conecta comprobantes DLQ al exchange de errores
    @Bean
    public Binding comprobantesDeadLetterBinding() {
        return BindingBuilder
                .bind(comprobantesDeadLetterQueue())
                .to(pedidosDeadLetterExchange())
                .with(COMPROBANTES_DLQ);
    }

    // Conecta auditoria al exchange Direct

    @Bean
    public Binding auditoriaBinding() {
        return BindingBuilder
                .bind(auditoriaQueue())
                .to(pedidosTopicExchange())
                .with("pedido.*");
    }

    // Conecta auditoria DLQ al exchange de errores
    @Bean
    public Binding auditoriaDeadLetterBinding() {
        return BindingBuilder
                .bind(auditoriaDeadLetterQueue())
                .to(pedidosDeadLetterExchange())
                .with(AUDITORIA_DLQ);
    }

    @Bean
    public CommandLineRunner iniciarRabbitMQ(ConnectionFactory connectionFactory) {
        return args -> {
            var connection = connectionFactory.createConnection();
            try {
                System.out.println("RabbitMQ conectado correctamente");
            } finally {
                connection.close();
            }
        };
    }

}
