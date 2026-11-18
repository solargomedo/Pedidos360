package cl.duoc.kafkaadmin.service;

import cl.duoc.kafkaadmin.dto.CrearTopicoRequest;
import cl.duoc.kafkaadmin.dto.TopicoResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KafkaAdminService {
    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);
    private final AdminClient client;
    private final int timeout;

    public KafkaAdminService(AdminClient client,
            @Value("${pedidos360.kafka.admin.timeout-ms}") int timeout) {
        this.client = client;
        this.timeout = timeout;
    }

    public List<String> listar() {
        return esperar(client.listTopics(new ListTopicsOptions().listInternal(false)).names())
                .stream().sorted().toList();
    }

    public TopicoResponse consultar(String nombre) {
        validarNombre(nombre);
        var topic = esperar(client.describeTopics(List.of(nombre)).allTopicNames()).get(nombre);
        return new TopicoResponse(topic.name(), topic.partitions().stream().map(partition ->
                new TopicoResponse.Particion(partition.partition(),
                        partition.leader() == null ? null : partition.leader().id(),
                        partition.replicas().stream().map(Node::id).toList(),
                        partition.isr().stream().map(Node::id).toList())).toList());
    }

    public List<Configuracion> configuraciones(String nombre) {
        validarNombre(nombre);
        var resource = recurso(nombre);
        return esperar(client.describeConfigs(List.of(resource)).all()).get(resource).entries()
                .stream().map(entry -> new Configuracion(entry.name(),
                        entry.isSensitive() ? null : entry.value(), entry.source().name(),
                        entry.isReadOnly(), entry.isSensitive()))
                .sorted(java.util.Comparator.comparing(Configuracion::nombre)).toList();
    }

    public void crear(CrearTopicoRequest request) {
        validarNombre(request.nombre());
        int brokers = esperar(client.describeCluster().nodes()).size();
        if (request.factorReplicacion() > brokers) {
            throw new InvalidReplicationFactorException("El factor de replicacion supera los brokers disponibles");
        }
        var topic = new NewTopic(request.nombre(), request.particiones(), request.factorReplicacion().shortValue());
        topic.configs(request.configuraciones() == null ? Map.of() : request.configuraciones());
        esperar(client.createTopics(List.of(topic)).all());
        log.info("Topico creado nombre={} particiones={} replicas={}",
                request.nombre(), request.particiones(), request.factorReplicacion());
    }

    public void modificar(String nombre, Map<String, String> configuraciones) {
        validarNombre(nombre);
        Collection<AlterConfigOp> cambios = configuraciones.entrySet().stream()
                .map(entry -> new AlterConfigOp(new ConfigEntry(entry.getKey(), entry.getValue()),
                        AlterConfigOp.OpType.SET)).toList();
        esperar(client.incrementalAlterConfigs(Map.of(recurso(nombre), cambios)).all());
        log.info("Configuraciones modificadas topico={} claves={}", nombre, configuraciones.keySet());
    }

    public void eliminar(String nombre) {
        validarNombre(nombre);
        esperar(client.deleteTopics(List.of(nombre)).all());
        log.info("Eliminacion de topico aceptada nombre={}", nombre);
    }

    private ConfigResource recurso(String nombre) {
        return new ConfigResource(ConfigResource.Type.TOPIC, nombre);
    }

    private void validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.length() > 249
                || !nombre.matches("[a-zA-Z0-9._-]+") || nombre.equals(".") || nombre.equals("..")) {
            throw new IllegalArgumentException("Nombre de topico invalido");
        }
    }

    private <T> T esperar(KafkaFuture<T> future) {
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operacion Kafka interrumpida", error);
        } catch (TimeoutException error) {
            throw new org.apache.kafka.common.errors.TimeoutException("Tiempo de operacion Kafka agotado", error);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new IllegalStateException("Fallo de operacion Kafka", error.getCause());
        }
    }

    public record Configuracion(String nombre, String valor, String origen,
            boolean soloLectura, boolean sensible) { }
}
