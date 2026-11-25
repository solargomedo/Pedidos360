package cl.duoc.pedidos360.bff.controller;

import cl.duoc.pedidos360.bff.client.OrdersClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class BffOrderProjectionsTest {
    private static final String ANALYTICS = "/api/bff/orders/analitica";
    private static final String TRACKING = "/api/bff/orders/seguimiento/16";

    @Autowired
    WebApplicationContext context;

    @MockitoBean
    JwtDecoder decoder;

    @MockitoBean
    OrdersClient ordersClient;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Operador"})
    void allowsAnalyticsAndTrackingForOrderReadRoles(String role) throws Exception {
        jwtWithRole(role);
        when(ordersClient.getOrderAnalytics()).thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"totalPedidosProcesados":6,"totalUnidadesSolicitadas":6,"pedidosPorEstado":{"CREADO":6}}
                        """));
        when(ordersClient.getOrderTracking("16")).thenReturn(ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"id":16,"estado":"CREADO"}
                        """));

        mvc.perform(get(ANALYTICS).header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPedidosProcesados").value(6));
        mvc.perform(get(TRACKING).header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(16));
        verify(ordersClient).getOrderAnalytics();
        verify(ordersClient).getOrderTracking("16");
        verifyNoMoreInteractions(ordersClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {ANALYTICS, TRACKING})
    void requiresValidBearerToken(String path) throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("invalid"));
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(ordersClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {ANALYTICS, TRACKING})
    void deniesCliente(String path) throws Exception {
        jwtWithRole("Cliente");
        mvc.perform(get(path).header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ordersClient);
    }

    @Test
    void preservesTrackingNotFoundAndOrderById() throws Exception {
        jwtWithRole("Operador");
        when(ordersClient.getOrderTracking("16")).thenReturn(ResponseEntity.notFound().build());
        when(ordersClient.getOrder("16")).thenReturn(ResponseEntity.ok("pedido"));

        mvc.perform(get(TRACKING).header("Authorization", "Bearer token")).andExpect(status().isNotFound());
        mvc.perform(get("/api/bff/orders/16").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(content().string("pedido"));
    }

    private void jwtWithRole(String role) {
        when(decoder.decode("token")).thenReturn(Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "usuario")
                .claim("roles", List.of(role))
                .build());
    }
}
