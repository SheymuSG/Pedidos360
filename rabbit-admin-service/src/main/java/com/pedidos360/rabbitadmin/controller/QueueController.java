package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.QueueCreateRequest;
import com.pedidos360.rabbitadmin.dto.QueueResponse;
import com.pedidos360.rabbitadmin.dto.ValidationPatterns;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** POST /api/queues, GET /api/queues/{name}, DELETE /api/queues/{name}. */
@RestController
@RequestMapping("/api/queues")
@Validated
public class QueueController {

    private final RabbitAdminService adminService;

    public QueueController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<QueueResponse> create(@Valid @RequestBody QueueCreateRequest request) {
        QueueResponse created = adminService.createQueue(request);
        return ResponseEntity.created(URI.create("/api/queues/" + created.name())).body(created);
    }

    @GetMapping("/{name}")
    public QueueResponse get(
            @PathVariable @Pattern(regexp = ValidationPatterns.BROKER_NAME,
                    message = ValidationPatterns.BROKER_NAME_MESSAGE) String name) {
        return adminService.getQueue(name);
    }

    @DeleteMapping("/{name}")
    public ResponseEntity<Void> delete(
            @PathVariable @Pattern(regexp = ValidationPatterns.BROKER_NAME,
                    message = ValidationPatterns.BROKER_NAME_MESSAGE) String name) {
        adminService.deleteQueue(name);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
