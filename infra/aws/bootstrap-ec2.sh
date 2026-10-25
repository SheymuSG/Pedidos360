#!/bin/bash
# User-data de la EC2 (Amazon Linux 2023). Instala Docker + Compose, baja el bundle desde S3
# (docker-compose.yml, rabbitmq.conf, .env, jars) y levanta todo.
# __BUCKET__ se reemplaza al lanzar la instancia (ver infra/aws/README.md).
# El bundle contiene secretos: se elimina de S3 una vez que la instancia arranco.
set -euxo pipefail
exec > >(tee -a /var/log/pedidos360-bootstrap.log) 2>&1

dnf install -y docker unzip
systemctl enable --now docker

mkdir -p /usr/local/lib/docker/cli-plugins
curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

mkdir -p /opt/pedidos360
cd /opt/pedidos360
aws s3 cp "s3://__BUCKET__/bundle.zip" bundle.zip --region us-east-1
unzip -o bundle.zip
rm -f bundle.zip

docker compose up -d
echo "BOOTSTRAP-OK $(date -u +%FT%TZ)"
