package cl.duoc.pedidos360.orders.controller;

import cl.duoc.pedidos360.orders.dto.PedidoAnaliticaResponse;
import cl.duoc.pedidos360.orders.dto.PedidoSeguimientoResponse;
import cl.duoc.pedidos360.orders.repository.PedidoProyeccionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class PedidoProyeccionController {
    private final PedidoProyeccionRepository proyecciones;

    public PedidoProyeccionController(PedidoProyeccionRepository proyecciones) {
        this.proyecciones = proyecciones;
    }

    @GetMapping("/analitica")
    public PedidoAnaliticaResponse consultarAnalitica() {
        return proyecciones.consultarAnalitica();
    }

    @GetMapping("/seguimiento/{id}")
    public PedidoSeguimientoResponse buscarSeguimiento(@PathVariable long id) {
        return proyecciones.buscarSeguimiento(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido sin seguimiento Kafka"));
    }
}
