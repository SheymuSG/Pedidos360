package com.pedidos360.procesador.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

/**
 * Construye las colas con la misma receta para los tres casos de uso:
 * la cola principal conoce su DLX y la DLQ aplica la politica de retencion.
 */
final class TopologySupport {

    private TopologySupport() {}

    static Queue mainQueue(MessagingProperties.QueueSpec spec, MessagingProperties props) {
        return QueueBuilder.durable(spec.name())
                .deadLetterExchange(props.exchanges().deadLetter())
                .deadLetterRoutingKey(spec.deadLetterKey())
                .build();
    }

    static Queue deadLetterQueue(MessagingProperties.QueueSpec spec, MessagingProperties props) {
        return QueueBuilder.durable(spec.dlq())
                .ttl((int) props.deadLetter().retentionMs())
                .maxLength(props.deadLetter().maxLength())
                .build();
    }
}
