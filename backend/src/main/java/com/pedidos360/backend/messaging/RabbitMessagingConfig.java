package com.pedidos360.backend.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion de mensajeria del productor. Solo declara los exchanges en los
 * que publica este servicio; las colas, DLQ y bindings pertenecen al
 * microservicio consumidor (procesador-service), que es quien las posee.
 * Declarar el exchange aqui tambien evita que el backend falle si arranca
 * antes que el consumidor (la declaracion es idempotente).
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitMessagingConfig {

    /** Exchange TOPIC: eventos de dominio (pedido.creado, pedido.*). */
    @Bean
    TopicExchange pedidosEventsExchange(MessagingProperties props) {
        return ExchangeBuilder.topicExchange(props.exchanges().events()).durable(true).build();
    }

    /** Exchange DIRECT: comandos dirigidos a un unico trabajador (documento.generar). */
    @Bean
    DirectExchange pedidosTasksExchange(MessagingProperties props) {
        return ExchangeBuilder.directExchange(props.exchanges().tasks()).durable(true).build();
    }

    /** Los mensajes viajan como JSON; el consumidor no depende de las clases del productor. */
    @Bean
    MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
