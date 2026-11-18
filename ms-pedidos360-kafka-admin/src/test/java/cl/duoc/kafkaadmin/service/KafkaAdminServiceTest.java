package cl.duoc.kafkaadmin.service;

import cl.duoc.kafkaadmin.dto.CrearTopicoRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.*;
import org.apache.kafka.common.internals.KafkaFutureImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaAdminServiceTest {
    private static final String TOPIC = "prueba.ep5";
    private final AdminClient client = mock(AdminClient.class);
    private final KafkaAdminService service = new KafkaAdminService(client, 100);
    private final ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, TOPIC);

    private void brokers() {
        var result = mock(DescribeClusterResult.class);
        when(client.describeCluster()).thenReturn(result);
        when(result.nodes()).thenReturn(KafkaFuture.completedFuture(List.of(
                new Node(1, "kafka-1", 9092), new Node(2, "kafka-2", 9092),
                new Node(3, "kafka-3", 9092))));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void crearEnviaParticionesReplicasYPoliticasSolicitadas() {
        brokers();
        var result = mock(CreateTopicsResult.class);
        when(client.createTopics(anyCollection())).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        var configs = Map.of("cleanup.policy", "delete", "retention.ms", "604800000");
        service.crear(new CrearTopicoRequest(TOPIC, 3, 3, configs));
        ArgumentCaptor<Collection<NewTopic>> captor = ArgumentCaptor.forClass((Class) Collection.class);
        verify(client).createTopics(captor.capture());
        var topic = captor.getValue().iterator().next();
        assertEquals(TOPIC, topic.name());
        assertEquals(3, topic.numPartitions());
        assertEquals(3, topic.replicationFactor());
        assertEquals(configs, topic.configs());
    }

    @Test
    void rechazaReplicacionMayorALosBrokersDisponibles() {
        brokers();
        assertThrows(InvalidReplicationFactorException.class,
                () -> service.crear(new CrearTopicoRequest(TOPIC, 3, 4, Map.of())));
        verify(client, never()).createTopics(anyCollection());
    }

    @Test
    void listaNombresOrdenadosSinSolicitarTopicosInternos() {
        var result = mock(ListTopicsResult.class);
        when(client.listTopics(any(ListTopicsOptions.class))).thenReturn(result);
        when(result.names()).thenReturn(KafkaFuture.completedFuture(Set.of("prueba.z", TOPIC)));
        assertEquals(List.of(TOPIC, "prueba.z"), service.listar());
        verify(client).listTopics(argThat(options -> !options.shouldListInternal()));
    }

    @Test
    void consultaLiderReplicasEIsr() {
        var nodes = List.of(new Node(1, "kafka-1", 9092), new Node(2, "kafka-2", 9092),
                new Node(3, "kafka-3", 9092));
        var description = new TopicDescription(TOPIC, false,
                List.of(new TopicPartitionInfo(0, nodes.get(0), nodes, nodes)));
        var result = mock(DescribeTopicsResult.class);
        when(client.describeTopics(List.of(TOPIC))).thenReturn(result);
        when(result.allTopicNames()).thenReturn(KafkaFuture.completedFuture(Map.of(TOPIC, description)));
        var response = service.consultar(TOPIC);
        assertEquals(TOPIC, response.nombre());
        assertEquals(1, response.particiones().get(0).lider());
        assertEquals(List.of(1, 2, 3), response.particiones().get(0).replicas());
        assertEquals(List.of(1, 2, 3), response.particiones().get(0).isr());
    }

    @Test
    void consultaConfiguracionesConOrigenYOcultaValoresSensibles() {
        var result = mock(DescribeConfigsResult.class);
        when(client.describeConfigs(List.of(resource))).thenReturn(result);
        var sensitive = mock(ConfigEntry.class);
        when(sensitive.name()).thenReturn("sensible");
        when(sensitive.value()).thenReturn("no-exponer");
        when(sensitive.isSensitive()).thenReturn(true);
        when(sensitive.source()).thenReturn(ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG);
        var config = new Config(List.of(new ConfigEntry("retention.ms", "604800000"), sensitive));
        when(result.all()).thenReturn(KafkaFuture.completedFuture(Map.of(resource, config)));
        var response = service.configuraciones(TOPIC);
        assertEquals("604800000", response.get(0).valor());
        assertNull(response.get(1).valor());
        assertEquals("DYNAMIC_TOPIC_CONFIG", response.get(1).origen());
    }

    @Test
    void modificaSoloLaPropiedadSolicitadaMedianteOperacionIncremental() {
        var result = mock(AlterConfigsResult.class);
        when(client.incrementalAlterConfigs(anyMap())).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        service.modificar(TOPIC, Map.of("retention.ms", "3600000"));
        verify(client).incrementalAlterConfigs(argThat(changes -> {
            var operations = changes.get(resource);
            if (changes.size() != 1 || operations.size() != 1) return false;
            var operation = operations.iterator().next();
            return operation.opType() == AlterConfigOp.OpType.SET
                    && operation.configEntry().name().equals("retention.ms")
                    && operation.configEntry().value().equals("3600000");
        }));
    }

    @Test
    void eliminacionSoloSolicitaPruebaEp5AlClienteSimulado() {
        var result = mock(DeleteTopicsResult.class);
        when(client.deleteTopics(List.of(TOPIC))).thenReturn(result);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        service.eliminar(TOPIC);
        verify(client).deleteTopics(List.of(TOPIC));
        verifyNoMoreInteractions(client);
    }

    @Test
    void nombreInvalidoNoInvocaKafka() {
        for (String name : List.of(".", "..", "", "invalido/ep5", "x".repeat(250))) {
            assertThrows(IllegalArgumentException.class, () -> service.consultar(name));
            assertThrows(IllegalArgumentException.class, () -> service.eliminar(name));
        }
        verifyNoInteractions(client);
    }

    @Test
    void propagaLaCausaKafkaDeUnFutureFallido() {
        var result = mock(DeleteTopicsResult.class);
        when(client.deleteTopics(List.of(TOPIC))).thenReturn(result);
        var future = new KafkaFutureImpl<Void>();
        future.completeExceptionally(new UnknownTopicOrPartitionException("inexistente"));
        when(result.all()).thenReturn(future);
        assertThrows(UnknownTopicOrPartitionException.class, () -> service.eliminar(TOPIC));
    }

    @Test
    void esperaAcotadaSeConvierteEnTimeoutKafka() {
        var result = mock(ListTopicsResult.class);
        when(client.listTopics(any(ListTopicsOptions.class))).thenReturn(result);
        when(result.names()).thenReturn(new KafkaFutureImpl<>());
        assertThrows(TimeoutException.class, service::listar);
    }
}
