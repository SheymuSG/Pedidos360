#!/bin/bash
# Publica el frontend Angular: sube el build a S3 (bucket privado) y lo sirve por el API Gateway
# mediante la Lambda "pedidos360-web". No abre puertos ni hace publico ningun bucket.
#
#   ./deploy-frontend.sh [bucket]        (requiere: ng build previo y ./api-gateway.sh ejecutado)
#
# Despues hay que registrar la URL del API Gateway como redirect URI (tipo SPA) de la app
# Pedidos360-Web en Entra ID.
set -euo pipefail

BUCKET="${1:-pedidos360-deploy-774860322169}"
FUNCTION="pedidos360-web"
ROLE_ARN="${LAB_ROLE_ARN:-arn:aws:iam::774860322169:role/LabRole}"
HERE="$(cd "$(dirname "$0")" && pwd)"
DIST="$HERE/../../frontend-pedidos360/dist/pedidos360-web/browser"
[ -f "$DIST/index.html" ] || { echo "No hay build: ejecute 'ng build' en frontend-pedidos360"; exit 1; }

API_ID=$(aws apigatewayv2 get-apis --query "Items[?Name=='pedidos360-api'].ApiId | [0]" --output text)
[ "$API_ID" != "None" ] || { echo "No existe el API Gateway: ejecute ./api-gateway.sh primero"; exit 1; }
ACCOUNT=$(aws sts get-caller-identity --query Account --output text)
REGION="${AWS_DEFAULT_REGION:-us-east-1}"

echo "1. Subiendo el build a s3://$BUCKET/web/ ..."
aws s3 sync "$DIST" "s3://$BUCKET/web/" --delete --only-show-errors

echo "2. Empaquetando y desplegando la Lambda $FUNCTION ..."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
( cd "$HERE/web-lambda" && python -m zipfile -c "$TMP/web.zip" app.py )
ZIP_ARG="fileb://$(cd "$TMP" && pwd -W 2>/dev/null || pwd)/web.zip"
if aws lambda get-function --function-name "$FUNCTION" >/dev/null 2>&1; then
  aws lambda update-function-code --function-name "$FUNCTION" --zip-file "$ZIP_ARG" >/dev/null
else
  aws lambda create-function --function-name "$FUNCTION" --runtime python3.12 --handler app.handler \
    --role "$ROLE_ARN" --zip-file "$ZIP_ARG" --timeout 10 --memory-size 128 \
    --environment "Variables={BUCKET=$BUCKET,PREFIX=web/}" >/dev/null
  aws lambda wait function-active --function-name "$FUNCTION"
fi

echo "3. Conectando rutas GET / y GET /{proxy+} (sin autorizador: es contenido estatico) ..."
LAMBDA_ARN="arn:aws:lambda:$REGION:$ACCOUNT:function:$FUNCTION"
INT_ID=$(aws apigatewayv2 get-integrations --api-id "$API_ID" --query "Items[?IntegrationUri=='$LAMBDA_ARN'].IntegrationId | [0]" --output text)
if [ "$INT_ID" = "None" ]; then
  INT_ID=$(aws apigatewayv2 create-integration --api-id "$API_ID" --integration-type AWS_PROXY \
    --integration-uri "$LAMBDA_ARN" --payload-format-version 2.0 --query IntegrationId --output text)
fi
for KEY in 'GET /' 'GET /{proxy+}'; do
  RID=$(aws apigatewayv2 get-routes --api-id "$API_ID" --query "Items[?RouteKey=='$KEY'].RouteId | [0]" --output text)
  if [ "$RID" = "None" ]; then
    aws apigatewayv2 create-route --api-id "$API_ID" --route-key "$KEY" --target "integrations/$INT_ID" \
      --authorization-type NONE >/dev/null
    echo "  + $KEY"
  fi
done
aws lambda add-permission --function-name "$FUNCTION" --statement-id apigw-invoke \
  --action lambda:InvokeFunction --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$REGION:$ACCOUNT:$API_ID/*/*/*" >/dev/null 2>&1 || true

URL=$(aws apigatewayv2 get-api --api-id "$API_ID" --query ApiEndpoint --output text)
echo ""
echo "Frontend publicado en: $URL"
echo "Registre esa URL como redirect URI (SPA) en la app Pedidos360-Web de Entra ID."
