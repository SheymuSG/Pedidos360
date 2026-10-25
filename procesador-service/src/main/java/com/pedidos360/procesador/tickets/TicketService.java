package com.pedidos360.procesador.tickets;

import com.pedidos360.procesador.messaging.FailureSimulator;
import com.pedidos360.procesador.messaging.PedidoCreadoEvent;
import com.pedidos360.procesador.messaging.PermanentProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Logica de negocio de tickets: no conoce RabbitMQ, ACK ni colas. */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final FailureSimulator failureSimulator;

    public TicketService(FailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    /** @return codigo del ticket generado */
    public String generar(PedidoCreadoEvent evento) {
        if (evento.pedidoId() == null) {
            throw new PermanentProcessingException("el evento no trae pedidoId");
        }
        failureSimulator.check(TicketConsumer.NAME, evento.eventId(), evento.descripcion());

        String codigo = String.format("TCK-%06d", evento.pedidoId());
        log.info("[TICKET] Ticket {} generado para el pedido #{} (cliente '{}')",
                codigo, evento.pedidoId(), evento.cliente());
        return codigo;
    }
}
