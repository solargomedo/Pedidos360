package cl.duoc.pedidos360.orders;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

    @MockitoBean(name = "iniciarRabbitMQ")
    CommandLineRunner iniciarRabbitMQ;

	@Test
	void contextLoads() {
	}

}
