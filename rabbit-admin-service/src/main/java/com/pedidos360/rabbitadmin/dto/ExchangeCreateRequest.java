package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Peticion para crear un exchange. Tipo y nombre son obligatorios. */
public record ExchangeCreateRequest(
        @NotBlank(message = "name es obligatorio")
        @Pattern(regexp = ValidationPatterns.BROKER_NAME, message = ValidationPatterns.BROKER_NAME_MESSAGE)
        String name,

        @NotNull(message = "type es obligatorio (DIRECT, TOPIC, FANOUT o HEADERS)")
        ExchangeType type,

        Boolean durable,

        Boolean autoDelete) {
}
