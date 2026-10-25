package com.pedidos360.rabbitadmin.dto;

/** Estado de una cola: mensajes listos y consumidores conectados. */
public record QueueResponse(String name, long messageCount, int consumerCount) {
}
