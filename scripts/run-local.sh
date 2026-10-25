#!/usr/bin/env bash
# Levanta en local los tres microservicios con el perfil "local".
#
# Requisitos:
#   1. Cluster RabbitMQ y MySQL arriba:  (cd infra/rabbitmq && docker compose --profile local-db up -d)
#   2. Jars empaquetados:                (cd <servicio> && ./mvnw -DskipTests package)
#   3. infra/rabbitmq/.env creado a partir de .env.example
#
# Uso:  scripts/run-local.sh start | stop
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${LOG_DIR:-$ROOT/.local-logs}"
mkdir -p "$LOG_DIR"

start() {
  set -a; source "$ROOT/infra/rabbitmq/.env"; set +a
  export SPRING_PROFILES_ACTIVE=local
  export SPRING_DATASOURCE_USERNAME=root
  export SPRING_DATASOURCE_PASSWORD="$MYSQL_ROOT_PASSWORD"

  launch() {
    local name="$1" jar="$2"
    nohup java -jar "$jar" > "$LOG_DIR/$name.log" 2>&1 &
    echo $! > "$LOG_DIR/$name.pid"
    echo "$name iniciado (pid $!) -> $LOG_DIR/$name.log"
  }

  launch procesador-service  "$ROOT/procesador-service/target/procesador-service-0.0.1-SNAPSHOT.jar"
  launch rabbit-admin-service "$ROOT/rabbit-admin-service/target/rabbit-admin-service-0.0.1-SNAPSHOT.jar"
  launch backend             "$ROOT/backend/target/backend-0.0.1-SNAPSHOT.jar"
}

stop() {
  for pidfile in "$LOG_DIR"/*.pid; do
    [ -f "$pidfile" ] || continue
    pid="$(cat "$pidfile")"
    if kill "$pid" 2>/dev/null; then echo "detenido $(basename "$pidfile" .pid) (pid $pid)"; fi
    rm -f "$pidfile"
  done
}

case "${1:-start}" in
  start) start ;;
  stop)  stop ;;
  *) echo "Uso: $0 start|stop" >&2; exit 1 ;;
esac
