package cl.duoc.pedidos360.orders.dto;

import cl.duoc.pedidos360.orders.entity.EstadoPedido;

public record PedidoSeguimientoResponse(
        long id,
        long productoId,
        int cantidad,
        EstadoPedido estado,
        PedidoEvento.Tipo tipoUltimoEvento) {}
