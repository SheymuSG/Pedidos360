export const environment = {
  production: false,
  /**
   * Vacío = misma-origen: en desarrollo el proxy de Angular (proxy.conf.json)
   * envía /api al backend Spring Boot. En producción, apuntar a la URL
   * pública del API Gateway (AWS) que protege al backend.
   */
  apiBaseUrl: '',
  msal: {
    // Reemplazar por el Client ID del registro "Pedidos360-Web" (SPA) en Entra ID.
    clientId: 'REEMPLAZAR_CLIENT_ID_SPA',
    // Reemplazar <TENANT_ID> por el "Directory (tenant) ID" del portal.
    authority: 'https://login.microsoftonline.com/404d6b8a-6f61-411a-98f7-3ae685d26aaa',
    redirectUri: window.location.origin,
    postLogoutRedirectUri: window.location.origin,
    // Reemplazar por el Application ID URI del registro "Pedidos360-API" + /access_as_user.
    scope: 'api://49673b38-d477-4cb1-b6c3-01806c189fe0/access_as_user',
  },
};
