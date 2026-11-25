package cl.duoc.pedidos360.orders.messaging.kafka;

import cl.duoc.pedidos360.orders.config.KafkaConsumerConfig;
import cl.duoc.pedidos360.orders.dto.PedidoEvento;
import cl.duoc.pedidos360.orders.entity.EstadoPedido;
import cl.duoc.pedidos360.orders.repository.PedidoProyeccionRepository;
import cl.duoc.pedidos360.orders.service.PedidoKafkaProcessor;
import cl.duoc.pedidos360.orders.service.PedidoKafkaProcessor.EventoInvalidoException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.ListenerExecutionFailedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PedidoKafkaTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = FACTORY.getValidator();
    private final PedidoEvento evento = new PedidoEvento(PedidoEvento.Tipo.CREADO,
            12L, 3L, 2, EstadoPedido.CREADO);

    @AfterAll
    static void cerrarValidator() {
        FACTORY.close();
    }

    @Test
    void serializaYDeserializaDtoSinDependerDeHeadersJava() {
        try (var serializer = new JacksonJsonSerializer<PedidoEvento>();
             var deserializer = new JacksonJsonDeserializer<PedidoEvento>()) {
            serializer.configure(Map.of("spring.json.add.type.headers", false), false);
            deserializer.configure(Map.of(
                    "spring.json.value.default.type", PedidoEvento.class.getName(),
                    "spring.json.use.type.headers", false,
                    "spring.json.trusted.packages", "cl.duoc.pedidos360.orders.dto"), false);
            var headers = new RecordHeaders();
            byte[] json = serializer.serialize("pedidos.eventos", headers, evento);
            assertFalse(headers.iterator().hasNext());
            assertEquals(evento, deserializer.deserialize("pedidos.eventos", headers, json));
        }
    }

    @Test
    void jsonInvalidoSeCapturaConErrorHandlingDeserializer() {
        try (var deserializer = new ErrorHandlingDeserializer<PedidoEvento>(
                new JacksonJsonDeserializer<>(PedidoEvento.class))) {
            var headers = new RecordHeaders();
            assertNull(deserializer.deserialize("pedidos.eventos", headers, new byte[] {123}));
            assertNotNull(headers.lastHeader("springDeserializerExceptionValue"));
        }
    }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, PedidoEvento> template() {
        return mock(KafkaTemplate.class);
    }

    private PedidoKafkaProducer producer(KafkaTemplate<String, PedidoEvento> template) {
        return new PedidoKafkaProducer(template, validator, "pedidos.eventos", "pedidos.analitica");
    }

    @Test
    void publicaMismoDtoEnAmbosTopicosConClaveDelPedido() {
        var template = template();
        for (String topic : new String[] {"pedidos.eventos", "pedidos.analitica"}) {
            var metadata = new RecordMetadata(new TopicPartition(topic, 1), 0, 0, 0, 0, 0);
            when(template.send(topic, "12", evento)).thenReturn(CompletableFuture.completedFuture(
                    new SendResult<>(new ProducerRecord<>(topic, "12", evento), metadata)));
        }
        producer(template).publicar(evento);
        verify(template).send("pedidos.eventos", "12", evento);
        verify(template).send("pedidos.analitica", "12", evento);
        verifyNoMoreInteractions(template);
    }

    @Test
    void falloSincronoEnUnTopicoNoImpideIntentarElOtroNiEscapaAlNegocio() {
        var template = template();
        when(template.send("pedidos.eventos", "12", evento)).thenThrow(new IllegalStateException("sin broker"));
        when(template.send("pedidos.analitica", "12", evento))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("fallo asincrono")));
        assertDoesNotThrow(() -> producer(template).publicar(evento));
        verify(template).send("pedidos.analitica", "12", evento);
    }

    @Test
    void dtoInvalidoNoSePublicaYElProcesamientoLoRechaza() {
        var invalid = new PedidoEvento(null, null, 0L, -1, null);
        var template = template();
        producer(template).publicar(invalid);
        verifyNoInteractions(template);
        var proyecciones = mock(PedidoProyeccionRepository.class);
        var processor = new PedidoKafkaProcessor(validator, proyecciones);
        assertThrows(ConstraintViolationException.class, () -> processor.procesarEvento(invalid));
        assertThrows(ConstraintViolationException.class, () -> processor.procesarAnalitica(
                new ConsumerRecord<>("pedidos.analitica", 0, 1, "12", invalid)));
        assertThrows(EventoInvalidoException.class, () -> processor.procesarSeguimiento(
                new ConsumerRecord<>("pedidos.eventos", 0, 1, "13", evento)));
        assertThrows(EventoInvalidoException.class, () -> processor.procesarSeguimiento(
                new ConsumerRecord<>("pedidos.eventos", 0, 2, "12", null)));
        assertDoesNotThrow(() -> processor.procesarEvento(evento));
        verifyNoInteractions(proyecciones);
    }

    @Test
    void persisteLosTresTiposConLaParticionYElOffsetDeCadaTopico() {
        var proyecciones = mock(PedidoProyeccionRepository.class);
        var processor = new PedidoKafkaProcessor(validator, proyecciones);
        var actualizado = new PedidoEvento(PedidoEvento.Tipo.ACTUALIZADO, 12L, 3L, 5, EstadoPedido.CREADO);
        var aceptado = new PedidoEvento(PedidoEvento.Tipo.ESTADO_CAMBIADO, 12L, 3L, 5, EstadoPedido.ACEPTADO);
        long offset = 5;
        for (var snapshot : new PedidoEvento[] {evento, actualizado, aceptado}) {
            processor.procesarAnalitica(new ConsumerRecord<>("pedidos.analitica", 1, offset, "12", snapshot));
            processor.procesarSeguimiento(new ConsumerRecord<>("pedidos.eventos", 2, offset + 10, "12", snapshot));
            verify(proyecciones).guardarAnalitica(snapshot, 1, offset);
            verify(proyecciones).guardarSeguimiento(snapshot, 2, offset + 10);
            offset++;
        }
    }

    @Test
    void listenersDeleganYPropaganFallosParaQueElContenedorReintente() {
        var processor = mock(PedidoKafkaProcessor.class);
        var consumer = new PedidoKafkaConsumer(processor);
        consumer.recibirEvento(evento);
        var analitica = new ConsumerRecord<>("pedidos.analitica", 1, 5, "12", evento);
        var seguimiento = new ConsumerRecord<>("pedidos.eventos", 1, 5, "12", evento);
        consumer.recibirAnalitica(analitica);
        consumer.recibirSeguimiento(seguimiento);
        verify(processor).procesarEvento(evento);
        verify(processor).procesarAnalitica(analitica);
        verify(processor).procesarSeguimiento(seguimiento);
        doThrow(new DataAccessResourceFailureException("sin BD")).when(processor).procesarSeguimiento(seguimiento);
        assertThrows(DataAccessResourceFailureException.class, () -> consumer.recibirSeguimiento(seguimiento));
    }

    @Test
    void errorHandlerReintentaDosVecesYRecuperaSinTopicoAdicional() {
        var handler = new KafkaConsumerConfig().kafkaErrorHandler(0, 2);
        var record = new ConsumerRecord<String, PedidoEvento>("pedidos.eventos", 0, 5, "12", evento);
        var container = mock(MessageListenerContainer.class);
        when(container.getContainerProperties()).thenReturn(new ContainerProperties("pedidos.eventos"));
        var error = new IllegalStateException("temporal");
        assertFalse(handler.handleOne(error, record, null, container));
        assertFalse(handler.handleOne(error, record, null, container));
        assertTrue(handler.handleOne(error, record, null, container));
    }

    @Test
    void descartaEventoInvalidoPeroNoRecuperaOffsetPorFalloDePersistencia() {
        var handler = new KafkaConsumerConfig().kafkaErrorHandler(0, 2);
        var record = new ConsumerRecord<>("pedidos.analitica", 0, 5, "12", evento);
        var container = mock(MessageListenerContainer.class);
        when(container.getContainerProperties()).thenReturn(new ContainerProperties("pedidos.analitica"));
        var invalido = new ListenerExecutionFailedException("listener", new EventoInvalidoException("clave"));
        assertTrue(handler.handleOne(invalido, record, null, container));

        var fallo = new ListenerExecutionFailedException("listener", new DataAccessResourceFailureException("sin BD"));
        for (int intento = 0; intento < 5; intento++) {
            assertFalse(handler.handleOne(fallo, record, null, container));
        }
    }
}
