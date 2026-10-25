#!/bin/bash
# Arma el bundle de despliegue (docker-compose, rabbitmq.conf, .env y jars) y lo sube a S3.
#
#   RDS_ENDPOINT=... RDS_MASTER_PASSWORD=... ./make-bundle.sh <bucket>
#
# Requisitos: los 3 jars empaquetados (./mvnw -DskipTests package en cada servicio).
# Las claves de RabbitMQ se generan aleatoriamente en cada ejecucion y solo viajan en el bundle.
set -euo pipefail

BUCKET="${1:?uso: $0 <bucket-s3>}"
: "${RDS_ENDPOINT:?defina RDS_ENDPOINT}"
: "${RDS_MASTER_PASSWORD:?defina RDS_MASTER_PASSWORD}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$WORK/jars"
cp "$ROOT/backend/target/backend-0.0.1-SNAPSHOT.jar"                       "$WORK/jars/backend.jar"
cp "$ROOT/procesador-service/target/procesador-service-0.0.1-SNAPSHOT.jar" "$WORK/jars/procesador-service.jar"
cp "$ROOT/rabbit-admin-service/target/rabbit-admin-service-0.0.1-SNAPSHOT.jar" "$WORK/jars/rabbit-admin-service.jar"
cp "$ROOT/infra/aws/docker-compose.aws.yml" "$WORK/docker-compose.yml"
cp "$ROOT/infra/rabbitmq/rabbitmq.conf"     "$WORK/rabbitmq.conf"

cat > "$WORK/.env" <<EOF
RABBITMQ_USERNAME=pedidos360
RABBITMQ_PASSWORD=$(openssl rand -hex 16)
RABBITMQ_ERLANG_COOKIE=$(openssl rand -hex 24)
SPRING_DATASOURCE_URL=jdbc:mysql://$RDS_ENDPOINT:3306/pedidos360_db?createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=admin
SPRING_DATASOURCE_PASSWORD=$RDS_MASTER_PASSWORD
PEDIDOS360_ALLOWED_ORIGINS=${ALLOWED_ORIGINS:-http://localhost:4200}
EOF

( cd "$WORK" && python -m zipfile -c bundle.zip docker-compose.yml rabbitmq.conf .env jars )
aws s3 cp "$WORK/bundle.zip" "s3://$BUCKET/bundle.zip" --region us-east-1

# Copia local de las credenciales de RabbitMQ para las pruebas (no se versiona).
if [ -n "${RABBIT_ENV_OUT:-}" ]; then
  grep -E '^RABBITMQ_' "$WORK/.env" > "$RABBIT_ENV_OUT"
fi
echo "Bundle subido a s3://$BUCKET/bundle.zip"
