package cl.duoc.pedidos360.orders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orders-context;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.kafka.listener.auto-startup=false"
})
class MsPedidos360OrdersApplicationTests {

    @Autowired
    KafkaListenerEndpointRegistry listeners;

    @MockitoBean(name = "iniciarRabbitMQ")
    CommandLineRunner iniciarRabbitMQ;

	@Test
    void registraLosTresListenersConGruposIndependientes() {
        var eventos = listeners.getListenerContainer("pedidos-eventos");
        var analitica = listeners.getListenerContainer("pedidos-analitica");
        var seguimiento = listeners.getListenerContainer("pedidos-seguimiento");
        assertNotNull(eventos);
        assertNotNull(analitica);
        assertNotNull(seguimiento);
        assertEquals("pedidos360-orders-eventos", eventos.getGroupId());
        assertEquals("pedidos360-orders-analitica", analitica.getGroupId());
        assertEquals("pedidos360-orders-seguimiento", seguimiento.getGroupId());
        assertArrayEquals(new String[] {"pedidos.eventos"}, eventos.getContainerProperties().getTopics());
        assertArrayEquals(new String[] {"pedidos.eventos"}, seguimiento.getContainerProperties().getTopics());
        assertArrayEquals(new String[] {"pedidos.analitica"}, analitica.getContainerProperties().getTopics());
        assertFalse(seguimiento.isAutoStartup());
    }

}
