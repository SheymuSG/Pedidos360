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
 * Caso de uso TICKETS: evento pedido.creado (topic) -> tickets.queue.
 * Los mensajes rechazados terminan en tickets.dlq via el DLX.
 */
@Configuration
public class TicketsTopologyConfig {

    @Bean
    Queue ticketsQueue(MessagingProperties props) {
        return TopologySupport.mainQueue(props.queues().tickets(), props);
    }

    @Bean
    Queue ticketsDlq(MessagingProperties props) {
        return TopologySupport.deadLetterQueue(props.queues().tickets(), props);
    }

    @Bean
    Binding ticketsBinding(@Qualifier("ticketsQueue") Queue queue,
                           TopicExchange eventsExchange, MessagingProperties props) {
        return BindingBuilder.bind(queue).to(eventsExchange).with(props.queues().tickets().bindingKey());
    }

    @Bean
    Binding ticketsDlqBinding(@Qualifier("ticketsDlq") Queue dlq,
                              @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange,
                              MessagingProperties props) {
        return BindingBuilder.bind(dlq).to(deadLetterExchange).with(props.queues().tickets().deadLetterKey());
    }
}
