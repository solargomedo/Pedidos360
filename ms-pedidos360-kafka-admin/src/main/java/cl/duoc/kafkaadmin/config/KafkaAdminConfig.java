package cl.duoc.kafkaadmin.config;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaAdminConfig {
    @Bean(destroyMethod = "")
    public AdminClient adminClient(
            @Value("${spring.kafka.bootstrap-servers}") String brokers,
            @Value("${pedidos360.kafka.admin.timeout-ms}") int timeout) {
        if (timeout <= 0) {
            throw new IllegalArgumentException("El timeout Kafka debe ser positivo");
        }
        return AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, brokers,
                AdminClientConfig.CLIENT_ID_CONFIG, "pedidos360-kafka-admin",
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeout,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeout));
    }

    @Bean
    public AutoCloseable adminClientShutdown(AdminClient client) {
        return () -> client.close(Duration.ofSeconds(5));
    }
}
