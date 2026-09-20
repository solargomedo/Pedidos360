package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.CatalogClient;
import cl.duoc.pedidos360.bff.client.OrdersClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bff")
public class BffController {

    private final OrdersClient ordersClient;
    private final CatalogClient catalogClient;

    public BffController(OrdersClient ordersClient, CatalogClient catalogClient) {
        this.ordersClient = ordersClient;
        this.catalogClient = catalogClient;
    }

    @GetMapping("/orders")
    public ResponseEntity<String> getOrders() {
        return ordersClient.getOrders();
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<String> getOrder(@PathVariable("id") String id) {
        return ordersClient.getOrder(id);
    }

    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createOrder(@RequestBody String body) {
        return ordersClient.createOrder(body);
    }

    @PutMapping(value = "/orders/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateOrder(@PathVariable("id") String id, @RequestBody String body) {
        return ordersClient.updateOrder(id, body);
    }

    @PatchMapping("/orders/{id}/estado")
    public ResponseEntity<String> updateOrderStatus(
            @PathVariable("id") String id, @RequestParam("estado") String estado) {
        return ordersClient.updateOrderStatus(id, estado);
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<String> deleteOrder(@PathVariable("id") String id) {
        return ordersClient.deleteOrder(id);
    }

    @GetMapping("/catalog")
    public ResponseEntity<String> getCatalog() {
        return catalogClient.getProducts();
    }

    @GetMapping("/catalog/{id}")
    public ResponseEntity<String> getProduct(@PathVariable("id") String id) {
        return catalogClient.getProduct(id);
    }

    @PostMapping(value = "/catalog", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> createProduct(@RequestBody String body) {
        return catalogClient.createProduct(body);
    }

    @PutMapping(value = "/catalog/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateProduct(@PathVariable("id") String id, @RequestBody String body) {
        return catalogClient.updateProduct(id, body);
    }

    @DeleteMapping("/catalog/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable("id") String id) {
        return catalogClient.deleteProduct(id);
    }
}
