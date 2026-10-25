package com.pedidos360.rabbitadmin.exception;

/** El recurso ya existe o se intenta redeclarar con una configuracion distinta. */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
