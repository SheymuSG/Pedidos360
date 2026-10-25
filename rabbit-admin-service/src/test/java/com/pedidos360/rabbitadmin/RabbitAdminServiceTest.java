package com.pedidos360.rabbitadmin;

import com.pedidos360.rabbitadmin.config.AdminProperties;
import com.pedidos360.rabbitadmin.dto.QueueCreateRequest;
import com.pedidos360.rabbitadmin.dto.QueueResponse;
import com.pedidos360.rabbitadmin.dto.QueueStatusResponse;
import com.pedidos360.rabbitadmin.exception.ResourceConflictException;
import com.pedidos360.rabbitadmin.exception.ResourceNotFoundException;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RabbitAdminServiceTest {

    private AmqpAdmin amqpAdmin;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        amqpAdmin = mock(AmqpAdmin.class);
        var props = new AdminProperties(new AdminProperties.Monitoring(1,
                List.of(new AdminProperties.MonitoredQueue("tickets.queue", "tickets.dlq"))));
        service = new RabbitAdminService(amqpAdmin, mock(RabbitTemplate.class), props);
    }

    private static QueueInformation info(String name, long messages, int consumers) {
        return new QueueInformation(name, messages, consumers);
    }

    @Test
    void creaColaDurableConDlxTtlYLimite() {
        when(amqpAdmin.getQueueInfo("reportes.queue"))
                .thenReturn(null)                       // comprobacion previa: no existe
                .thenReturn(info("reportes.queue", 0, 0)); // lectura tras crearla

        QueueResponse response = service.createQueue(new QueueCreateRequest(
                "reportes.queue", null, null, "pedidos.dlx", "reportes.dead", 60_000L, 500L));

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(captor.capture());
        Queue declared = captor.getValue();
        assertThat(declared.isDurable()).isTrue();
        assertThat(declared.getArguments())
                .containsEntry("x-dead-letter-exchange", "pedidos.dlx")
                .containsEntry("x-dead-letter-routing-key", "reportes.dead")
                .containsEntry("x-message-ttl", 60_000)
                .containsEntry("x-max-length", 500L);
        assertThat(response.name()).isEqualTo("reportes.queue");
    }

    @Test
    void noRecreaUnaColaExistente() {
        when(amqpAdmin.getQueueInfo("tickets.queue")).thenReturn(info("tickets.queue", 3, 1));

        assertThatThrownBy(() -> service.createQueue(
                new QueueCreateRequest("tickets.queue", null, null, null, null, null, null)))
                .isInstanceOf(ResourceConflictException.class);
        verify(amqpAdmin, never()).declareQueue(any());
    }

    @Test
    void eliminarColaInexistenteLanzaNotFound() {
        when(amqpAdmin.getQueueInfo("nada")).thenReturn(null);

        assertThatThrownBy(() -> service.deleteQueue("nada")).isInstanceOf(ResourceNotFoundException.class);
        verify(amqpAdmin, never()).deleteQueue("nada");
    }

    @Test
    void eliminaColaExistente() {
        when(amqpAdmin.getQueueInfo("viejo.queue")).thenReturn(info("viejo.queue", 0, 0));

        service.deleteQueue("viejo.queue");

        verify(amqpAdmin).deleteQueue("viejo.queue");
    }

    @Test
    void monitoreoLevantaAlertaCuandoLaDlqTieneMensajes() {
        when(amqpAdmin.getQueueInfo("tickets.queue")).thenReturn(info("tickets.queue", 0, 1));
        when(amqpAdmin.getQueueInfo("tickets.dlq")).thenReturn(info("tickets.dlq", 2, 0));

        List<QueueStatusResponse> status = service.monitoringStatus();

        assertThat(status).singleElement().satisfies(s -> {
            assertThat(s.alert()).isTrue();
            assertThat(s.state()).isEqualTo("ALERTA");
            assertThat(s.dlqMessages()).isEqualTo(2);
        });
    }

    @Test
    void monitoreoIndicaNoDisponibleSiFaltaUnaCola() {
        when(amqpAdmin.getQueueInfo("tickets.queue")).thenReturn(null);
        when(amqpAdmin.getQueueInfo("tickets.dlq")).thenReturn(null);

        assertThat(service.monitoringStatus()).singleElement()
                .satisfies(s -> assertThat(s.state()).isEqualTo("NO_DISPONIBLE"));
    }
}
