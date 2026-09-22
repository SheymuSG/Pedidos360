import { ApplicationConfig, importProvidersFrom, provideZoneChangeDetection } from '@angular/core';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptors, withInterceptorsFromDi } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { MsalInterceptor, MsalModule } from '@azure/msal-angular';
import { IPublicClientApplication, InteractionType } from '@azure/msal-browser';

import { routes } from './app.routes';
import { authTokenInterceptor } from './core/auth-token.interceptor';
import { loginRequest, protectedResourceMap } from './core/msal.config';

/**
 * Recibe la MISMA instancia de PublicClientApplication que main.ts ya
 * inicializó (initialize() + handleRedirectPromise()); MsalModule.forRoot
 * no debe crear una segunda instancia sin inicializar.
 */
export function buildAppConfig(msalInstance: IPublicClientApplication): ApplicationConfig {
  return {
    providers: [
      provideZoneChangeDetection({ eventCoalescing: true }),
      provideRouter(routes),
      provideHttpClient(withInterceptors([authTokenInterceptor]), withInterceptorsFromDi()),
      importProvidersFrom(
        MsalModule.forRoot(
          msalInstance,
          { interactionType: InteractionType.Redirect, authRequest: loginRequest },
          { interactionType: InteractionType.Redirect, protectedResourceMap }
        )
      ),
      { provide: HTTP_INTERCEPTORS, useClass: MsalInterceptor, multi: true },
    ],
  };
}
