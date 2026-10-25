package com.pedidos360.rabbitadmin.dto;

/**
 * Estado de un caso de uso: cola principal + su DLQ.
 *
 * @param alert true cuando la DLQ alcanza el umbral configurado
 * @param state OK | ALERTA | NO_DISPONIBLE (alguna de las dos colas no existe en el broker)
 */
public record QueueStatusResponse(
        String queue,
        long queueMessages,
        int queueConsumers,
        String dlq,
        long dlqMessages,
        boolean alert,
        String state) {
}
