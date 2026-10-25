#!/usr/bin/env node
// SOLO DESARROLLO LOCAL. Firma un JWT HS256 que aceptan los servicios cuando se ejecutan con
// el perfil "local" (SPRING_PROFILES_ACTIVE=local). No sirve contra Entra ID ni en AWS.
//
//   LOCAL_JWT_SECRET=<mismo secreto que los servicios> node scripts/dev-token.mjs Cliente
//   LOCAL_JWT_SECRET=... node scripts/dev-token.mjs Admin
//   LOCAL_JWT_SECRET=... node scripts/dev-token.mjs Cliente,Admin
//
// Argumento: roles separados por coma (claim "roles", igual que los app roles de Entra ID).
import { createHmac } from 'node:crypto';

const secret = process.env.LOCAL_JWT_SECRET;
if (!secret || secret.length < 32) {
  console.error('Defina LOCAL_JWT_SECRET (minimo 32 caracteres), el mismo que usan los servicios.');
  process.exit(1);
}

const tenant = process.env.AZURE_TENANT_ID ?? '404d6b8a-6f61-411a-98f7-3ae685d26aaa';
const issuer = `https://login.microsoftonline.com/${tenant}/v2.0`;
const audience = process.env.AZURE_API_AUDIENCE ?? 'api://49673b38-d477-4cb1-b6c3-01806c189fe0';
const roles = (process.argv[2] ?? 'Cliente').split(',').map((r) => r.trim()).filter(Boolean);
const ttlSeconds = Number(process.env.TOKEN_TTL_SECONDS ?? 3600);

const b64 = (obj) => Buffer.from(JSON.stringify(obj)).toString('base64url');
const now = Math.floor(Date.now() / 1000);
const header = { alg: 'HS256', typ: 'JWT' };
const payload = {
  iss: issuer,
  aud: audience,
  sub: 'dev-user',
  preferred_username: 'dev.local@pedidos360.test',
  roles,
  iat: now,
  nbf: now,
  exp: now + ttlSeconds,
};

const unsigned = `${b64(header)}.${b64(payload)}`;
const signature = createHmac('sha256', secret).update(unsigned).digest('base64url');
process.stdout.write(`${unsigned}.${signature}\n`);
