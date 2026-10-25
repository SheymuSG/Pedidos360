package com.pedidos360.backend.messaging;

import com.pedidos360.backend.model.Pedido;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor de mensajes del dominio de pedidos.
 *
 * Regla de diseno: la mensajeria nunca rompe la logica existente. El pedido ya
 * esta guardado en la base de datos cuando se publica; si RabbitMQ no esta
 * disponible solo se registra el error y la API responde igual.
 */
@Component
public class PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    /** Confirmaciones del broker (publisher confirms) y mensajes no enrutables (returns). */
    @PostConstruct
    void registrarCallbacks() {
        rabbitTemplate.setConfirmCallback((correlation, ack, cause) -> {
            String id = correlation != null ? correlation.getId() : "-";
            if (ack) {
                log.debug("[PUBLISH-CONFIRM] ack msgId={}", id);
            } else {
                log.error("[PUBLISH-CONFIRM] nack msgId={} causa={}", id, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> log.error(
                "[PUBLISH-RETURN] mensaje no enrutable exchange={} routingKey={} motivo={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
    }

    public void publicarPedidoCreado(Pedido pedido) {
        PedidoCreadoEvent evento = PedidoCreadoEvent.from(pedido);
        publicar(props.exchanges().events(), props.routingKeys().pedidoCreado(), evento.eventId(), evento);

        GenerarDocumentoCommand comando = GenerarDocumentoCommand.comprobante(pedido);
        publicar(props.exchanges().tasks(), props.routingKeys().documentoGenerar(), comando.commandId(), comando);
    }

    private void publicar(String exchange, String routingKey, String messageId, Object payload) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, message -> {
                message.getMessageProperties().setMessageId(messageId);
                return message;
            }, new CorrelationData(messageId));
            log.info("[PUBLISH] exchange={} routingKey={} msgId={}", exchange, routingKey, messageId);
        } catch (AmqpException ex) {
            log.error("[PUBLISH-ERROR] no se pudo publicar exchange={} routingKey={} msgId={}: {}",
                    exchange, routingKey, messageId, ex.getMessage());
        }
    }
}
