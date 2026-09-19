package cl.duoc.pedidos360.orders.controller;

import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import cl.duoc.pedidos360.orders.entity.Pedido;
import cl.duoc.pedidos360.orders.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoService.listarPedidos();
    }

    @GetMapping("/{id}")
    public Pedido buscarPedidoPorId(@PathVariable("id") Long id) {
        return pedidoService.buscarPedidoPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido crearPedido(@Valid @RequestBody Pedido pedido) {
        return pedidoService.crearPedido(pedido);
    }

    @PatchMapping("/{id}/estado")
    public Pedido cambiarEstado(@PathVariable("id") Long id,
            @RequestParam("estado") EstadoPedido estado) {
        return pedidoService.cambiarEstado(id, estado);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarPedido(@PathVariable("id") Long id) {
        pedidoService.eliminarPedido(id);
    }
}
