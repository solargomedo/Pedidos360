package cl.duoc.pedidos360.orders;

import cl.duoc.pedidos360.orders.controller.*;
import cl.duoc.pedidos360.orders.config.MessagingSecurityConfig;
import cl.duoc.pedidos360.orders.messaging.*;
import cl.duoc.pedidos360.orders.service.PedidoService;
import cl.duoc.pedidos360.orders.entity.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers={MessagingEventsController.class,PedidoController.class})
@Import({MessagingSecurityConfig.class,MessagingEventHistory.class})
class MessagingSecurityTest {
    static final String PATH="/api/orders/messaging/events";
    @Autowired MockMvc mvc;
    @Autowired MessagingEventHistory history;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PedidoService pedidos;
    void token(String role) {
        when(decoder.decode("marker")).thenReturn(Jwt.withTokenValue("marker").header("alg","RS256")
            .subject("test").issuedAt(Instant.now().minusSeconds(60)).expiresAt(Instant.now().plusSeconds(300))
            .claim("roles",List.of(role)).build());
    }
    @Test void noTokenIs401() throws Exception { mvc.perform(get(PATH)).andExpect(status().isUnauthorized()); }
    @Test void invalidTokenIs401() throws Exception {
        when(decoder.decode("bad")).thenThrow(new BadJwtException("invalid"));
        mvc.perform(get(PATH).header("Authorization","Bearer bad")).andExpect(status().isUnauthorized());
    }
    @Test void nonAdminIs403() throws Exception {
        token("Operador");mvc.perform(get(PATH).header("Authorization","Bearer marker")).andExpect(status().isForbidden());
    }
    @Test void adminReadsRecordedEventsAndPagination() throws Exception {
        token("Admin");long before=history.snapshot(null,0,1000).lastSequence();
        var c=new MessagingEventHistory.Context(987L,"pedidos.direct","pedido.creado",null,null);
        history.record(c,null,MessagingEvent.Type.PUBLICATION_REQUESTED,MessagingEvent.Result.REQUESTED);
        history.record(c,null,MessagingEvent.Type.PUBLICATION_REQUESTED,MessagingEvent.Result.REQUESTED);
        mvc.perform(get(PATH).param("after",String.valueOf(before)).param("pedidoId","987").param("limit","1")
            .header("Authorization","Bearer marker")).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.instanceId").isNotEmpty()).andExpect(jsonPath("$.events.length()").value(1))
            .andExpect(jsonPath("$.events[0].pedidoId").value(987))
            .andExpect(jsonPath("$.nextAfter").value(before+1));
        mvc.perform(get(PATH).param("after",String.valueOf(before+1)).param("pedidoId","987")
            .header("Authorization","Bearer marker")).andExpect(jsonPath("$.events.length()").value(1));
        mvc.perform(get(PATH).param("pedidoId","999999").header("Authorization","Bearer marker"))
            .andExpect(jsonPath("$.events.length()").value(0));
    }
    @Test void invalidParametersAreSanitized() throws Exception {
        token("Admin");
        for(String query:List.of("after=-1","limit=0","limit=1001","pedidoId=0","after=secret","limit=999999999999999999999")) {
            mvc.perform(get(PATH+"?"+query).header("Authorization","Bearer marker"))
                .andExpect(status().isBadRequest()).andExpect(content().json("{\"error\":\"INVALID_PARAMETERS\"}"));
        }
    }
    @Test void otherHistoryMethodsAndRoutesAreDenied() throws Exception {
        token("Admin");
        for(String method:List.of("POST","PUT","PATCH","DELETE","OPTIONS","HEAD")) {
            mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),PATH).header("Authorization","Bearer marker"))
                .andExpect(status().isForbidden());
        }
        for(String path:List.of("/api/orders/messaging","/api/orders/messaging/unknown","/api/orders/messaging/events/extra")) {
            mvc.perform(get(path).header("Authorization","Bearer marker")).andExpect(status().isForbidden());
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
    @Test void existingOrderOperationsStillReachControllerWithoutJwtOrCsrf() throws Exception {
        var p=new Pedido();p.setId(1L);p.setProductoId(2L);p.setCantidad(1);
        when(pedidos.listarPedidos()).thenReturn(List.of(p));when(pedidos.buscarPedidoPorId(1L)).thenReturn(p);
        when(pedidos.crearPedido(any())).thenReturn(p);when(pedidos.actualizarPedido(eq(1L),any())).thenReturn(p);
        when(pedidos.cambiarEstado(1L,EstadoPedido.ACEPTADO)).thenReturn(p);
        mvc.perform(get("/api/orders")).andExpect(status().isOk());
        mvc.perform(get("/api/orders/1")).andExpect(status().isOk());
        mvc.perform(post("/api/orders").contentType("application/json").content("{\"productoId\":2,\"cantidad\":1}"))
            .andExpect(status().isCreated());
        mvc.perform(put("/api/orders/1").contentType("application/json").content("{\"productoId\":2,\"cantidad\":1}"))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/orders/1/estado").param("estado","ACEPTADO")).andExpect(status().isOk());
        mvc.perform(delete("/api/orders/1")).andExpect(status().isNoContent());
        verify(pedidos).crearPedido(any());verify(pedidos).actualizarPedido(eq(1L),any());verify(pedidos).eliminarPedido(1L);
        verifyNoInteractions(decoder);
    }
}
