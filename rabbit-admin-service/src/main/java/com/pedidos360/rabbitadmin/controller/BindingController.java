package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.BindingRequest;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * POST /api/bindings                                  (cuerpo JSON)
 * DELETE /api/bindings?exchange=&queue=&routingKey=   (un DELETE no lleva cuerpo)
 */
@RestController
@RequestMapping("/api/bindings")
@Validated
public class BindingController {

    private final RabbitAdminService adminService;

    public BindingController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> create(@Valid @RequestBody BindingRequest request) {
        adminService.createBinding(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "exchange", request.exchange(),
                "queue", request.queue(),
                "routingKey", request.routingKeyOrEmpty()));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @ModelAttribute BindingRequest request) {
        adminService.deleteBinding(request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
