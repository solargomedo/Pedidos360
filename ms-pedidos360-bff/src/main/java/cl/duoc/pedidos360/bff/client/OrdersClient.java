package cl.duoc.pedidos360.bff.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OrdersClient {

    private final RestClient restClient;

    public OrdersClient(@Value("${orders.service.url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                // Devolver los errores HTTP del microservicio al consumidor del BFF.
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {})
                .build();
    }

    public ResponseEntity<String> getOrders() {
        return sanitizeErrorResponse(restClient.get()
                .uri("/api/orders")
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> getOrder(String id) {
        return sanitizeErrorResponse(restClient.get()
                .uri("/api/orders/{id}", id)
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> createOrder(String body) {
        return sanitizeErrorResponse(restClient.post()
                .uri("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> updateOrder(String id, String body) {
        return sanitizeErrorResponse(restClient.put()
                .uri("/api/orders/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> updateOrderStatus(String id, String estado) {
        return sanitizeErrorResponse(restClient.patch()
                .uri("/api/orders/{id}/estado?estado={estado}", id, estado)
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> deleteOrder(String id) {
        return sanitizeErrorResponse(restClient.delete()
                .uri("/api/orders/{id}", id)
                .retrieve()
                .toEntity(String.class));
    }

    private ResponseEntity<String> sanitizeErrorResponse(ResponseEntity<String> upstream) {
        if (!upstream.getStatusCode().isError()) {
            return upstream;
        }

        // Spring genera los headers HTTP, CORS y de transporte del BFF.
        ResponseEntity.BodyBuilder response = ResponseEntity.status(upstream.getStatusCode());
        MediaType contentType = upstream.getHeaders().getContentType();
        if (contentType != null) {
            response.contentType(contentType);
        }
        return response.body(upstream.getBody());
    }
}
