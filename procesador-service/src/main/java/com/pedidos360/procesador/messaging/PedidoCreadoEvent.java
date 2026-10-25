package com.pedidos360.procesador.messaging;

/** Contrato JSON del evento publicado por el backend de pedidos. */
public record PedidoCreadoEvent(
        String eventId,
        String occurredAt,
        Long pedidoId,
        String cliente,
        String descripcion,
        Double total) {
}
