#!/bin/bash
# Crea (o actualiza) el API Gateway HTTP "pedidos360-api" delante de la EC2.
#
#   ./api-gateway.sh <IP_PUBLICA_EC2>
#
# - Autorizador JWT de Microsoft Entra ID (issuer v2.0 + audience de Pedidos360-API):
#   el gateway rechaza con 401 cualquier peticion sin token valido.
# - CORS configurado en el gateway (preflight OPTIONS sin autenticacion).
# - Es idempotente: si el API ya existe solo actualiza las IP de las integraciones. Sirve tambien
#   para cuando la EC2 cambia de IP publica tras detener/iniciar la instancia (sin IP elastica).
set -euo pipefail

IP="${1:?uso: $0 <ip-publica-ec2>}"
TENANT_ID="${AZURE_TENANT_ID:-404d6b8a-6f61-411a-98f7-3ae685d26aaa}"
API_CLIENT_ID="${AZURE_API_CLIENT_ID:-49673b38-d477-4cb1-b6c3-01806c189fe0}"
ALLOWED_ORIGINS="${ALLOWED_ORIGINS:-http://localhost:4200}"   # separados por coma
NAME="pedidos360-api"
BACKEND="http://$IP:8080"
ADMIN="http://$IP:8082"

API_ID=$(aws apigatewayv2 get-apis --query "Items[?Name=='$NAME'].ApiId | [0]" --output text)
if [ "$API_ID" = "None" ]; then
  API_ID=$(aws apigatewayv2 create-api --name "$NAME" --protocol-type HTTP --query ApiId --output text)
  echo "API creado: $API_ID"
else
  echo "API existente: $API_ID"
fi

# El propio endpoint del gateway tambien es un origen valido (el frontend se sirve desde alli).
ALLOWED_ORIGINS="$ALLOWED_ORIGINS,https://$API_ID.execute-api.${AWS_DEFAULT_REGION:-us-east-1}.amazonaws.com"
ORIGINS_JSON=$(printf '%s' "$ALLOWED_ORIGINS" | awk -F, '{for(i=1;i<=NF;i++) printf "%s\"%s\"", (i>1?",":""), $i}')
aws apigatewayv2 update-api --api-id "$API_ID" --cors-configuration \
  "{\"AllowOrigins\":[$ORIGINS_JSON],\"AllowMethods\":[\"GET\",\"POST\",\"DELETE\",\"OPTIONS\"],\"AllowHeaders\":[\"authorization\",\"content-type\"],\"MaxAge\":3600}" >/dev/null

AUTH_ID=$(aws apigatewayv2 get-authorizers --api-id "$API_ID" --query "Items[?Name=='entra-jwt'].AuthorizerId | [0]" --output text)
if [ "$AUTH_ID" = "None" ]; then
  AUTH_ID=$(aws apigatewayv2 create-authorizer --api-id "$API_ID" --name entra-jwt --authorizer-type JWT \
    --identity-source '$request.header.Authorization' \
    --jwt-configuration "{\"Audience\":[\"$API_CLIENT_ID\",\"api://$API_CLIENT_ID\"],\"Issuer\":\"https://login.microsoftonline.com/$TENANT_ID/v2.0\"}" \
    --query AuthorizerId --output text)
  echo "Autorizador JWT creado: $AUTH_ID"
fi

upsert_route() {   # $1 = route key, $2 = URI de la integracion
  local key="$1" uri="$2" rid iid
  rid=$(aws apigatewayv2 get-routes --api-id "$API_ID" --query "Items[?RouteKey=='$key'].RouteId | [0]" --output text)
  if [ "$rid" = "None" ]; then
    iid=$(aws apigatewayv2 create-integration --api-id "$API_ID" --integration-type HTTP_PROXY \
      --integration-method ANY --integration-uri "$uri" --payload-format-version 1.0 \
      --query IntegrationId --output text)
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$key" --target "integrations/$iid" \
      --authorization-type JWT --authorizer-id "$AUTH_ID" >/dev/null
    echo "  + $key -> $uri"
  else
    iid=$(aws apigatewayv2 get-route --api-id "$API_ID" --route-id "$rid" --query Target --output text)
    iid="${iid#integrations/}"
    aws apigatewayv2 update-integration --api-id "$API_ID" --integration-id "$iid" --integration-uri "$uri" >/dev/null
    echo "  ~ $key -> $uri"
  fi
}

echo "Rutas:"
upsert_route "ANY /api/pedidos"            "$BACKEND/api/pedidos"
upsert_route "ANY /api/prueba"             "$BACKEND/api/prueba"
upsert_route "ANY /api/queues"             "$ADMIN/api/queues"
upsert_route "ANY /api/queues/{name}"      "$ADMIN/api/queues/{name}"
upsert_route "ANY /api/exchanges"          "$ADMIN/api/exchanges"
upsert_route "ANY /api/exchanges/{name}"   "$ADMIN/api/exchanges/{name}"
upsert_route "ANY /api/bindings"           "$ADMIN/api/bindings"
upsert_route "GET /api/monitoring/queues"  "$ADMIN/api/monitoring/queues"

STAGE=$(aws apigatewayv2 get-stages --api-id "$API_ID" --query "Items[?StageName=='\$default'].StageName | [0]" --output text)
if [ "$STAGE" = "None" ]; then
  aws apigatewayv2 create-stage --api-id "$API_ID" --stage-name '$default' --auto-deploy >/dev/null
  echo "Stage \$default creado (auto-deploy)"
fi

ENDPOINT=$(aws apigatewayv2 get-api --api-id "$API_ID" --query ApiEndpoint --output text)
echo ""
echo "API_ID=$API_ID"
echo "URL=$ENDPOINT"
