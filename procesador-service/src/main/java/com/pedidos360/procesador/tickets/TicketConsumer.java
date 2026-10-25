package com.pedidos360.procesador.tickets;

import com.pedidos360.procesador.messaging.AckingMessageProcessor;
import com.pedidos360.procesador.messaging.PedidoCreadoEvent;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumidor del dominio TICKETS. La cola se resuelve desde application.yml. */
@Component
public class TicketConsumer {

    public static final String NAME = "TICKET";

    private final AckingMessageProcessor processor;
    private final TicketService service;

    public TicketConsumer(AckingMessageProcessor processor, TicketService service) {
        this.processor = processor;
        this.service = service;
    }

    @RabbitListener(queues = "${pedidos360.messaging.queues.tickets.name}")
    public void onMessage(Message message, Channel channel) {
        processor.process(NAME, message, channel, PedidoCreadoEvent.class, service::generar);
    }
}
