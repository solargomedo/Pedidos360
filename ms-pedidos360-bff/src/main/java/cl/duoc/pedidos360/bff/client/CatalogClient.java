package cl.duoc.pedidos360.bff.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(@Value("${catalog.service.url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                // Devolver los errores HTTP del microservicio al consumidor del BFF.
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {})
                .build();
    }

    public ResponseEntity<String> getProducts() {
        return sanitizeErrorResponse(restClient.get()
                .uri("/api/catalog/productos")
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> getProduct(String id) {
        return sanitizeErrorResponse(restClient.get()
                .uri("/api/catalog/productos/{id}", id)
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> createProduct(String body) {
        return sanitizeErrorResponse(restClient.post()
                .uri("/api/catalog/productos")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> updateProduct(String id, String body) {
        return sanitizeErrorResponse(restClient.put()
                .uri("/api/catalog/productos/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class));
    }

    public ResponseEntity<String> deleteProduct(String id) {
        return sanitizeErrorResponse(restClient.delete()
                .uri("/api/catalog/productos/{id}", id)
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
