package com.pedidos360.procesador;

import com.pedidos360.procesador.messaging.FailureSimulator;
import com.pedidos360.procesador.messaging.PermanentProcessingException;
import com.pedidos360.procesador.messaging.RecoverableProcessingException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailureSimulatorTest {

    @Test
    void desactivadoNuncaFalla() {
        var sim = new FailureSimulator(false);
        assertThatCode(() -> sim.check("TICKET", "k", "#FAIL-TICKET")).doesNotThrowAnyException();
    }

    @Test
    void soloAfectaAlConsumidorIndicado() {
        var sim = new FailureSimulator(true);
        assertThatCode(() -> sim.check("NOTIFICACION", "k", "pedido #FAIL-TICKET")).doesNotThrowAnyException();
        assertThatThrownBy(() -> sim.check("TICKET", "k", "pedido #FAIL-TICKET"))
                .isInstanceOf(RecoverableProcessingException.class);
    }

    @Test
    void invalidEsPermanente() {
        var sim = new FailureSimulator(true);
        assertThatThrownBy(() -> sim.check("DOCUMENTO", "k", "#INVALID-DOCUMENTO"))
                .isInstanceOf(PermanentProcessingException.class);
    }

    @Test
    void flakyFallaDosVecesYLuegoFunciona() {
        var sim = new FailureSimulator(true);
        assertThatThrownBy(() -> sim.check("TICKET", "k", "#FLAKY-TICKET")).isInstanceOf(RecoverableProcessingException.class);
        assertThatThrownBy(() -> sim.check("TICKET", "k", "#FLAKY-TICKET")).isInstanceOf(RecoverableProcessingException.class);
        assertThatCode(() -> sim.check("TICKET", "k", "#FLAKY-TICKET")).doesNotThrowAnyException();
    }
}
