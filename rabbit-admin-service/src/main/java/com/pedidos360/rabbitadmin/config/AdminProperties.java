package com.pedidos360.rabbitadmin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuracion del administrador (application.yml, prefijo pedidos360.admin).
 * Los nombres de las colas monitoreadas viven aqui y no en el codigo.
 */
@ConfigurationProperties(prefix = "pedidos360.admin")
public record AdminProperties(Monitoring monitoring) {

    /**
     * @param alertThreshold mensajes en una DLQ a partir de los cuales se levanta la alerta
     * @param queues         pares cola principal / DLQ que se vigilan
     */
    public record Monitoring(int alertThreshold, List<MonitoredQueue> queues) {}

    public record MonitoredQueue(String name, String dlq) {}
}
