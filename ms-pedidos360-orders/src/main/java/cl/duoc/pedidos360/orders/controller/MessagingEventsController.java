package cl.duoc.pedidos360.orders.controller;

import cl.duoc.pedidos360.orders.messaging.MessagingEventHistory;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
@RequestMapping("/api/orders/messaging")
public class MessagingEventsController {
    private final MessagingEventHistory history;
    public MessagingEventsController(MessagingEventHistory history) { this.history = history; }

    @GetMapping("/events")
    public ResponseEntity<?> events(@RequestParam(name="pedidoId", required=false) Long pedidoId,
            @RequestParam(name="after", defaultValue="0") long after,
            @RequestParam(name="limit", defaultValue="100") int limit) {
        if (after < 0 || limit < 1 || limit > 1000 || (pedidoId != null && pedidoId <= 0)) return invalid();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(history.page(pedidoId, after, limit));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> invalidType() { return invalid(); }

    private ResponseEntity<?> invalid() {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
                .body(Map.of("error", "INVALID_PARAMETERS"));
    }
}
