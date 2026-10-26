
package cl.duoc.rabbitmqadmin.controller;

import cl.duoc.rabbitmqadmin.service.RabbitmqAdminService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/rabbitmq")
public class RabbitmqAdminController {

    private final RabbitmqAdminService service;

    public RabbitmqAdminController(RabbitmqAdminService service) {
        this.service = service;
    }

    @PostMapping("/queues")
    public ResponseEntity<Map<String, String>> crearCola(
            @RequestBody Map<String, String> request) {

        String nombre = request.get("nombre");

        service.crearCola(nombre);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "mensaje", "Cola creada correctamente",
                        "nombre", nombre));
    }

    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, String>> crearExchange(
            @RequestBody Map<String, String> request) {

        String nombre = request.get("nombre");
        String tipo = request.get("tipo");

        service.crearExchange(nombre, tipo);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "mensaje", "Exchange creado correctamente",
                        "nombre", nombre,
                        "tipo", tipo));
    }

    @DeleteMapping("/exchanges/{nombre}")
    public ResponseEntity<Map<String, String>> eliminarExchange(
            @PathVariable String nombre) {

        service.eliminarExchange(nombre);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Exchange eliminado correctamente",
                "nombre", nombre));
    }

    @PostMapping("/bindings")
    public ResponseEntity<Map<String, String>> crearBinding(
            @RequestBody Map<String, String> request) {

        String exchange = request.get("exchange");
        String cola = request.get("cola");
        String routingKey = request.get("routingKey");

        service.crearBinding(exchange, cola, routingKey);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                        "mensaje", "Binding creado correctamente",
                        "exchange", exchange,
                        "cola", cola,
                        "routingKey", routingKey));
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Map<String, String>> eliminarBinding(
            @RequestBody Map<String, String> request) {

        String exchange = request.get("exchange");
        String cola = request.get("cola");
        String routingKey = request.get("routingKey");

        service.eliminarBinding(exchange, cola, routingKey);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Binding eliminado correctamente",
                "exchange", exchange,
                "cola", cola,
                "routingKey", routingKey));
    }

    @DeleteMapping("/queues/{nombre}")
    public ResponseEntity<Map<String, String>> eliminarCola(
            @PathVariable String nombre) {

        service.eliminarCola(nombre);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Cola eliminada correctamente",
                "nombre", nombre));
    }
}
