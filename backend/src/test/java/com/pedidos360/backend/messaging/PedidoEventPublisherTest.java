package com.pedidos360.backend.messaging;

import com.pedidos360.backend.model.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PedidoEventPublisherTest {

    private RabbitTemplate rabbitTemplate;
    private PedidoEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        var props = new MessagingProperties(
                new MessagingProperties.Exchanges("pedidos.events.exchange", "pedidos.tasks.exchange"),
                new MessagingProperties.RoutingKeys("pedido.creado", "documento.generar"));
        publisher = new PedidoEventPublisher(rabbitTemplate, props);
    }

    private static Pedido pedido() {
        Pedido p = new Pedido();
        p.setId(15L);
        p.setCliente("Ana");
        p.setDescripcion("Cafe molido");
        p.setTotal(4990.0);
        return p;
    }

    @Test
    void publicaElEventoEnElTopicYElComandoEnElDirect() {
        publisher.publicarPedidoCreado(pedido());

        verify(rabbitTemplate).convertAndSend(eq("pedidos.events.exchange"), eq("pedido.creado"),
                isA(PedidoCreadoEvent.class), any(MessagePostProcessor.class), any(CorrelationData.class));
        verify(rabbitTemplate).convertAndSend(eq("pedidos.tasks.exchange"), eq("documento.generar"),
                isA(GenerarDocumentoCommand.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void siRabbitMqFallaElPedidoNoSeRompe() {
        doThrow(new AmqpConnectException(new RuntimeException("broker caido")))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class),
                        any(MessagePostProcessor.class), any(CorrelationData.class));

        assertThatCode(() -> publisher.publicarPedidoCreado(pedido())).doesNotThrowAnyException();
    }
}
