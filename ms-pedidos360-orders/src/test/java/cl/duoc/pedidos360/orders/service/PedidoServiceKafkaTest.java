package cl.duoc.pedidos360.orders.service;

import cl.duoc.pedidos360.orders.client.CatalogClient;
import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import cl.duoc.pedidos360.orders.entity.Pedido;
import cl.duoc.pedidos360.orders.messaging.PedidoProducer;
import cl.duoc.pedidos360.orders.messaging.kafka.PedidoKafkaProducer;
import cl.duoc.pedidos360.orders.repository.PedidoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PedidoServiceKafkaTest {
    private final PedidoRepository repository = mock(PedidoRepository.class);
    private final CatalogClient catalog = mock(CatalogClient.class);
    private final PedidoProducer rabbit = mock(PedidoProducer.class);
    private final PedidoKafkaProducer kafka = mock(PedidoKafkaProducer.class);
    private final PedidoService service = new PedidoService(repository, catalog, rabbit, kafka);

    private Pedido pedido() {
        Pedido pedido = new Pedido();
        pedido.setId(1L);
        pedido.setProductoId(2L);
        pedido.setCantidad(3);
        return pedido;
    }

    @Test
    void creacionGuardaUnaVezYConservaOrdenDePublicacionRabbit() {
        Pedido pedido = pedido();
        when(repository.save(pedido)).thenReturn(pedido);
        assertSame(pedido, service.crearPedido(pedido));
        var order = inOrder(repository, rabbit, kafka);
        order.verify(repository).save(pedido);
        order.verify(rabbit).enviarPedidoCreado(1L);
        order.verify(kafka).publicar(PedidoEvento.de(pedido, PedidoEvento.Tipo.CREADO));
        verifyNoMoreInteractions(repository, rabbit, kafka);
        verifyNoInteractions(catalog);
    }

    @Test
    void actualizacionPublicaSnapshotGuardadoSinRabbitNiStock() {
        Pedido pedido = pedido();
        when(repository.findById(1L)).thenReturn(Optional.of(pedido));
        when(repository.save(pedido)).thenReturn(pedido);
        service.actualizarPedido(1L, pedido);
        verify(repository).save(pedido);
        verify(kafka).publicar(PedidoEvento.de(pedido, PedidoEvento.Tipo.ACTUALIZADO));
        verifyNoInteractions(rabbit, catalog);
    }

    @Test
    void aceptarDescuentaStockUnaVezYPublicaEstadoFinal() {
        Pedido pedido = pedido();
        when(repository.findById(1L)).thenReturn(Optional.of(pedido));
        when(repository.save(pedido)).thenReturn(pedido);
        service.cambiarEstado(1L, EstadoPedido.ACEPTADO);
        var order = inOrder(catalog, repository, kafka);
        order.verify(repository).findById(1L);
        order.verify(catalog).descontarStock(2L, 3);
        order.verify(repository).save(pedido);
        order.verify(kafka).publicar(PedidoEvento.de(pedido, PedidoEvento.Tipo.ESTADO_CAMBIADO));
        verifyNoInteractions(rabbit);
    }

    @Test
    void transicionInvalidaNoGuardaNiPublica() {
        when(repository.findById(1L)).thenReturn(Optional.of(pedido()));
        assertThrows(ResponseStatusException.class, () -> service.cambiarEstado(1L, EstadoPedido.ENTREGADO));
        verify(repository, never()).save(any());
        verifyNoInteractions(kafka, rabbit, catalog);
    }

    @Test
    void errorDePersistenciaNoPublicaMensajes() {
        when(repository.save(any())).thenThrow(new IllegalStateException("base no disponible"));
        assertThrows(IllegalStateException.class, () -> service.crearPedido(pedido()));
        verifyNoInteractions(kafka, rabbit, catalog);
    }
}
