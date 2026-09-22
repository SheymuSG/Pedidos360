import { bootstrapApplication } from '@angular/platform-browser';
import { PublicClientApplication } from '@azure/msal-browser';
import { buildAppConfig } from './app/app.config';
import { AppComponent } from './app/app.component';
import { msalConfig } from './app/core/msal.config';

// MSAL (desde msal-browser v3+) exige inicializar la instancia y procesar
// la respuesta de redirect ANTES de arrancar la app; si no, el login falla
// con "uninitialized_public_client_application". Se crea una única instancia
// y se reutiliza en app.config.ts para que MsalModule no cree otra sin iniciar.
const msalInstance = new PublicClientApplication(msalConfig);

msalInstance
  .initialize()
  .then(() => msalInstance.handleRedirectPromise())
  .then(() => bootstrapApplication(AppComponent, buildAppConfig(msalInstance)))
  .catch((err) => console.error('Error al inicializar MSAL:', err));
