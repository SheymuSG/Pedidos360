package com.pedidos360.backend.messaging;

import com.pedidos360.backend.model.Pedido;

import java.time.Instant;
import java.util.UUID;

/**
 * Comando dirigido a la cola de documentos: pide generar el comprobante de un
 * pedido. A diferencia del evento, tiene un unico destinatario (exchange direct).
 */
public record GenerarDocumentoCommand(
        String commandId,
        String requestedAt,
        Long pedidoId,
        String cliente,
        String descripcion,
        Double total,
        String tipoDocumento) {

    public static GenerarDocumentoCommand comprobante(Pedido pedido) {
        return new GenerarDocumentoCommand(
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                pedido.getId(),
                pedido.getCliente(),
                pedido.getDescripcion(),
                pedido.getTotal(),
                "COMPROBANTE");
    }
}
