package cl.duoc.pedidos360.orders.controller;

import cl.duoc.pedidos360.orders.repository.PedidoProyeccionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PedidoProyeccionControllerTest {
    private EmbeddedDatabase db;
    private JdbcClient jdbc;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .addScript("db/manual/V001__proyecciones_kafka.sql")
                .build();
        jdbc = JdbcClient.create(db);
        mvc = MockMvcBuilders.standaloneSetup(
                new PedidoProyeccionController(new PedidoProyeccionRepository(jdbc))).build();
    }

    @AfterEach
    void cerrar() {
        db.shutdown();
    }

    @Test
    void analiticaVaciaIncluyeTodosLosEstadosEnCero() throws Exception {
        mvc.perform(get("/api/orders/analitica"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidosProcesados").value(0))
                .andExpect(jsonPath("$.totalUnidadesSolicitadas").value(0))
                .andExpect(jsonPath("$.pedidosPorEstado.length()").value(6))
                .andExpect(jsonPath("$.pedidosPorEstado.CREADO").value(0))
                .andExpect(jsonPath("$.pedidosPorEstado.EN_PREPARACION").value(0));
    }

    @Test
    void leeCadaProyeccionIndependientemente() throws Exception {
        jdbc.sql("""
                INSERT INTO pedido_kafka_analitica VALUES
                (12, 3, 5, 'ACEPTADO', 'ESTADO_CAMBIADO', 1, 8),
                (13, 4, 2, 'CREADO', 'CREADO', 2, 5)
                """).update();
        jdbc.sql("""
                INSERT INTO pedido_kafka_seguimiento VALUES (12, 3, 5, 'ACEPTADO', 'ESTADO_CAMBIADO', 2, 11)
                """).update();

        mvc.perform(get("/api/orders/analitica"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidosProcesados").value(2))
                .andExpect(jsonPath("$.totalUnidadesSolicitadas").value(7))
                .andExpect(jsonPath("$.pedidosPorEstado.ACEPTADO").value(1))
                .andExpect(jsonPath("$.pedidosPorEstado.CREADO").value(1))
                .andExpect(jsonPath("$.pedidosPorEstado.CANCELADO").value(0));
        mvc.perform(get("/api/orders/seguimiento/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.productoId").value(3))
                .andExpect(jsonPath("$.cantidad").value(5))
                .andExpect(jsonPath("$.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.tipoUltimoEvento").value("ESTADO_CAMBIADO"));
        mvc.perform(get("/api/orders/seguimiento/13")).andExpect(status().isNotFound());
    }
}
