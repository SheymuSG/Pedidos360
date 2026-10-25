package com.pedidos360.rabbitadmin.exception;

/** El recurso (cola o exchange) no existe en el broker. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
