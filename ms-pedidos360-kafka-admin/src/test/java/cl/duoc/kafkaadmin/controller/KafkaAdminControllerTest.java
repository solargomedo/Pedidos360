package cl.duoc.kafkaadmin.controller;

import cl.duoc.kafkaadmin.dto.CrearTopicoRequest;
import cl.duoc.kafkaadmin.dto.TopicoResponse;
import cl.duoc.kafkaadmin.exception.KafkaAdminExceptionHandler;
import cl.duoc.kafkaadmin.service.KafkaAdminService;
import java.util.List;
import java.util.Map;
import org.apache.kafka.common.errors.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KafkaAdminControllerTest {
    private static final String BASE = "/api/admin/kafka/topics";
    private static final String TOPIC = "prueba.ep5";
    private final KafkaAdminService service = mock(KafkaAdminService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new KafkaAdminController(service))
            .setControllerAdvice(new KafkaAdminExceptionHandler()).build();

    @Test
    void creaCon201YLocation() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                {"nombre":"prueba.ep5","particiones":3,"factorReplicacion":3,
                 "configuraciones":{"cleanup.policy":"delete","retention.ms":"604800000"}}
                """))
                .andExpect(status().isCreated()).andExpect(header().string("Location", BASE + "/" + TOPIC));
        verify(service).crear(new CrearTopicoRequest(TOPIC, 3, 3,
                Map.of("cleanup.policy", "delete", "retention.ms", "604800000")));
    }

    @Test
    void listaYConsultaMetadatosYConfiguraciones() throws Exception {
        when(service.listar()).thenReturn(List.of(TOPIC));
        when(service.consultar(TOPIC)).thenReturn(new TopicoResponse(TOPIC,
                List.of(new TopicoResponse.Particion(0, 1, List.of(1, 2, 3), List.of(1, 2, 3)))));
        when(service.configuraciones(TOPIC)).thenReturn(List.of(new KafkaAdminService.Configuracion(
                "cleanup.policy", "delete", "DYNAMIC_TOPIC_CONFIG", false, false)));
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(TOPIC));
        mvc.perform(get(BASE + "/" + TOPIC)).andExpect(status().isOk())
                .andExpect(jsonPath("$.particiones[0].replicas.length()").value(3));
        mvc.perform(get(BASE + "/" + TOPIC + "/configs")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valor").value("delete"));
    }

    @Test
    void modificaConfiguracionesCon200() throws Exception {
        mvc.perform(patch(BASE + "/" + TOPIC + "/configs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"configuraciones\":{\"retention.ms\":\"3600000\"}}"))
                .andExpect(status().isOk());
        verify(service).modificar(TOPIC, Map.of("retention.ms", "3600000"));
    }

    @Test
    void eliminaPruebaEp5Con204SinConectarAKafka() throws Exception {
        mvc.perform(delete(BASE + "/" + TOPIC)).andExpect(status().isNoContent());
        verify(service).eliminar(TOPIC);
    }

    @Test
    void rechazaSolicitudesInvalidasSinInvocarServicio() throws Exception {
        for (String json : List.of("{}",
                "{\"nombre\":\"prueba.ep5\",\"particiones\":0,\"factorReplicacion\":3}",
                "{\"nombre\":\"prueba.ep5\",\"particiones\":3,\"factorReplicacion\":0}",
                "{\"nombre\":\"prueba/ep5\",\"particiones\":3,\"factorReplicacion\":3}",
                "{\"nombre\":\"prueba.ep5\",\"particiones\":3,\"factorReplicacion\":3,\"configuraciones\":{\"\":\"delete\"}}",
                "{json invalido")) {
            mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        for (String json : List.of("{}", "{\"configuraciones\":{}}",
                "{\"configuraciones\":{\"retention.ms\":null}}")) {
            mvc.perform(patch(BASE + "/" + TOPIC + "/configs").contentType(MediaType.APPLICATION_JSON)
                    .content(json)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void topicoDuplicadoDevuelve409() throws Exception {
        doThrow(new TopicExistsException("duplicado")).when(service).crear(any());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"prueba.ep5\",\"particiones\":3,\"factorReplicacion\":3}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void erroresKafkaTienenRespuestaUniformeSinExponerDetalleInterno() throws Exception {
        Exception[] errors = { new UnknownTopicOrPartitionException("interno"),
                new InvalidConfigurationException("interno"), new TimeoutException("interno"),
                new NetworkException("interno"), new IllegalStateException("interno") };
        int[] statuses = {404, 400, 504, 503, 500};
        for (int i = 0; i < errors.length; i++) {
            doThrow(errors[i]).when(service).consultar(TOPIC);
            mvc.perform(get(BASE + "/" + TOPIC)).andExpect(status().is(statuses[i]))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(statuses[i]));
        }
    }
}
