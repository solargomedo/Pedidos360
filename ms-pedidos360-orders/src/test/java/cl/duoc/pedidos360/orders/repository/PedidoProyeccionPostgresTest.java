package cl.duoc.pedidos360.orders.repository;

import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "ORDERS_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.+")
class PedidoProyeccionPostgresTest {

    @Test
    void guardaSoloElUltimoOffsetDeLaParticionOriginalEnAmbasProyecciones() throws Exception {
        try (var datasource = new SingleConnectionDataSource(System.getenv("ORDERS_TEST_POSTGRES_URL"),
                System.getenv("ORDERS_TEST_POSTGRES_USER"), System.getenv("ORDERS_TEST_POSTGRES_PASSWORD"), true);
             var conexion = datasource.getConnection()) {
            conexion.setAutoCommit(false);
            try {
                String esquema = "orders_test_" + UUID.randomUUID().toString().replace("-", "");
                var jdbc = JdbcClient.create(datasource);
                jdbc.sql("CREATE SCHEMA " + esquema).update();
                jdbc.sql("SET LOCAL search_path TO " + esquema).update();
                ScriptUtils.executeSqlScript(conexion,
                        new ClassPathResource("db/manual/V001__proyecciones_kafka.sql"));
                var repo = new PedidoProyeccionRepository(jdbc);
                var creado = new PedidoEvento(PedidoEvento.Tipo.CREADO, 12L, 3L, 2, EstadoPedido.CREADO);
                var actualizado = new PedidoEvento(PedidoEvento.Tipo.ACTUALIZADO, 12L, 3L, 5, EstadoPedido.CREADO);
                var aceptado = new PedidoEvento(PedidoEvento.Tipo.ESTADO_CAMBIADO, 12L, 3L, 5, EstadoPedido.ACEPTADO);

                repo.guardarAnalitica(creado, 0, 4);
                repo.guardarAnalitica(actualizado, 0, 5);
                repo.guardarAnalitica(aceptado, 0, 6);
                repo.guardarAnalitica(creado, 0, 4);
                repo.guardarAnalitica(actualizado, 0, 6);
                repo.guardarAnalitica(creado, 1, 30);

                repo.guardarSeguimiento(creado, 2, 8);
                repo.guardarSeguimiento(aceptado, 2, 9);
                repo.guardarSeguimiento(creado, 2, 8);

                var analitica = repo.consultarAnalitica();
                assertEquals(1, analitica.totalPedidosProcesados());
                assertEquals(5, analitica.totalUnidadesSolicitadas());
                assertEquals(1L, analitica.pedidosPorEstado().get(EstadoPedido.ACEPTADO));
                assertEquals(0L, analitica.pedidosPorEstado().get(EstadoPedido.CREADO));
                var seguimiento = repo.buscarSeguimiento(12).orElseThrow();
                assertEquals(5, seguimiento.cantidad());
                assertEquals(EstadoPedido.ACEPTADO, seguimiento.estado());
                assertEquals(PedidoEvento.Tipo.ESTADO_CAMBIADO, seguimiento.tipoUltimoEvento());
            } finally {
                conexion.rollback();
            }
        }
    }
}
