import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { Pedido } from '../../core/pedido.model';
import { PedidoService } from '../../core/pedido.service';

@Component({
  selector: 'app-pedidos',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './pedidos.page.html',
  styleUrl: './pedidos.page.css',
})
export class PedidosPage implements OnInit {
  private pedidoService = inject(PedidoService);
  auth = inject(AuthService);

  pedidos: Pedido[] = [];
  cargando = false;
  error = '';

  nuevo: Pedido = { cliente: '', descripcion: '', total: 0 };

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.cargando = true;
    this.error = '';
    this.pedidoService.listar().subscribe({
      next: (data) => { this.pedidos = data; this.cargando = false; },
      error: (err) => {
        this.cargando = false;
        this.error = this.mensajeError(err);
      },
    });
  }

  crear(): void {
    if (!this.nuevo.cliente || !this.nuevo.descripcion) return;
    this.pedidoService.crear(this.nuevo).subscribe({
      next: () => {
        this.nuevo = { cliente: '', descripcion: '', total: 0 };
        this.cargar();
      },
      error: (err) => { this.error = this.mensajeError(err); },
    });
  }

  logout(): void {
    this.auth.logout();
  }

  private mensajeError(err: any): string {
    if (err?.status === 401) return 'Sesión inválida o expirada (401). Vuelve a iniciar sesión.';
    if (err?.status === 403) return 'No tienes permiso para esta operación (403).';
    return 'No se pudo conectar con el backend.';
  }
}
