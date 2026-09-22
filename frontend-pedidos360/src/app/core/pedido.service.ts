import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { Pedido } from './pedido.model';

@Injectable({ providedIn: 'root' })
export class PedidoService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/api/pedidos`;

  listar(): Observable<Pedido[]> {
    return this.http.get<Pedido[]>(this.base);
  }

  crear(pedido: Pedido): Observable<Pedido> {
    return this.http.post<Pedido>(this.base, pedido);
  }
}
