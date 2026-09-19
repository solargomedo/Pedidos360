package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.client.CatalogClient;
import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import cl.duoc.pedidos360.orders.entity.Pedido;
import cl.duoc.pedidos360.orders.repository.PedidoRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final CatalogClient catalogClient;

    public PedidoService(PedidoRepository pedidoRepository, CatalogClient catalogClient) {
        this.pedidoRepository = pedidoRepository;
        this.catalogClient = catalogClient;
    }

    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    public Pedido buscarPedidoPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Pedido no encontrado"));
    }

    public Pedido crearPedido(Pedido pedido) {
        pedido.setEstado(EstadoPedido.CREADO);
        return pedidoRepository.save(pedido);
    }

    public Pedido cambiarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = buscarPedidoPorId(id);
        EstadoPedido estadoActual = pedido.getEstado();

        boolean transicionPermitida = estadoActual != null && switch (estadoActual) {
            case CREADO -> nuevoEstado == EstadoPedido.ACEPTADO
                    || nuevoEstado == EstadoPedido.CANCELADO;
            case ACEPTADO -> nuevoEstado == EstadoPedido.EN_PREPARACION
                    || nuevoEstado == EstadoPedido.CANCELADO;
            case EN_PREPARACION -> nuevoEstado == EstadoPedido.DESPACHADO
                    || nuevoEstado == EstadoPedido.CANCELADO;
            case DESPACHADO -> nuevoEstado == EstadoPedido.ENTREGADO;
            case ENTREGADO, CANCELADO -> false;
        };

        if (!transicionPermitida) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Transición de estado no permitida");
        }

        if (estadoActual == EstadoPedido.CREADO && nuevoEstado == EstadoPedido.ACEPTADO) {
            catalogClient.descontarStock(pedido.getProductoId(), pedido.getCantidad());
        }

        pedido.setEstado(nuevoEstado);
        return pedidoRepository.save(pedido);
    }

    public void eliminarPedido(Long id) {
        if (!pedidoRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        pedidoRepository.deleteById(id);
    }
}
