package cl.duoc.pedidos360.orders.messaging;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.rabbitmq.client.Channel;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static cl.duoc.pedidos360.orders.messaging.MessagingEvent.Type.*;

class MessagingEventsTest {
    interface Receiver { void receive(String text, Message message, Channel channel) throws IOException; }
    List<Receiver> receivers(MessagingEventHistory h) {
        return List.of(new PedidoConsumer(h)::recibirPedidoCreado,
                new ComprobanteConsumer(h)::recibirSolicitud, new AuditoriaConsumer(h)::recibirEvento);
    }
    Message message() {
        var p = new MessageProperties(); p.setDeliveryTag(19); p.setReceivedExchange("pedidos.direct");
        p.setReceivedRoutingKey("pedido.creado"); return new Message(new byte[0], p);
    }
    @Test void successfulConsumersKeepOneAttemptAndOneAck() throws Exception {
        for (int i=0;i<3;i++) {
            var h=new MessagingEventHistory(); var channel=mock(Channel.class);
            receivers(h).get(i).receive("Se ha creado el pedido con ID: 42",message(),channel);
            verify(channel).basicAck(19,false); verifyNoMoreInteractions(channel);
            var events=h.snapshot(42L,0,100).events();
            assertEquals(List.of(RECEIVED,PROCESSING_ATTEMPT,PROCESSING_SUCCEEDED,ACK_SENT),events.stream().map(MessagingEvent::type).toList());
            assertEquals(1,events.get(3).attempt());
        }
    }
    @Test void failuresKeepThreeTotalAttemptsAndOneNackWithoutRequeue() throws Exception {
        for (int i=0;i<3;i++) {
            var h=new MessagingEventHistory(); var channel=mock(Channel.class);
            receivers(h).get(i).receive("PRUEBA_ERROR",message(),channel);
            verify(channel).basicNack(19,false,false); verifyNoMoreInteractions(channel);
            var events=h.snapshot(null,0,100).events();
            assertEquals(List.of(1,2,3),events.stream().filter(e->e.type()==PROCESSING_ATTEMPT).map(MessagingEvent::attempt).toList());
            assertEquals(3,events.stream().filter(e->e.type()==PROCESSING_ERROR).count());
            assertFalse(events.stream().anyMatch(e->e.type()==ACK_SENT || e.type()==PROCESSING_SUCCEEDED));
            assertTrue(events.stream().allMatch(e->e.pedidoId()==null));
            assertEquals(NACK_SENT,events.get(events.size()-1).type());
        }
    }
    @Test void failedAckIsNotRecordedAsSentAndExistingNackBranchRemains() throws Exception {
        var h=new MessagingEventHistory();var channel=mock(Channel.class);
        doThrow(new IOException("private detail")).when(channel).basicAck(19,false);
        new PedidoConsumer(h).recibirPedidoCreado("ok",message(),channel);
        verify(channel).basicAck(19,false);verify(channel).basicNack(19,false,false);
        assertFalse(h.snapshot(null,0,100).events().stream().anyMatch(e->e.type()==ACK_SENT));
    }
    @Test void failedNackIsNotRecordedAsSent() throws Exception {
        var h=new MessagingEventHistory();var channel=mock(Channel.class);
        doThrow(new IOException()).when(channel).basicNack(19,false,false);
        assertThrows(IOException.class,()->new PedidoConsumer(h).recibirPedidoCreado("PRUEBA_ERROR",message(),channel));
        assertFalse(h.snapshot(null,0,100).events().stream().anyMatch(e->e.type()==NACK_SENT));
    }
    @Test void producerRecordsOnlyRequestsAndKeepsThreePublications() {
        var h=new MessagingEventHistory();var rabbit=mock(RabbitTemplate.class);
        new PedidoProducer(rabbit,h).enviarPedidoCreado(42L);
        verify(rabbit).convertAndSend("pedidos.direct","pedido.creado","Se ha creado el pedido con ID: 42");
        verify(rabbit).convertAndSend("pedidos.topic","pedido.creado","Se ha creado el pedido con ID: 42");
        verify(rabbit).convertAndSend("pedidos.direct","pedido.comprobante","Se ha creado el pedido con ID: 42");
        verifyNoMoreInteractions(rabbit);
        assertEquals(3,h.snapshot(42L,0,100).events().size());
        assertTrue(h.snapshot(null,0,100).events().stream().allMatch(e->e.type()==PUBLICATION_REQUESTED && e.queue()==null));
    }
    @Test void publicationFailureRemainsPropagatedAndLaterRequestsAreNotInvented() {
        var h=new MessagingEventHistory();var rabbit=mock(RabbitTemplate.class);
        doThrow(new IllegalStateException()).when(rabbit).convertAndSend(anyString(),anyString(),anyString());
        assertThrows(IllegalStateException.class,()->new PedidoProducer(rabbit,h).enviarPedidoCreado(42L));
        assertEquals(1,h.snapshot(null,0,100).events().size());
    }
    @Test void boundedImmutableHistoryFiltersAndRejectsUnsafeLimits() {
        var h=new MessagingEventHistory();var context=new MessagingEventHistory.Context(42L,null,null,null,null);
        for(int i=0;i<1010;i++) h.record(context,null,PUBLICATION_REQUESTED,MessagingEvent.Result.REQUESTED);
        var snapshot=h.snapshot(42L,0,1000);
        assertEquals(1000,snapshot.events().size());assertEquals(11,snapshot.events().get(0).sequence());
        assertEquals(1010,snapshot.lastSequence());
        assertEquals(5,h.snapshot(42L,1005,100).events().size());
        assertTrue(h.snapshot(99L,0,100).events().isEmpty());
        assertThrows(UnsupportedOperationException.class,()->snapshot.events().clear());
        assertThrows(IllegalArgumentException.class,()->h.snapshot(null,0,1001));
        assertEquals(1000,snapshot.events().stream().map(MessagingEvent::eventId).distinct().count());
    }
    @Test void dlqListenersRemainDisabledByDefaultAndOnlyRecordActualInvocations() throws Exception {
        var h=new MessagingEventHistory();var dlq=new DeadLetterConsumer(h);
        for(var method:DeadLetterConsumer.class.getDeclaredMethods()) {
            var listener=method.getAnnotation(org.springframework.amqp.rabbit.annotation.RabbitListener.class);
            if(listener!=null) assertEquals("${pedidos360.dlq.consumer.enabled:false}",listener.autoStartup());
        }
        assertTrue(h.snapshot(null,0,100).events().isEmpty());
        var channel=mock(Channel.class);dlq.recibirNotificacionFallida("ok",message(),channel);
        verify(channel).basicAck(19,false);verifyNoMoreInteractions(channel);
        assertEquals(List.of(RECEIVED,ACK_SENT),h.snapshot(null,0,100).events().stream().map(MessagingEvent::type).toList());
    }
}
