#!/bin/bash
# Autoriza (CORS) los dominios desde los que el navegador puede llamar al backend y al administrador.
# Hace falta cuando el frontend se sirve desde el API Gateway: el navegador envia el header Origin en
# los POST/DELETE y Spring responde "403 Invalid CORS request" si el dominio no esta en la lista.
#
#   ./set-allowed-origins.sh <instance-id> <origen1,origen2,...>
#   ./set-allowed-origins.sh i-0abc... "http://localhost:4200,https://<api-id>.execute-api.us-east-1.amazonaws.com"
#
# Actua por SSM (no requiere SSH): actualiza /opt/pedidos360/.env y recrea backend y rabbit-admin.
set -euo pipefail

INSTANCE_ID="${1:?uso: $0 <instance-id> <origenes-separados-por-coma>}"
ORIGINS="${2:?uso: $0 <instance-id> <origenes-separados-por-coma>}"
case "$ORIGINS" in *" "*) echo "Los origenes no deben llevar espacios" >&2; exit 1;; esac

PARAMS=$(ORIGINS="$ORIGINS" node -e '
  const o = process.env.ORIGINS;
  console.log(JSON.stringify({ commands: [
    "cd /opt/pedidos360",
    // el compose ya instalado puede ser anterior: asegura que pase la variable a los contenedores
    "grep -q PEDIDOS360_ALLOWED_ORIGINS docker-compose.yml || sudo sed -i \"/JAVA_TOOL_OPTIONS:/a\\\\    PEDIDOS360_ALLOWED_ORIGINS: \\${PEDIDOS360_ALLOWED_ORIGINS:-http://localhost:4200}\" docker-compose.yml",
    "sudo sed -i \"/^PEDIDOS360_ALLOWED_ORIGINS=/d\" .env",
    "echo \"PEDIDOS360_ALLOWED_ORIGINS=" + o + "\" | sudo tee -a .env >/dev/null",
    "sudo docker compose up -d backend rabbit-admin 2>&1 | tail -3"
  ]}));')

CMD=$(aws ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript \
  --parameters "$PARAMS" --query Command.CommandId --output text)
until st=$(aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null); \
      [ "$st" = "Success" ] || [ "$st" = "Failed" ]; do sleep 3; done
aws ssm get-command-invocation --command-id "$CMD" --instance-id "$INSTANCE_ID" \
  --query "[StandardOutputContent,StandardErrorContent]" --output text
echo "Estado: $st. El backend tarda ~1 minuto en volver a responder."
