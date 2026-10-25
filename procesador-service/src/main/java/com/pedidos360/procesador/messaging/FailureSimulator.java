package com.pedidos360.procesador.messaging;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simulacion didactica de fallos para demostrar reintentos y DLQ (no es logica de negocio).
 * Se activa con pedidos360.processor.failure-simulation=true y se dispara con marcadores
 * en la descripcion del pedido:
 *
 *   #FAIL-TICKET      error recuperable que nunca se resuelve -> N reintentos -> DLQ
 *   #INVALID-TICKET   error permanente -> DLQ de inmediato, sin reintentos
 *   #FLAKY-TICKET     falla los 2 primeros intentos y luego funciona (demuestra el retry)
 *
 * El destino puede ser NOTIFICACION, TICKET o DOCUMENTO. En produccion debe quedar en false.
 */
@Component
public class FailureSimulator {

    private static final Pattern MARKER = Pattern.compile("#(FAIL|INVALID|FLAKY)-(NOTIFICACION|TICKET|DOCUMENTO)");
    private static final int FLAKY_FAILURES = 2;

    private final boolean enabled;
    private final Map<String, Integer> flakyAttempts = new ConcurrentHashMap<>();

    public FailureSimulator(@Value("${pedidos360.processor.failure-simulation:false}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @param target nombre del consumidor (NOTIFICACION, TICKET o DOCUMENTO)
     * @param key    identificador estable del mensaje (para contar intentos de FLAKY)
     */
    public void check(String target, String key, String descripcion) {
        if (!enabled || descripcion == null) {
            return;
        }
        Matcher m = MARKER.matcher(descripcion);
        while (m.find()) {
            if (!m.group(2).equals(target)) {
                continue;
            }
            switch (m.group(1)) {
                case "INVALID" -> throw new PermanentProcessingException(
                        "Simulacion: dato invalido para " + target + " (" + m.group() + ")");
                case "FAIL" -> throw new RecoverableProcessingException(
                        "Simulacion: servicio externo de " + target + " no disponible (" + m.group() + ")");
                case "FLAKY" -> {
                    int n = flakyAttempts.merge(target + ":" + key, 1, Integer::sum);
                    if (n <= FLAKY_FAILURES) {
                        throw new RecoverableProcessingException(
                                "Simulacion: fallo transitorio " + n + "/" + FLAKY_FAILURES + " en " + target);
                    }
                    flakyAttempts.remove(target + ":" + key);
                }
                default -> { }
            }
        }
    }
}
