package com.pedidos360.backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/prueba")
    public String pruebaSeguridad() {
        return "¡Conexión exitosa! Spring Boot validó correctamente tu token de Azure.";
    }
}