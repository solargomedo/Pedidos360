package cl.duoc.pedidos360.orders.repository;

import cl.duoc.pedidos360.orders.dto.PedidoAnaliticaResponse;
import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import cl.duoc.pedidos360.orders.dto.PedidoSeguimientoResponse;
import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import java.util.EnumMap;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PedidoProyeccionRepository {
    private static final String UPSERT = """
            INSERT INTO %1$s (pedido_id, producto_id, cantidad, estado, tipo_evento, kafka_particion, kafka_offset)
            VALUES (:id, :producto, :cantidad, :estado, :tipo, :particion, :offset)
            ON CONFLICT (pedido_id) DO UPDATE SET
                producto_id = EXCLUDED.producto_id,
                cantidad = EXCLUDED.cantidad,
                estado = EXCLUDED.estado,
                tipo_evento = EXCLUDED.tipo_evento,
                kafka_offset = EXCLUDED.kafka_offset
            WHERE %1$s.kafka_particion = EXCLUDED.kafka_particion
              AND %1$s.kafka_offset < EXCLUDED.kafka_offset
            """;
    private static final String UPSERT_SEGUIMIENTO = UPSERT.formatted("pedido_kafka_seguimiento");
    private static final String UPSERT_ANALITICA = UPSERT.formatted("pedido_kafka_analitica");

    private record TotalesEstado(EstadoPedido estado, long pedidos, long unidades) {}

    private final JdbcClient jdbc;

    public PedidoProyeccionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void guardarSeguimiento(PedidoEvento evento, int particion, long offset) {
        guardar(UPSERT_SEGUIMIENTO, evento, particion, offset);
    }

    public void guardarAnalitica(PedidoEvento evento, int particion, long offset) {
        guardar(UPSERT_ANALITICA, evento, particion, offset);
    }

    // Cada upsert es una sola transacción JDBC confirmada antes de retornar al listener.
    private void guardar(String sql, PedidoEvento evento, int particion, long offset) {
        jdbc.sql(sql)
                .param("id", evento.id())
                .param("producto", evento.productoId())
                .param("cantidad", evento.cantidad())
                .param("estado", evento.estado().name())
                .param("tipo", evento.tipo().name())
                .param("particion", particion)
                .param("offset", offset)
                .update();
    }

    public Optional<PedidoSeguimientoResponse> buscarSeguimiento(long id) {
        return jdbc.sql("""
                SELECT pedido_id, producto_id, cantidad, estado, tipo_evento
                FROM pedido_kafka_seguimiento WHERE pedido_id = :id
                """)
                .param("id", id)
                .query((rs, row) -> new PedidoSeguimientoResponse(
                        rs.getLong("pedido_id"), rs.getLong("producto_id"), rs.getInt("cantidad"),
                        EstadoPedido.valueOf(rs.getString("estado")),
                        PedidoEvento.Tipo.valueOf(rs.getString("tipo_evento"))))
                .optional();
    }

    public PedidoAnaliticaResponse consultarAnalitica() {
        var porEstado = new EnumMap<EstadoPedido, Long>(EstadoPedido.class);
        for (var estado : EstadoPedido.values()) {
            porEstado.put(estado, 0L);
        }

        var totales = jdbc.sql("""
                SELECT estado, COUNT(*) AS pedidos, SUM(cantidad) AS unidades
                FROM pedido_kafka_analitica GROUP BY estado
                """)
                .query((rs, row) -> new TotalesEstado(EstadoPedido.valueOf(rs.getString("estado")),
                        rs.getLong("pedidos"), rs.getLong("unidades")))
                .list();

        long pedidos = 0;
        long unidades = 0;
        for (var total : totales) {
            porEstado.put(total.estado(), total.pedidos());
            pedidos += total.pedidos();
            unidades += total.unidades();
        }
        return new PedidoAnaliticaResponse(pedidos, unidades, porEstado);
    }
}
