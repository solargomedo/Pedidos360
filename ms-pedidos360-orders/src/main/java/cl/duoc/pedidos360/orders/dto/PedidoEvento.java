package cl.duoc.pedidos360.orders.dto;

import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import cl.duoc.pedidos360.orders.entity.Pedido;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PedidoEvento(
        @NotNull Tipo tipo,
        @NotNull @Positive Long id,
        @NotNull @Positive Long productoId,
        @NotNull @Positive Integer cantidad,
        @NotNull EstadoPedido estado) {

    public enum Tipo { CREADO, ACTUALIZADO, ESTADO_CAMBIADO }

    public static PedidoEvento de(Pedido pedido, Tipo tipo) {
        return new PedidoEvento(tipo, pedido.getId(), pedido.getProductoId(),
                pedido.getCantidad(), pedido.getEstado());
    }
}
