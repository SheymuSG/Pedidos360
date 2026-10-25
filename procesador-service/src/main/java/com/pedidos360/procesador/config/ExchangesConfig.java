package com.pedidos360.procesador.config;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exchanges compartidos por las tres colas:
 *  - events      (TOPIC)  : eventos de dominio, ruteo por patron (pedido.*).
 *  - tasks       (DIRECT) : comandos con un unico destinatario (documento.generar).
 *  - deadLetter  (DIRECT) : DLX al que RabbitMQ re-publica los mensajes rechazados.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class ExchangesConfig {

    @Bean
    TopicExchange eventsExchange(MessagingProperties props) {
        return ExchangeBuilder.topicExchange(props.exchanges().events()).durable(true).build();
    }

    @Bean
    DirectExchange tasksExchange(MessagingProperties props) {
        return ExchangeBuilder.directExchange(props.exchanges().tasks()).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange(MessagingProperties props) {
        return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
    }
}
