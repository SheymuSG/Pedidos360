import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  template: `
    <div class="login-box">
      <h1>Pedidos360</h1>
      <p>Inicia sesión con tu cuenta de Microsoft Entra ID para gestionar tus pedidos.</p>
      <button (click)="entrar()">Iniciar sesión</button>
    </div>
  `,
  styles: [`
    .login-box { max-width: 360px; margin: 15vh auto; text-align: center; font-family: system-ui, sans-serif; }
    button { padding: 0.6rem 1.2rem; font-size: 1rem; cursor: pointer; }
  `],
})
export class LoginPage {
  private auth = inject(AuthService);
  private router = inject(Router);

  constructor() {
    if (this.auth.isLoggedIn) {
      this.router.navigateByUrl('/pedidos');
    }
  }

  entrar(): void {
    this.auth.login();
  }
}
