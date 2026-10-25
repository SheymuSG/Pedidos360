package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.config.AdminProperties;
import com.pedidos360.rabbitadmin.dto.BindingRequest;
import com.pedidos360.rabbitadmin.dto.ExchangeCreateRequest;
import com.pedidos360.rabbitadmin.dto.ExchangeResponse;
import com.pedidos360.rabbitadmin.dto.ExchangeType;
import com.pedidos360.rabbitadmin.dto.QueueCreateRequest;
import com.pedidos360.rabbitadmin.dto.QueueResponse;
import com.pedidos360.rabbitadmin.dto.QueueStatusResponse;
import com.pedidos360.rabbitadmin.exception.ResourceConflictException;
import com.pedidos360.rabbitadmin.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Toda la administracion del broker vive aqui. Los controladores solo llaman a estos
 * metodos de alto nivel y no conocen la libreria de RabbitMQ.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;
    private final AdminProperties properties;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate, AdminProperties properties) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    // ------------------------------------------------------------------ colas

    public QueueResponse createQueue(QueueCreateRequest request) {
        if (queueInfo(request.name()) != null) {
            throw new ResourceConflictException("La cola '" + request.name() + "' ya existe");
        }
        boolean durable = request.durable() == null || request.durable();
        QueueBuilder builder = durable ? QueueBuilder.durable(request.name()) : QueueBuilder.nonDurable(request.name());
        if (Boolean.TRUE.equals(request.autoDelete())) {
            builder.autoDelete();
        }
        if (hasText(request.deadLetterExchange())) {
            builder.deadLetterExchange(request.deadLetterExchange());
        }
        if (hasText(request.deadLetterRoutingKey())) {
            builder.deadLetterRoutingKey(request.deadLetterRoutingKey());
        }
        if (request.messageTtlMs() != null) {
            builder.ttl(request.messageTtlMs().intValue());
        }
        if (request.maxLength() != null) {
            builder.maxLength(request.maxLength());
        }
        Queue queue = builder.build();
        amqpAdmin.declareQueue(queue);
        log.info("[ADMIN] cola creada name={} durable={} dlx={}", queue.getName(), durable, request.deadLetterExchange());
        return getQueue(request.name());
    }

    public QueueResponse getQueue(String name) {
        QueueInformation info = queueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        return new QueueResponse(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    public void deleteQueue(String name) {
        if (queueInfo(name) == null) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        amqpAdmin.deleteQueue(name);
        log.info("[ADMIN] cola eliminada name={}", name);
    }

    // -------------------------------------------------------------- exchanges

    public ExchangeResponse createExchange(ExchangeCreateRequest request) {
        if (exchangeExists(request.name())) {
            throw new ResourceConflictException("El exchange '" + request.name() + "' ya existe");
        }
        boolean durable = request.durable() == null || request.durable();
        boolean autoDelete = Boolean.TRUE.equals(request.autoDelete());
        ExchangeBuilder builder = switch (request.type()) {
            case DIRECT -> ExchangeBuilder.directExchange(request.name());
            case TOPIC -> ExchangeBuilder.topicExchange(request.name());
            case FANOUT -> ExchangeBuilder.fanoutExchange(request.name());
            case HEADERS -> ExchangeBuilder.headersExchange(request.name());
        };
        builder.durable(durable);
        if (autoDelete) {
            builder.autoDelete();
        }
        Exchange exchange = builder.build();
        amqpAdmin.declareExchange(exchange);
        log.info("[ADMIN] exchange creado name={} type={} durable={}", request.name(), request.type(), durable);
        return new ExchangeResponse(request.name(), request.type(), durable, autoDelete);
    }

    public void deleteExchange(String name) {
        if (!exchangeExists(name)) {
            throw new ResourceNotFoundException("El exchange '" + name + "' no existe");
        }
        amqpAdmin.deleteExchange(name);
        log.info("[ADMIN] exchange eliminado name={}", name);
    }

    // --------------------------------------------------------------- bindings

    public void createBinding(BindingRequest request) {
        Binding binding = toBinding(request);
        amqpAdmin.declareBinding(binding);
        log.info("[ADMIN] binding creado exchange={} queue={} routingKey='{}'",
                request.exchange(), request.queue(), request.routingKeyOrEmpty());
    }

    public void deleteBinding(BindingRequest request) {
        Binding binding = toBinding(request);
        amqpAdmin.removeBinding(binding);
        log.info("[ADMIN] binding eliminado exchange={} queue={} routingKey='{}'",
                request.exchange(), request.queue(), request.routingKeyOrEmpty());
    }

    private Binding toBinding(BindingRequest request) {
        if (queueInfo(request.queue()) == null) {
            throw new ResourceNotFoundException("La cola '" + request.queue() + "' no existe");
        }
        if (!exchangeExists(request.exchange())) {
            throw new ResourceNotFoundException("El exchange '" + request.exchange() + "' no existe");
        }
        return new Binding(request.queue(), Binding.DestinationType.QUEUE,
                request.exchange(), request.routingKeyOrEmpty(), null);
    }

    // ------------------------------------------------------------ monitoreo

    /** Estado de cada cola principal y su DLQ, con alerta cuando la DLQ supera el umbral. */
    public List<QueueStatusResponse> monitoringStatus() {
        int threshold = properties.monitoring().alertThreshold();
        return properties.monitoring().queues().stream().map(spec -> {
            QueueInformation main = queueInfo(spec.name());
            QueueInformation dlq = queueInfo(spec.dlq());
            if (main == null || dlq == null) {
                return new QueueStatusResponse(spec.name(),
                        main == null ? 0 : main.getMessageCount(), main == null ? 0 : main.getConsumerCount(),
                        spec.dlq(), dlq == null ? 0 : dlq.getMessageCount(), false, "NO_DISPONIBLE");
            }
            boolean alert = dlq.getMessageCount() >= threshold;
            return new QueueStatusResponse(spec.name(), main.getMessageCount(), main.getConsumerCount(),
                    spec.dlq(), dlq.getMessageCount(), alert, alert ? "ALERTA" : "OK");
        }).toList();
    }

    // ---------------------------------------------------------------- helpers

    private QueueInformation queueInfo(String name) {
        return amqpAdmin.getQueueInfo(name);
    }

    /** exchange.declare pasivo: no crea nada, solo comprueba existencia (el canal se cierra si no existe). */
    private boolean exchangeExists(String name) {
        return Boolean.TRUE.equals(rabbitTemplate.execute(channel -> {
            try {
                channel.exchangeDeclarePassive(name);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
