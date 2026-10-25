package com.pedidos360.procesador.messaging;

/** Contrato JSON del comando "generar documento" publicado por el backend. */
public record GenerarDocumentoCommand(
        String commandId,
        String requestedAt,
        Long pedidoId,
        String cliente,
        String descripcion,
        Double total,
        String tipoDocumento) {
}
