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

// El Bearer token lo adjunta authTokenInterceptor (core/auth-token.interceptor.ts) a toda llamada /api/.
// Este mapa se deja VACIO a proposito: cuando el frontend se sirve desde el mismo dominio que el
// API Gateway, las rutas /api/* coinciden con el origen y el MsalInterceptor de Microsoft se activaria
// ademas del nuestro; los dos pedirian token a la vez y se pisan (error al adquirir el token).
export const protectedResourceMap = new Map<string, string[]>();
