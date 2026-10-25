package com.pedidos360.rabbitadmin.controller;

import com.pedidos360.rabbitadmin.dto.QueueStatusResponse;
import com.pedidos360.rabbitadmin.service.RabbitAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** GET /api/monitoring/queues: cola principal + DLQ de cada caso de uso, con alerta. */
@RestController
@RequestMapping("/api/monitoring")
public class MonitoringController {

    private final RabbitAdminService adminService;

    public MonitoringController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/queues")
    public List<QueueStatusResponse> queues() {
        return adminService.monitoringStatus();
    }
}
