package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.ExchangeCreateRequest;
import com.pedidos360.rabbitadmin.dto.ExchangeResponse;
import com.pedidos360.rabbitadmin.dto.ValidationPatterns;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** POST /api/exchanges, DELETE /api/exchanges/{name}. */
@RestController
@RequestMapping("/api/exchanges")
@Validated
public class ExchangeController {

    private final RabbitAdminService adminService;

    public ExchangeController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<ExchangeResponse> create(@Valid @RequestBody ExchangeCreateRequest request) {
        ExchangeResponse created = adminService.createExchange(request);
        return ResponseEntity.created(URI.create("/api/exchanges/" + created.name())).body(created);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(
            @PathVariable @Pattern(regexp = ValidationPatterns.BROKER_NAME,
                    message = ValidationPatterns.BROKER_NAME_MESSAGE) String name) {
        adminService.deleteExchange(name);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
