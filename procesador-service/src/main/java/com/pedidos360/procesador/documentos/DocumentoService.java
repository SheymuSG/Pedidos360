package com.pedidos360.procesador.documentos;

import com.pedidos360.procesador.messaging.FailureSimulator;
import com.pedidos360.procesador.messaging.GenerarDocumentoCommand;
import com.pedidos360.procesador.messaging.PermanentProcessingException;
import com.pedidos360.procesador.messaging.RecoverableProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Logica de negocio de documentos: genera el comprobante de un pedido en disco. */
@Service
public class DocumentoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoService.class);

    private final FailureSimulator failureSimulator;
    private final Path directorio;

    public DocumentoService(FailureSimulator failureSimulator,
                            @Value("${pedidos360.processor.documents-dir:./generated-documents}") String directorio) {
        this.failureSimulator = failureSimulator;
        this.directorio = Path.of(directorio);
    }

    /** @return ruta del documento generado */
    public Path generar(GenerarDocumentoCommand comando) {
        if (comando.pedidoId() == null || comando.cliente() == null || comando.cliente().isBlank()) {
            throw new PermanentProcessingException("el comando no trae pedidoId o cliente");
        }
        failureSimulator.check(DocumentoConsumer.NAME, comando.commandId(), comando.descripcion());

        String tipo = comando.tipoDocumento() == null ? "DOCUMENTO" : comando.tipoDocumento().toLowerCase();
        Path destino = directorio.resolve(tipo + "-pedido-" + comando.pedidoId() + ".txt");
        String contenido = """
                COMPROBANTE DE PEDIDO
                ---------------------
                Pedido     : #%d
                Cliente    : %s
                Detalle    : %s
                Total      : %s
                Solicitado : %s
                """.formatted(comando.pedidoId(), comando.cliente(), comando.descripcion(),
                comando.total(), comando.requestedAt());
        try {
            Files.createDirectories(directorio);
            Files.writeString(destino, contenido, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            // El disco puede volver a estar disponible: es un error recuperable.
            throw new RecoverableProcessingException("no se pudo escribir " + destino + ": " + ex.getMessage(), ex);
        }
        log.info("[DOCUMENTO] {} generado para el pedido #{} en {}", tipo, comando.pedidoId(), destino);
        return destino;
    }
}
