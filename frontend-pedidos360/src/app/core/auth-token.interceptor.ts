import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { catchError, from, switchMap, throwError } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * Adjunta el Bearer token (JWT de Entra ID) a toda llamada hacia /api.
 * El backend (y luego el API Gateway) validan ese token en cada petición.
 */
export const authTokenInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.includes('/api/') || req.headers.has('Authorization')) {
    return next(req);
  }

  const msal = inject(MsalService);
  const cuenta = msal.instance.getActiveAccount() ?? msal.instance.getAllAccounts()[0] ?? null;

  if (!cuenta) {
    console.warn('authTokenInterceptor: sin sesión activa, se envía sin token:', req.url);
    return next(req);
  }

  return from(
    msal.acquireTokenSilent({ scopes: [environment.msal.scope], account: cuenta })
  ).pipe(
    switchMap((resultado) =>
      next(req.clone({ setHeaders: { Authorization: `Bearer ${resultado.accessToken}` } }))
    ),
    catchError((err) => {
      console.error('authTokenInterceptor: no se pudo adquirir el token para', req.url, err);
      return throwError(() => err);
    })
  );
};
