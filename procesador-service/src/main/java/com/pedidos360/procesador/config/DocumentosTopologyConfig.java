package com.pedidos360.procesador.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caso de uso DOCUMENTOS: comando documento.generar (direct) -> documentos.queue.
 * Los mensajes rechazados terminan en documentos.dlq via el DLX.
 */
@Configuration
public class DocumentosTopologyConfig {

    @Bean
    Queue documentosQueue(MessagingProperties props) {
        return TopologySupport.mainQueue(props.queues().documentos(), props);
    }

    @Bean
    Queue documentosDlq(MessagingProperties props) {
        return TopologySupport.deadLetterQueue(props.queues().documentos(), props);
    }

    @Bean
    Binding documentosBinding(@Qualifier("documentosQueue") Queue queue,
                              @Qualifier("tasksExchange") DirectExchange tasksExchange,
                              MessagingProperties props) {
        return BindingBuilder.bind(queue).to(tasksExchange).with(props.queues().documentos().bindingKey());
    }

    @Bean
    Binding documentosDlqBinding(@Qualifier("documentosDlq") Queue dlq,
                                 @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
                                 MessagingProperties props) {
        return BindingBuilder.bind(dlq).to(deadLetterExchange).with(props.queues().documentos().deadLetterKey());
    }
}
