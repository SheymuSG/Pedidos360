package com.pedidos360.procesador.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Unica fuente de verdad de nombres de exchanges, colas, DLQ y bindings del
 * microservicio (application.yml, prefijo pedidos360.messaging). Ninguna clase
 * de negocio ni de configuracion escribe un nombre literal.
 */
@ConfigurationProperties(prefix = "pedidos360.messaging")
public record MessagingProperties(
        Exchanges exchanges,
        Queues queues,
        DeadLetter deadLetter,
        Retry retry) {

    /** events = topic, tasks = direct, deadLetter = direct (DLX comun a las tres colas). */
    public record Exchanges(String events, String tasks, String deadLetter) {}

    public record Queues(QueueSpec notificaciones, QueueSpec tickets, QueueSpec documentos) {}

    /**
     * @param name          cola principal
     * @param bindingKey    routing key / patron con el que se enlaza al exchange de origen
     * @param dlq           cola de mensajes muertos asociada
     * @param deadLetterKey routing key con la que la cola principal publica en el DLX
     */
    public record QueueSpec(String name, String bindingKey, String dlq, String deadLetterKey) {}

    /** Politica de retencion de las DLQ: tiempo maximo y tamano maximo. */
    public record DeadLetter(long retentionMs, long maxLength) {}

    /** Reintentos del consumidor ante errores recuperables, con backoff exponencial. */
    public record Retry(int maxAttempts, long initialIntervalMs, double multiplier, long maxIntervalMs) {}
}
