package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.OrdersEventsClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/bff/rabbitmq/events")
public class RabbitmqEventsController {
    private final OrdersEventsClient client;
    public RabbitmqEventsController(OrdersEventsClient client) { this.client = client; }

    @GetMapping
    public ResponseEntity<?> events(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="after", defaultValue="0") long after,
            @RequestParam(name="limit", defaultValue="100") int limit,
            @RequestParam(name="pedidoId", required=false) Long pedidoId) {
        if (after < 0 || limit < 1 || limit > 1000 || (pedidoId != null && pedidoId <= 0)) {
            return OrdersEventsClient.error(400, "INVALID_PARAMETERS");
        }
        return client.events(jwt.getTokenValue(), after, limit, pedidoId);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> invalidType() { return OrdersEventsClient.error(400, "INVALID_PARAMETERS"); }
}
