package cl.duoc.pedidos360.orders.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(@Value("${catalog.service.url}") String catalogServiceUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(catalogServiceUrl)
                .build();
    }

    public void descontarStock(Long productoId, Integer cantidad) {
        restClient.patch()
                .uri("/api/catalog/productos/{id}/stock?cantidad={cantidad}", productoId, cantidad)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "No fue posible descontar stock: stock insuficiente o cantidad inválida");
                })
                .toBodilessEntity();
    }
}
