package cl.duoc.pedidos360.orders.dto;

import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import java.util.Map;

public record PedidoAnaliticaResponse(
        long totalPedidosProcesados,
        long totalUnidadesSolicitadas,
        Map<EstadoPedido, Long> pedidosPorEstado) {}
