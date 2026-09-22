import { Injectable, inject } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';
import { loginRequest } from './msal.config';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private msal = inject(MsalService);

  get account(): AccountInfo | null {
    return this.msal.instance.getActiveAccount() ?? this.msal.instance.getAllAccounts()[0] ?? null;
  }

  get isLoggedIn(): boolean {
    return this.account !== null;
  }

  get displayName(): string {
    return this.account?.name ?? this.account?.username ?? 'Usuario';
  }

  login(): void {
    this.msal.loginRedirect(loginRequest);
  }

  logout(): void {
    this.msal.logoutRedirect();
  }
}
