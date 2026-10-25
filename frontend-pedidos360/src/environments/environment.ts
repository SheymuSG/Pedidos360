export const environment = {
  production: false,
  // El backend ya no corre en local: apunta directo al API Gateway desplegado en AWS.
  apiBaseUrl: 'https://6iqkstpi9c.execute-api.us-east-1.amazonaws.com',
  // Microservicio administrador de RabbitMQ (rabbit-admin-service). En desarrollo se llama
  // directo al puerto local; en produccion debe salir por el API Gateway (ver environment.prod.ts).
  adminApiBaseUrl: 'http://localhost:8082',
  msal: {
    clientId: 'dae98be9-c519-4d2e-870b-6b9899734902',
    authority: 'https://login.microsoftonline.com/404d6b8a-6f61-411a-98f7-3ae685d26aaa',
    redirectUri: window.location.origin,
    postLogoutRedirectUri: window.location.origin,
    scope: 'api://49673b38-d477-4cb1-b6c3-01806c189fe0/access_as_user',
  },
};
