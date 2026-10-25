import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, catchError, of, switchMap, timer } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { ColaEstado } from '../../core/mensajeria.model';
import { MensajeriaService } from '../../core/mensajeria.service';

/** Intervalo de actualización. Es polling (no push): suficiente para ver una DLQ crecer. */
const REFRESCO_MS = 5000;

@Component({
  selector: 'app-mensajeria',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './mensajeria.page.html',
  styleUrl: './mensajeria.page.css',
})
export class MensajeriaPage implements OnInit, OnDestroy {
  private servicio = inject(MensajeriaService);
  auth = inject(AuthService);

  colas: ColaEstado[] = [];
  error = '';
  cargado = false;
  private suscripcion?: Subscription;

  get hayAlerta(): boolean {
    return this.colas.some((c) => c.alert);
  }

  get totalEnDlq(): number {
    return this.colas.reduce((suma, c) => suma + c.dlqMessages, 0);
  }

  ngOnInit(): void {
    this.suscripcion = timer(0, REFRESCO_MS)
      .pipe(
        switchMap(() =>
          this.servicio.estadoColas().pipe(
            catchError((err) => {
              this.error = this.mensajeError(err);
              return of(null);
            })
          )
        )
      )
      .subscribe((datos) => {
        this.cargado = true;
        if (datos) {
          this.colas = datos;
          this.error = '';
        }
      });
  }

  ngOnDestroy(): void {
    this.suscripcion?.unsubscribe();
  }

  logout(): void {
    this.auth.logout();
  }

  private mensajeError(err: any): string {
    if (err?.status === 401) return 'Sesión inválida o expirada (401). Vuelve a iniciar sesión.';
    if (err?.status === 403) return 'No tienes permiso para ver el estado de la mensajería (403).';
    if (err?.status === 503) return 'El administrador no logra conectar con RabbitMQ (503).';
    return 'No se pudo conectar con el microservicio administrador de RabbitMQ.';
  }
}
