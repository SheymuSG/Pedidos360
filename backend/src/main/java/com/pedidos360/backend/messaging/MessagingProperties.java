package com.pedidos360.backend.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges y routing keys de RabbitMQ, centralizados en
 * application.yml (prefijo pedidos360.messaging). Ninguna clase de negocio
 * escribe nombres a mano: todo se toma de este objeto.
 */
@ConfigurationProperties(prefix = "pedidos360.messaging")
public record MessagingProperties(Exchanges exchanges, RoutingKeys routingKeys) {

    /** events = exchange topic (eventos de dominio); tasks = exchange direct (comandos). */
    public record Exchanges(String events, String tasks) {}

    public record RoutingKeys(String pedidoCreado, String documentoGenerar) {}
}
