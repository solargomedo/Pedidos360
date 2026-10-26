
package cl.duoc.rabbitmqadmin.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

@Service
public class RabbitmqAdminService {

        private final RestClient restClient;

        public RabbitmqAdminService(
                        @Value("${rabbitmq.management.url}") String url,
                        @Value("${rabbitmq.management.username}") String username,
                        @Value("${rabbitmq.management.password}") String password) {

                // Forzamos HTTP/1.1 para comunicarnos con RabbitMQ.
                HttpClient httpClient = HttpClient.newBuilder()
                                .version(HttpClient.Version.HTTP_1_1)
                                .build();

                this.restClient = RestClient.builder()
                                .baseUrl(url)
                                .requestFactory(
                                                new JdkClientHttpRequestFactory(httpClient))
                                .defaultHeaders(headers -> headers.setBasicAuth(
                                                username,
                                                password,
                                                StandardCharsets.UTF_8))
                                .build();
        }

        // Crear una cola en RabbitMQ
        public void crearCola(String nombre) {

                validarNombre(nombre);

                restClient.put()
                                .uri(uriCola(nombre))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Map.of(
                                                "durable", true,
                                                "auto_delete", false,
                                                "arguments", Map.of()))
                                .retrieve()
                                .toBodilessEntity();
        }

        public void crearExchange(String nombre, String tipo) {

                validarNombre(nombre);

                if (!tipo.equals("direct") &&
                                !tipo.equals("topic") &&
                                !tipo.equals("fanout")) {

                        throw new IllegalArgumentException(
                                        "El tipo debe ser direct, topic o fanout");
                }

                String nombreCodificado = UriUtils.encodePathSegment(
                                nombre, StandardCharsets.UTF_8);

                restClient.put()
                                .uri(URI.create(
                                                "/api/exchanges/%2F/" + nombreCodificado))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Map.of(
                                                "type", tipo,
                                                "durable", true,
                                                "auto_delete", false,
                                                "internal", false,
                                                "arguments", Map.of()))
                                .retrieve()
                                .toBodilessEntity();
        }

        public void eliminarExchange(String nombre) {
                validarNombre(nombre);

                if (!nombre.startsWith("prueba.")) {
                        throw new IllegalArgumentException(
                                        "Por seguridad, solo se pueden eliminar exchanges de prueba");
                }

                String nombreCodificado = UriUtils.encodePathSegment(
                                nombre, StandardCharsets.UTF_8);

                restClient.delete()
                                .uri(URI.create(
                                                "/api/exchanges/%2F/" + nombreCodificado
                                                                + "?if-unused=true"))
                                .retrieve()
                                .toBodilessEntity();
        }

        public void crearBinding(
                        String exchange,
                        String cola,
                        String routingKey) {

                validarNombre(exchange);
                validarNombre(cola);

                if (routingKey == null) {
                        throw new IllegalArgumentException(
                                        "La routing key es obligatoria");
                }

                String exchangeCodificado = UriUtils.encodePathSegment(
                                exchange, StandardCharsets.UTF_8);

                String colaCodificada = UriUtils.encodePathSegment(
                                cola, StandardCharsets.UTF_8);

                String ruta = "/api/bindings/%2F/e/"
                                + exchangeCodificado
                                + "/q/"
                                + colaCodificada;

                restClient.post()
                                .uri(URI.create(ruta))
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(Map.of(
                                                "routing_key", routingKey,
                                                "arguments", Map.of()))
                                .retrieve()
                                .toBodilessEntity();
        }

        public void eliminarBinding(
                        String exchange,
                        String cola,
                        String routingKey) {

                validarNombre(exchange);
                validarNombre(cola);

                if (!exchange.startsWith("prueba.")
                                || !cola.startsWith("prueba.")) {
                        throw new IllegalArgumentException(
                                        "Solo se pueden eliminar bindings de prueba");
                }

                if (routingKey == null) {
                        throw new IllegalArgumentException(
                                        "La routing key es obligatoria");
                }

                String exchangeCodificado = UriUtils.encodePathSegment(
                                exchange, StandardCharsets.UTF_8);

                String colaCodificada = UriUtils.encodePathSegment(
                                cola, StandardCharsets.UTF_8);

                String routingKeyCodificada = UriUtils.encodePathSegment(
                                routingKey, StandardCharsets.UTF_8);

                restClient.delete()
                                .uri(URI.create(
                                                "/api/bindings/%2F/e/"
                                                                + exchangeCodificado
                                                                + "/q/"
                                                                + colaCodificada
                                                                + "/"
                                                                + routingKeyCodificada))
                                .retrieve()
                                .toBodilessEntity();
        }

        // Eliminar una cola de prueba
        public void eliminarCola(String nombre) {

                validarNombre(nombre);

                // Protegemos las colas principales de Pedidos360.
                if (!nombre.startsWith("prueba.")) {
                        throw new IllegalArgumentException(
                                        "Por seguridad, solo se pueden eliminar colas de prueba");
                }

                // Solo elimina colas vacias y sin consumidores.
                restClient.delete()
                                .uri(URI.create(
                                                uriCola(nombre).toString()
                                                                + "?if-empty=true&if-unused=true"))
                                .retrieve()
                                .toBodilessEntity();
        }

        // Construir la ruta de la cola sin duplicar la codificacion.
        private URI uriCola(String nombre) {

                String nombreCodificado = UriUtils.encodePathSegment(
                                nombre, StandardCharsets.UTF_8);

                return URI.create(
                                "/api/queues/%2F/" + nombreCodificado);
        }

        // Validar que el nombre de la cola sea correcto.
        private void validarNombre(String nombre) {

                if (nombre == null || nombre.isBlank()) {
                        throw new IllegalArgumentException(
                                        "El nombre de la cola es obligatorio");
                }
        }
}
