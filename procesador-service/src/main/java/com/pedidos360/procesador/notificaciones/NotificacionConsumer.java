package com.pedidos360.procesador.notificaciones;

import com.pedidos360.procesador.messaging.AckingMessageProcessor;
import com.pedidos360.procesador.messaging.PedidoCreadoEvent;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumidor del dominio NOTIFICACIONES. La cola se resuelve desde application.yml. */
@Component
public class NotificacionConsumer {

    public static final String NAME = "NOTIFICACION";

    private final AckingMessageProcessor processor;
    private final NotificacionService service;

    public NotificacionConsumer(AckingMessageProcessor processor, NotificacionService service) {
        this.processor = processor;
        this.service = service;
    }

    @RabbitListener(queues = "${pedidos360.messaging.queues.notificaciones.name}")
    public void onMessage(Message message, Channel channel) {
        processor.process(NAME, message, channel, PedidoCreadoEvent.class, service::notificar);
    }
}
