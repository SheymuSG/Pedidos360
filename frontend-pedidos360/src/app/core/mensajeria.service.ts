import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ColaEstado } from './mensajeria.model';

/** Consulta al microservicio administrador de RabbitMQ (rabbit-admin-service). */
@Injectable({ providedIn: 'root' })
export class MensajeriaService {
  private http = inject(HttpClient);
  private base = `${environment.adminApiBaseUrl}/api/monitoring`;

  estadoColas(): Observable<ColaEstado[]> {
    return this.http.get<ColaEstado[]>(`${this.base}/queues`);
  }
}
