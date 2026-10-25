package com.pedidos360.rabbitadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Binding exchange -> cola. La routing key puede ser vacia (p. ej. exchange fanout). */
public record BindingRequest(
        @NotBlank(message = "exchange es obligatorio")
        @Pattern(regexp = ValidationPatterns.BROKER_NAME, message = ValidationPatterns.BROKER_NAME_MESSAGE)
        String exchange,

        @NotBlank(message = "queue es obligatorio")
        @Pattern(regexp = ValidationPatterns.BROKER_NAME, message = ValidationPatterns.BROKER_NAME_MESSAGE)
        String queue,

        @Pattern(regexp = ValidationPatterns.ROUTING_KEY, message = ValidationPatterns.ROUTING_KEY_MESSAGE)
        String routingKey) {

    public String routingKeyOrEmpty() {
        return routingKey == null ? "" : routingKey;
    }
}
