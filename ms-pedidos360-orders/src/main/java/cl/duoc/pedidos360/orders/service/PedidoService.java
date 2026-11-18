package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.client.CatalogClient;
import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import cl.duoc.pedidos360.orders.entity.Pedido;
import cl.duoc.pedidos360.orders.repository.PedidoRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import cl.duoc.pedidos360.orders.messaging.PedidoProducer;
import cl.duoc.pedidos360.orders.messaging.kafka.PedidoKafkaProducer;
import cl.duoc.pedidos360.orders.dto.PedidoEvento;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final CatalogClient catalogClient;
    private final PedidoProducer pedidoProducer;
    private final PedidoKafkaProducer kafkaProducer;

    public PedidoService(
            PedidoRepository pedidoRepository,
            CatalogClient catalogClient,
            PedidoProducer pedidoProducer,
            PedidoKafkaProducer kafkaProducer) {

        this.pedidoRepository = pedidoRepository;
        this.catalogClient = catalogClient;
        this.pedidoProducer = pedidoProducer;
        this.kafkaProducer = kafkaProducer;
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

        // Primero guardamos el pedido en PostgreSQL
        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        // Después enviamos una notificación a RabbitMQ
        pedidoProducer.enviarPedidoCreado(pedidoGuardado.getId());
        kafkaProducer.publicar(PedidoEvento.de(pedidoGuardado, PedidoEvento.Tipo.CREADO));

        return pedidoGuardado;
    }

    public Pedido actualizarPedido(Long id, Pedido datos) {
        Pedido pedido = buscarPedidoPorId(id);

        if (pedido.getEstado() != EstadoPedido.CREADO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden editar pedidos en estado CREADO");
        }

        if (datos.getProductoId() == null || datos.getProductoId() <= 0
                || datos.getCantidad() == null || datos.getCantidad() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "productoId y cantidad deben ser obligatorios y mayores que 0");
        }

        pedido.setProductoId(datos.getProductoId());
        pedido.setCantidad(datos.getCantidad());
        Pedido guardado = pedidoRepository.save(pedido);
        kafkaProducer.publicar(PedidoEvento.de(guardado, PedidoEvento.Tipo.ACTUALIZADO));
        return guardado;
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
        Pedido guardado = pedidoRepository.save(pedido);
        kafkaProducer.publicar(PedidoEvento.de(guardado, PedidoEvento.Tipo.ESTADO_CAMBIADO));
        return guardado;
    }

    public void eliminarPedido(Long id) {
        if (!pedidoRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado");
        }
        pedidoRepository.deleteById(id);
    }
}
