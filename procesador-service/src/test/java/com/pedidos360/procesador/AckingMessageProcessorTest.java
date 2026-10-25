package com.pedidos360.procesador;

import com.pedidos360.procesador.config.MessagingProperties;
import com.pedidos360.procesador.messaging.AckingMessageProcessor;
import com.pedidos360.procesador.messaging.PedidoCreadoEvent;
import com.pedidos360.procesador.messaging.PermanentProcessingException;
import com.pedidos360.procesador.messaging.RecoverableProcessingException;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AckingMessageProcessorTest {

    private static final long TAG = 42L;
    private static final String JSON = """
            {"eventId":"e-1","occurredAt":"2026-01-01T00:00:00Z","pedidoId":7,
             "cliente":"Ana","descripcion":"cafe","total":1990.0}""";

    private Channel channel;
    private List<Long> sleeps;
    private AckingMessageProcessor processor;

    @BeforeEach
    void setUp() {
        channel = mock(Channel.class);
        sleeps = new ArrayList<>();
        var props = new MessagingProperties(null, null, null,
                new MessagingProperties.Retry(3, 1000, 2.0, 10_000));
        processor = new AckingMessageProcessor(new JacksonJsonMessageConverter(), props, sleeps::add);
    }

    private Message message(String body, String messageId) {
        MessageProperties mp = new MessageProperties();
        mp.setContentType("application/json");
        mp.setDeliveryTag(TAG);
        mp.setMessageId(messageId);
        return new Message(body.getBytes(StandardCharsets.UTF_8), mp);
    }

    @Test
    void exitoHaceAck() throws Exception {
        List<PedidoCreadoEvent> recibidos = new ArrayList<>();

        processor.process("TEST", message(JSON, "m1"), channel, PedidoCreadoEvent.class, recibidos::add);

        assertThat(recibidos).singleElement().satisfies(e -> {
            assertThat(e.pedidoId()).isEqualTo(7L);
            assertThat(e.cliente()).isEqualTo("Ana");
        });
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(TAG, false, false);
    }

    @Test
    void errorPermanenteVaDirectoALaDlqSinReintentar() throws Exception {
        AtomicInteger intentos = new AtomicInteger();

        processor.process("TEST", message(JSON, "m2"), channel, PedidoCreadoEvent.class, e -> {
            intentos.incrementAndGet();
            throw new PermanentProcessingException("dato invalido");
        });

        assertThat(intentos).hasValue(1);
        assertThat(sleeps).isEmpty();
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(TAG, false);
    }

    @Test
    void errorRecuperableReintentaConBackoffYLuegoVaALaDlq() throws Exception {
        AtomicInteger intentos = new AtomicInteger();

        processor.process("TEST", message(JSON, "m3"), channel, PedidoCreadoEvent.class, e -> {
            intentos.incrementAndGet();
            throw new RecoverableProcessingException("servicio caido");
        });

        assertThat(intentos).hasValue(3);
        assertThat(sleeps).containsExactly(1000L, 2000L);
        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(TAG, false);
    }

    @Test
    void fallaTransitoriaSeRecuperaYHaceAck() throws Exception {
        AtomicInteger intentos = new AtomicInteger();

        processor.process("TEST", message(JSON, "m4"), channel, PedidoCreadoEvent.class, e -> {
            if (intentos.incrementAndGet() < 3) {
                throw new RecoverableProcessingException("aun no");
            }
        });

        assertThat(intentos).hasValue(3);
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(TAG, false, false);
    }

    @Test
    void jsonInvalidoSeRechazaSinInvocarElHandler() throws Exception {
        AtomicInteger intentos = new AtomicInteger();

        processor.process("TEST", message("{esto no es json", "m5"), channel,
                PedidoCreadoEvent.class, e -> intentos.incrementAndGet());

        assertThat(intentos).hasValue(0);
        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void mensajeDuplicadoSeConfirmaSinReprocesar() throws Exception {
        AtomicInteger intentos = new AtomicInteger();

        processor.process("TEST", message(JSON, "dup"), channel, PedidoCreadoEvent.class, e -> intentos.incrementAndGet());
        processor.process("TEST", message(JSON, "dup"), channel, PedidoCreadoEvent.class, e -> intentos.incrementAndGet());

        assertThat(intentos).hasValue(1);
        verify(channel, org.mockito.Mockito.times(2)).basicAck(TAG, false);
    }
}
