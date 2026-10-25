package com.pedidos360.procesador.messaging;

import com.pedidos360.procesador.config.MessagingProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Ejecuta un handler de negocio con confirmacion MANUAL y decide el destino del mensaje:
 *
 *  exito                      -> basicAck
 *  error permanente           -> basicNack(requeue=false)  -> DLX -> DLQ (sin reintentar)
 *  error recuperable          -> reintento con backoff; al agotarse -> basicNack(requeue=false) -> DLQ
 *  mensaje ya procesado       -> basicAck (idempotencia basica por messageId)
 *  hilo interrumpido          -> basicNack(requeue=true) para no perder el mensaje al apagar
 *
 * Con acknowledge-mode=manual Spring no confirma ni rechaza por su cuenta, por eso toda
 * salida del listener pasa por aqui: no puede quedar un mensaje sin ACK/NACK.
 */
@Component
public class AckingMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(AckingMessageProcessor.class);
    private static final int DEDUP_CAPACITY = 10_000;

    /** Espera entre reintentos; inyectable para poder probar sin dormir. */
    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final JacksonJsonMessageConverter converter;
    private final MessagingProperties.Retry retry;
    private final Sleeper sleeper;
    private final Set<String> processed = Collections.newSetFromMap(Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > DEDUP_CAPACITY;
                }
            }));

    /**
     * El conversor NO es un bean a proposito: si lo fuera, Spring Boot lo asignaria al contenedor
     * del listener y este intentaria reconstruir la clase del productor desde el header __TypeId__
     * (paquete no confiable) antes de llegar a nuestro codigo; y con ACK manual ese fallo dejaria el
     * mensaje sin confirmar. Aqui el tipo destino se indica explicitamente y los errores se manejan.
     */
    @Autowired
    public AckingMessageProcessor(MessagingProperties props) {
        this(new JacksonJsonMessageConverter(), props, Thread::sleep);
    }

    /** Constructor para pruebas: permite sustituir la espera entre reintentos. */
    public AckingMessageProcessor(JacksonJsonMessageConverter converter, MessagingProperties props, Sleeper sleeper) {
        this.converter = converter;
        this.retry = props.retry();
        this.sleeper = sleeper;
    }

    public <T> void process(String consumer, Message message, Channel channel,
                            Class<T> payloadType, Consumer<T> handler) {
        long tag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        String dedupKey = consumer + ":" + messageId;

        if (messageId != null && processed.contains(dedupKey)) {
            log.warn("[{}] mensaje duplicado msgId={}: se confirma sin reprocesar", consumer, messageId);
            ack(consumer, channel, tag);
            return;
        }

        T payload;
        try {
            payload = convert(message, payloadType);
        } catch (RuntimeException ex) {
            reject(consumer, channel, tag, messageId, "JSON invalido o no mapeable: " + ex.getMessage());
            return;
        }

        int attempt = 0;
        long delay = retry.initialIntervalMs();
        while (true) {
            attempt++;
            try {
                handler.accept(payload);
                if (messageId != null) {
                    processed.add(dedupKey);
                }
                ack(consumer, channel, tag);
                log.info("[{}] ACK msgId={} intento={}", consumer, messageId, attempt);
                return;
            } catch (PermanentProcessingException ex) {
                reject(consumer, channel, tag, messageId, "error permanente: " + ex.getMessage());
                return;
            } catch (RuntimeException ex) { // RecoverableProcessingException o imprevisto: se trata como transitorio
                if (attempt >= retry.maxAttempts()) {
                    reject(consumer, channel, tag, messageId,
                            "reintentos agotados (" + attempt + "): " + ex.getMessage());
                    return;
                }
                log.warn("[{}] intento {}/{} fallo msgId={}: {}. Reintento en {} ms",
                        consumer, attempt, retry.maxAttempts(), messageId, ex.getMessage(), delay);
                try {
                    sleeper.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    requeue(consumer, channel, tag, messageId);
                    return;
                }
                delay = Math.min((long) (delay * retry.multiplier()), retry.maxIntervalMs());
            }
        }
    }

    private <T> T convert(Message message, Class<T> type) {
        Object value = converter.fromMessage(message, ParameterizedTypeReference.forType(type));
        if (value == null) {
            throw new PermanentProcessingException("el cuerpo del mensaje esta vacio");
        }
        return type.cast(value);
    }

    private void ack(String consumer, Channel channel, long tag) {
        try {
            channel.basicAck(tag, false);
        } catch (IOException ex) {
            log.error("[{}] no se pudo enviar ACK tag={}: {}", consumer, tag, ex.getMessage());
        }
    }

    /** NACK sin requeue: con DLX configurado en la cola, el mensaje viaja a la DLQ. */
    private void reject(String consumer, Channel channel, long tag, String messageId, String reason) {
        try {
            channel.basicNack(tag, false, false);
            log.error("[{}] NACK -> DLQ msgId={} motivo={}", consumer, messageId, reason);
        } catch (IOException ex) {
            log.error("[{}] no se pudo enviar NACK tag={} msgId={}: {}", consumer, tag, messageId, ex.getMessage());
        }
    }

    private void requeue(String consumer, Channel channel, long tag, String messageId) {
        try {
            channel.basicNack(tag, false, true);
            log.warn("[{}] NACK requeue msgId={} (consumidor interrumpido)", consumer, messageId);
        } catch (IOException ex) {
            log.error("[{}] no se pudo reencolar tag={}: {}", consumer, tag, ex.getMessage());
        }
    }
}
