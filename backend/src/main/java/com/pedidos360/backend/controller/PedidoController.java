package com.pedidos360.backend.controller; // Ajusta si tu paquete base es distinto

import com.pedidos360.backend.model.Pedido;
import com.pedidos360.backend.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    // Endpoint 1: Obtener todos los pedidos (Lectura) - cualquier usuario autenticado
    @GetMapping
    public List<Pedido> listarPedidos() {
        return pedidoRepository.findAll();
    }

    // Endpoint 2: Crear un nuevo pedido (Escritura) - exige el rol Cliente del token.
    // Si el usuario esta autenticado pero no tiene ese rol, Spring Security
    // responde 403 (ver SecurityConfig.jsonAccessDeniedHandler).
    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<Pedido> crearPedido(@RequestBody Pedido pedido) {
        Pedido nuevoPedido = pedidoRepository.save(pedido);
        return ResponseEntity.ok(nuevoPedido);
    }
}