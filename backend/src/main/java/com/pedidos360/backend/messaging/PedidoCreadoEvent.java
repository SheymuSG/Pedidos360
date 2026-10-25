package com.pedidos360.backend.messaging;

import com.pedidos360.backend.model.Pedido;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio publicado cuando se crea un pedido. Es el contrato JSON
 * que consumen notificaciones y tickets en procesador-service.
 */
public record PedidoCreadoEvent(
        String eventId,
        String occurredAt,
        Long pedidoId,
        String cliente,
        String descripcion,
        Double total) {

    public static PedidoCreadoEvent from(Pedido pedido) {
        return new PedidoCreadoEvent(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                pedido.getId(),
                pedido.getCliente(),
                pedido.getDescripcion(),
                pedido.getTotal());
    }
}
