import { Configuration, PopupRequest } from '@azure/msal-browser';
import { environment } from '../../environments/environment';

export const msalConfig: Configuration = {
  auth: {
    clientId: environment.msal.clientId,
    authority: environment.msal.authority,
    redirectUri: environment.msal.redirectUri,
    postLogoutRedirectUri: environment.msal.postLogoutRedirectUri,
  },
  cache: {
    // localStorage para que la sesión sobreviva a un refresh de página.
    cacheLocation: 'localStorage',
  },
};

// PKCE (code_verifier / code_challenge, state y nonce) lo genera MSAL
// automáticamente con el flujo Authorization Code; no requiere configuración extra.
export const loginRequest: PopupRequest = {
  scopes: ['openid', 'profile', 'email', environment.msal.scope],
};

// Rutas que deben llevar el Bearer token del scope de la API.
export const protectedResourceMap = new Map<string, string[]>([
  [`${window.location.origin}/api/*`, [environment.msal.scope]],
  ['/api/*', [environment.msal.scope]],
]);
