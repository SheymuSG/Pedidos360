package com.pedidos360.procesador.messaging;

/**
 * Error definitivo (JSON invalido, dato que viola una regla de negocio):
 * reintentar no ayuda. El mensaje se rechaza de inmediato hacia la DLQ.
 */
public class PermanentProcessingException extends RuntimeException {

    public PermanentProcessingException(String message) {
        super(message);
    }

    public PermanentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
