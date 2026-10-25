import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', loadComponent: () => import('./pages/login/login.page').then((m) => m.LoginPage) },
  {
    path: 'pedidos',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/pedidos/pedidos.page').then((m) => m.PedidosPage),
  },
  {
    path: 'mensajeria',
    canActivate: [authGuard],
    loadComponent: () => import('./pages/mensajeria/mensajeria.page').then((m) => m.MensajeriaPage),
  },
  { path: 'login', redirectTo: '' },
  { path: '**', redirectTo: '' },
];
