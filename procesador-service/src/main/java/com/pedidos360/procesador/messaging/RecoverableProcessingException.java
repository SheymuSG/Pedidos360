package com.pedidos360.procesador.messaging;

/**
 * Error transitorio (dependencia caida, timeout, IO): vale la pena reintentar.
 * Si se agotan los intentos el mensaje se rechaza y RabbitMQ lo envia a la DLQ.
 */
public class RecoverableProcessingException extends RuntimeException {

    public RecoverableProcessingException(String message) {
        super(message);
    }

    public RecoverableProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
