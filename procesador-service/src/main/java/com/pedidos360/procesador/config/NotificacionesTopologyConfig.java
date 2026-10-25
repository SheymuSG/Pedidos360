package com.pedidos360.procesador.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caso de uso NOTIFICACIONES: eventos pedido.* (topic) -> notificaciones.queue.
 * Los mensajes rechazados terminan en notificaciones.dlq via el DLX.
 */
@Configuration
public class NotificacionesTopologyConfig {

    @Bean
    Queue notificacionesQueue(MessagingProperties props) {
        return TopologySupport.mainQueue(props.queues().notificaciones(), props);
    }

    @Bean
    Queue notificacionesDlq(MessagingProperties props) {
        return TopologySupport.deadLetterQueue(props.queues().notificaciones(), props);
    }

    @Bean
    Binding notificacionesBinding(@Qualifier("notificacionesQueue") Queue queue,
                                  TopicExchange eventsExchange, MessagingProperties props) {
        return BindingBuilder.bind(queue).to(eventsExchange).with(props.queues().notificaciones().bindingKey());
    }

    @Bean
    Binding notificacionesDlqBinding(@Qualifier("notificacionesDlq") Queue dlq,
                                     @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
                                     MessagingProperties props) {
        return BindingBuilder.bind(dlq).to(deadLetterExchange).with(props.queues().notificaciones().deadLetterKey());
    }
}
