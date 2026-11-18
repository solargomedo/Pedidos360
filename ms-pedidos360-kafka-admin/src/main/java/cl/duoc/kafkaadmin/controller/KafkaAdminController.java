package cl.duoc.kafkaadmin.controller;

import cl.duoc.kafkaadmin.dto.*;
import cl.duoc.kafkaadmin.service.KafkaAdminService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/kafka/topics")
public class KafkaAdminController {
    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> crear(@Valid @RequestBody CrearTopicoRequest request) {
        service.crear(request);
        return ResponseEntity.created(URI.create("/api/admin/kafka/topics/" + request.nombre())).build();
    }

    @GetMapping
    public List<String> listar() {
        return service.listar();
    }

    @GetMapping("/{nombre}")
    public TopicoResponse consultar(@PathVariable String nombre) {
        return service.consultar(nombre);
    }

    @GetMapping("/{nombre}/configs")
    public List<KafkaAdminService.Configuracion> configuraciones(@PathVariable String nombre) {
        return service.configuraciones(nombre);
    }

    @PatchMapping("/{nombre}/configs")
    public ResponseEntity<Void> modificar(@PathVariable String nombre,
            @Valid @RequestBody ModificarConfiguracionRequest request) {
        service.modificar(nombre, request.configuraciones());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        service.eliminar(nombre);
        return ResponseEntity.noContent().build();
    }
}
