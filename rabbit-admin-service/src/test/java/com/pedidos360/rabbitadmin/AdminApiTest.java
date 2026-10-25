package com.pedidos360.rabbitadmin;

import com.pedidos360.rabbitadmin.dto.ExchangeResponse;
import com.pedidos360.rabbitadmin.dto.ExchangeType;
import com.pedidos360.rabbitadmin.dto.QueueResponse;
import com.pedidos360.rabbitadmin.dto.QueueStatusResponse;
import com.pedidos360.rabbitadmin.exception.ResourceConflictException;
import com.pedidos360.rabbitadmin.exception.ResourceNotFoundException;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la API completa (seguridad + validacion + codigos HTTP) sin RabbitMQ real:
 * el servicio de administracion se reemplaza por un mock.
 */
@SpringBootTest(properties = "pedidos360.security.local-jwt-secret=secreto-solo-para-pruebas-unitarias-0123456789")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AdminApiTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RabbitAdminService adminService;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor soloLectura() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    // ---------------------------------------------------------------- seguridad

    @Test
    void sinTokenDevuelve401EnJson() throws Exception {
        mvc.perform(post("/api/queues").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"q\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void usuarioSinRolAdminNoPuedeCrearColas() throws Exception {
        mvc.perform(post("/api/queues").with(soloLectura())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"cola.valida\"}"))
                .andExpect(status().isForbidden());
        verify(adminService, never()).createQueue(any());
    }

    @Test
    void usuarioAutenticadoPuedeConsultarElMonitoreo() throws Exception {
        when(adminService.monitoringStatus()).thenReturn(List.of(
                new QueueStatusResponse("tickets.queue", 0, 1, "tickets.dlq", 2, true, "ALERTA")));

        mvc.perform(get("/api/monitoring/queues").with(soloLectura()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].queue").value("tickets.queue"))
                .andExpect(jsonPath("$[0].alert").value(true));
    }

    // --------------------------------------------------------------- validacion

    @Test
    void colaConNombreVacioSeRechaza() throws Exception {
        mvc.perform(post("/api/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isNotEmpty());
        verify(adminService, never()).createQueue(any());
    }

    @Test
    void colaConPrefijoReservadoAmqSeRechaza() throws Exception {
        mvc.perform(post("/api/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"amq.mia\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void colaConCaracteresInvalidosSeRechaza() throws Exception {
        mvc.perform(post("/api/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"mi cola/../x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ttlNegativoSeRechaza() throws Exception {
        mvc.perform(post("/api/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cola.ttl\",\"messageTtlMs\":-5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void routingKeyDeDlqSinExchangeSeRechaza() throws Exception {
        mvc.perform(post("/api/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"cola.x\",\"deadLetterRoutingKey\":\"x.dead\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchangeConTipoDesconocidoSeRechaza() throws Exception {
        mvc.perform(post("/api/exchanges").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ex.1\",\"type\":\"inventado\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bindingSinColaSeRechaza() throws Exception {
        mvc.perform(post("/api/bindings").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"ex.1\",\"routingKey\":\"a.b\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nombreInvalidoEnLaRutaSeRechaza() throws Exception {
        mvc.perform(delete("/api/queues/{name}", "amq.reservada").with(admin()))
                .andExpect(status().isBadRequest());
        verify(adminService, never()).deleteQueue(any());
    }

    // ------------------------------------------------------------ casos de exito

    @Test
    void crearColaValidaDevuelve201ConLocation() throws Exception {
        when(adminService.createQueue(any())).thenReturn(new QueueResponse("reportes.queue", 0, 0));

        mvc.perform(post("/api/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"reportes.queue","deadLetterExchange":"pedidos.dlx",
                                 "deadLetterRoutingKey":"reportes.dead","messageTtlMs":60000}"""))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", "/api/queues/reportes.queue"))
                .andExpect(jsonPath("$.name").value("reportes.queue"));
    }

    @Test
    void crearExchangeValidoDevuelve201() throws Exception {
        when(adminService.createExchange(any()))
                .thenReturn(new ExchangeResponse("ex.topic", ExchangeType.TOPIC, true, false));

        mvc.perform(post("/api/exchanges").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"ex.topic\",\"type\":\"TOPIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TOPIC"));
    }

    @Test
    void eliminarColaExistenteDevuelve204() throws Exception {
        mvc.perform(delete("/api/queues/{name}", "reportes.queue").with(admin()))
                .andExpect(status().isNoContent());
        verify(adminService).deleteQueue(eq("reportes.queue"));
    }

    // -------------------------------------------------------- errores de negocio

    @Test
    void eliminarColaInexistenteDevuelve404() throws Exception {
        doThrow(new ResourceNotFoundException("La cola 'nada' no existe")).when(adminService).deleteQueue("nada");

        mvc.perform(delete("/api/queues/{name}", "nada").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La cola 'nada' no existe"));
    }

    @Test
    void crearColaExistenteDevuelve409() throws Exception {
        when(adminService.createQueue(any())).thenThrow(new ResourceConflictException("La cola 'q' ya existe"));

        mvc.perform(post("/api/queues").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"q\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void eliminarBindingUsaParametrosDeConsulta() throws Exception {
        mvc.perform(delete("/api/bindings").with(admin())
                        .param("exchange", "pedidos.events.exchange")
                        .param("queue", "tickets.queue")
                        .param("routingKey", "pedido.creado"))
                .andExpect(status().isNoContent());
        verify(adminService).deleteBinding(any());
    }
}
