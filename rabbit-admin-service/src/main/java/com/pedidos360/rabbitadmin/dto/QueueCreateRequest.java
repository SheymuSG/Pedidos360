package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Peticion para crear una cola. Solo "name" es obligatorio; el resto tiene valores
 * por defecto seguros (durable=true).
 */
public record QueueCreateRequest(
        @NotBlank(message = "name es obligatorio")
        @Pattern(regexp = ValidationPatterns.BROKER_NAME, message = ValidationPatterns.BROKER_NAME_MESSAGE)
        String name,

        Boolean durable,

        Boolean autoDelete,

        /** Exchange al que la cola envia los mensajes rechazados o vencidos (DLX). */
        @Pattern(regexp = "^$|" + ValidationPatterns.BROKER_NAME, message = ValidationPatterns.BROKER_NAME_MESSAGE)
        String deadLetterExchange,

        @Pattern(regexp = ValidationPatterns.ROUTING_KEY, message = ValidationPatterns.ROUTING_KEY_MESSAGE)
        String deadLetterRoutingKey,

        /** Tiempo de vida de los mensajes en la cola, en milisegundos. */
        @PositiveOrZero(message = "messageTtlMs no puede ser negativo")
        @Max(value = Integer.MAX_VALUE, message = "messageTtlMs excede el maximo permitido")
        Long messageTtlMs,

        /** Maximo de mensajes retenidos en la cola. */
        @Positive(message = "maxLength debe ser mayor que 0")
        Long maxLength) {

    @AssertTrue(message = "deadLetterRoutingKey requiere deadLetterExchange")
    public boolean isDeadLetterConfigValid() {
        boolean tieneKey = deadLetterRoutingKey != null && !deadLetterRoutingKey.isBlank();
        boolean tieneDlx = deadLetterExchange != null && !deadLetterExchange.isBlank();
        return !tieneKey || tieneDlx;
    }
}
