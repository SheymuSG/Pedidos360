package com.pedidos360.procesador.notificaciones;

import com.pedidos360.procesador.messaging.FailureSimulator;
import com.pedidos360.procesador.messaging.PedidoCreadoEvent;
import com.pedidos360.procesador.messaging.PermanentProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Logica de negocio de notificaciones: no conoce RabbitMQ, ACK ni colas. */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final FailureSimulator failureSimulator;

    public NotificacionService(FailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    public void notificar(PedidoCreadoEvent evento) {
        if (evento.pedidoId() == null || evento.cliente() == null || evento.cliente().isBlank()) {
            throw new PermanentProcessingException("el evento no trae pedidoId o cliente");
        }
        failureSimulator.check(NotificacionConsumer.NAME, evento.eventId(), evento.descripcion());

        // Aqui iria el envio real (correo, SMS, push). Se simula con un log.
        log.info("[NOTIFICACION] Pedido #{} confirmado: aviso enviado a cliente '{}' por un total de {}",
                evento.pedidoId(), evento.cliente(), evento.total());
    }
}
