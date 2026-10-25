package com.pedidos360.procesador.documentos;

import com.pedidos360.procesador.messaging.AckingMessageProcessor;
import com.pedidos360.procesador.messaging.GenerarDocumentoCommand;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** Consumidor del dominio DOCUMENTOS. La cola se resuelve desde application.yml. */
@Component
public class DocumentoConsumer {

    public static final String NAME = "DOCUMENTO";

    private final AckingMessageProcessor processor;
    private final DocumentoService service;

    public DocumentoConsumer(AckingMessageProcessor processor, DocumentoService service) {
        this.processor = processor;
        this.service = service;
    }

    @RabbitListener(queues = "${pedidos360.messaging.queues.documentos.name}")
    public void onMessage(Message message, Channel channel) {
        processor.process(NAME, message, channel, GenerarDocumentoCommand.class, service::generar);
    }
}
