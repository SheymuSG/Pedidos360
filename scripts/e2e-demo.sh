#!/usr/bin/env bash
# Demostracion de punta a punta (solo local, perfil "local"). Imprime la evidencia por consola:
#   scripts/e2e-demo.sh | tee docs/evidencias/e2e-demo.txt
#
# Requiere: cluster RabbitMQ + MySQL arriba y los 3 servicios corriendo (scripts/run-local.sh start).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
set -a; source "$ROOT/infra/rabbitmq/.env"; set +a

BACKEND="${BACKEND_URL:-http://localhost:8080}"
ADMIN="${ADMIN_URL:-http://localhost:8082}"
MGMT="${MGMT_URL:-http://localhost:15672}"
CLIENTE_TOKEN="$(node "$ROOT/scripts/dev-token.mjs" Cliente)"
ADMIN_TOKEN="$(node "$ROOT/scripts/dev-token.mjs" Admin)"
BROKER_AUTH="$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD"

hr()   { printf '\n=================================================================\n%s\n=================================================================\n' "$*"; }
step() { printf '\n--- %s\n' "$*"; }
pause() { sleep "${1:-6}"; }   # la API de management actualiza sus contadores cada ~5 s

# curl que muestra "HTTP <codigo>" y el cuerpo.
call() {
  local body_file; body_file="$(mktemp)"
  local code; code="$(curl -s -o "$body_file" -w '%{http_code}' "$@")"
  printf 'HTTP %s  %s\n' "$code" "$(cat "$body_file")"
  rm -f "$body_file"
}

queues() {
  curl -s -u "$BROKER_AUTH" "$MGMT/api/queues/%2F" | node -e '
    let d=""; process.stdin.on("data",c=>d+=c).on("end",()=>{
      const rows = JSON.parse(d)
        .filter(q => /^(notificaciones|tickets|documentos|reportes)/.test(q.name))
        .sort((a,b) => a.name.localeCompare(b.name));
      console.log("COLA".padEnd(28) + "MENSAJES".padEnd(10) + "CONSUMIDORES".padEnd(14) + "NODO");
      for (const q of rows)
        console.log(q.name.padEnd(28) + String(q.messages).padEnd(10) + String(q.consumers).padEnd(14) + q.node);
    })'
}

topology() {
  step "Exchanges"
  curl -s -u "$BROKER_AUTH" "$MGMT/api/exchanges/%2F" | node -e '
    let d=""; process.stdin.on("data",c=>d+=c).on("end",()=>{
      for (const e of JSON.parse(d).filter(e => e.name.startsWith("pedidos")))
        console.log(" ", e.name.padEnd(28), "tipo=" + e.type.padEnd(8), "durable=" + e.durable);
    })'
  step "Bindings"
  curl -s -u "$BROKER_AUTH" "$MGMT/api/bindings/%2F" | node -e '
    let d=""; process.stdin.on("data",c=>d+=c).on("end",()=>{
      for (const b of JSON.parse(d).filter(b => b.source.startsWith("pedidos")))
        console.log(" ", b.source.padEnd(28), "--[" + b.routing_key + "]-->", b.destination);
    })'
  step "Colas (argumentos de DLX / retencion)"
  curl -s -u "$BROKER_AUTH" "$MGMT/api/queues/%2F" | node -e '
    let d=""; process.stdin.on("data",c=>d+=c).on("end",()=>{
      const rows = JSON.parse(d).filter(q => /^(notificaciones|tickets|documentos)/.test(q.name))
        .sort((a,b) => a.name.localeCompare(b.name));
      for (const q of rows) console.log(" ", q.name.padEnd(24), "durable=" + q.durable, JSON.stringify(q.arguments));
    })'
}

dlq_peek() {  # $1 = nombre de la DLQ. Lee 1 mensaje y lo deja en la cola (ack_requeue_true).
  curl -s -u "$BROKER_AUTH" -H 'content-type: application/json' \
    -X POST "$MGMT/api/queues/%2F/$1/get" \
    -d '{"count":1,"ackmode":"ack_requeue_true","encoding":"auto","truncate":300}' | node -e '
    let d=""; process.stdin.on("data",c=>d+=c).on("end",()=>{
      const m = JSON.parse(d)[0];
      if (!m) { console.log("  (sin mensajes)"); return; }
      const death = (m.properties.headers || {})["x-death"]?.[0] || {};
      console.log("  payload :", m.payload);
      console.log("  x-death : cola origen=" + death.queue + " razon=" + death.reason +
                  " exchange=" + death.exchange + " routing-keys=" + JSON.stringify(death["routing-keys"]));
    })'
}

nuevo_pedido() {  # $1 cliente, $2 descripcion
  call -X POST "$BACKEND/api/pedidos" \
    -H "Authorization: Bearer $CLIENTE_TOKEN" -H 'Content-Type: application/json' \
    -d "{\"cliente\":\"$1\",\"descripcion\":\"$2\",\"total\":19990}"
}

# ---------------------------------------------------------------------------
hr "0. TOPOLOGIA declarada por procesador-service en el cluster"
topology

hr "1. SEGURIDAD del backend: sin token y con rol equivocado"
step "POST /api/pedidos sin Authorization -> 401"
call -X POST "$BACKEND/api/pedidos" -H 'Content-Type: application/json' -d '{"cliente":"x","descripcion":"y","total":1}'
step "POST /api/pedidos con token de rol Admin (se exige Cliente) -> 403"
call -X POST "$BACKEND/api/pedidos" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"cliente":"x","descripcion":"y","total":1}'

hr "2. FLUJO FELIZ: un pedido genera notificacion, ticket y documento"
nuevo_pedido "Ana Perez" "Cafe de especialidad 1kg"
pause 4

hr "3. REINTENTO que se recupera (#FLAKY-TICKET: falla 2 veces, la 3a funciona)"
nuevo_pedido "Bruno Diaz" "Pedido con falla transitoria #FLAKY-TICKET"
pause 8

hr "4. REINTENTOS AGOTADOS -> DLQ (#FAIL-TICKET: 3 intentos con backoff 1s, 2s)"
nuevo_pedido "Carla Soto" "Pedido con servicio caido #FAIL-TICKET"
pause 10

hr "5. ERROR PERMANENTE -> DLQ sin reintentos (#INVALID-NOTIFICACION)"
nuevo_pedido "Diego Mora" "Pedido con dato invalido #INVALID-NOTIFICACION"
pause 5

hr "6. ERROR EN LA COLA DIRECT -> DLQ (#FAIL-DOCUMENTO)"
nuevo_pedido "Elena Rojas" "Pedido con disco lleno #FAIL-DOCUMENTO"
pause 10

hr "7. ESTADO de colas tras las pruebas (mensajes en cada DLQ)"
queues
step "Contenido de tickets.dlq"
dlq_peek tickets.dlq
step "Contenido de notificaciones.dlq"
dlq_peek notificaciones.dlq
step "Contenido de documentos.dlq"
dlq_peek documentos.dlq

hr "8. ALERTA de DLQ via microservicio administrador"
call "$ADMIN/api/monitoring/queues" -H "Authorization: Bearer $CLIENTE_TOKEN"

hr "9. ADMINISTRACION: crear / consultar / enlazar / eliminar"
step "POST /api/queues (sin token) -> 401"
call -X POST "$ADMIN/api/queues" -H 'Content-Type: application/json' -d '{"name":"reportes.queue"}'
step "POST /api/queues con rol Cliente -> 403"
call -X POST "$ADMIN/api/queues" -H "Authorization: Bearer $CLIENTE_TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"reportes.queue"}'
step "POST /api/queues nombre vacio -> 400"
call -X POST "$ADMIN/api/queues" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"  "}'
step "POST /api/queues nombre con prefijo reservado amq. -> 400"
call -X POST "$ADMIN/api/queues" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"amq.mia"}'
step "POST /api/queues TTL negativo -> 400"
call -X POST "$ADMIN/api/queues" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"reportes.queue","messageTtlMs":-1}'
step "POST /api/queues valida (con DLX + TTL) -> 201"
call -X POST "$ADMIN/api/queues" -H "Authorization: Bearer $ADMIN_TOKEN" -H 'Content-Type: application/json' \
     -d '{"name":"reportes.queue","deadLetterExchange":"pedidos.dlx","deadLetterRoutingKey":"tickets.dead","messageTtlMs":60000,"maxLength":500}'
step "POST /api/queues repetida -> 409"
call -X POST "$ADMIN/api/queues" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"reportes.queue"}'
step "POST /api/exchanges (topic) -> 201"
call -X POST "$ADMIN/api/exchanges" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"reportes.topic","type":"TOPIC"}'
step "POST /api/exchanges tipo inexistente -> 400"
call -X POST "$ADMIN/api/exchanges" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"name":"reportes.x","type":"inventado"}'
step "POST /api/bindings -> 201"
call -X POST "$ADMIN/api/bindings" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"exchange":"reportes.topic","queue":"reportes.queue","routingKey":"reporte.*"}'
step "POST /api/bindings a una cola inexistente -> 404"
call -X POST "$ADMIN/api/bindings" -H "Authorization: Bearer $ADMIN_TOKEN" \
     -H 'Content-Type: application/json' -d '{"exchange":"reportes.topic","queue":"no.existe","routingKey":"a"}'
step "GET /api/queues/reportes.queue -> 200"
call "$ADMIN/api/queues/reportes.queue" -H "Authorization: Bearer $CLIENTE_TOKEN"
step "DELETE /api/bindings -> 204"
call -X DELETE "$ADMIN/api/bindings?exchange=reportes.topic&queue=reportes.queue&routingKey=reporte.*" \
     -H "Authorization: Bearer $ADMIN_TOKEN"
step "DELETE /api/exchanges/reportes.topic -> 204"
call -X DELETE "$ADMIN/api/exchanges/reportes.topic" -H "Authorization: Bearer $ADMIN_TOKEN"
step "DELETE /api/queues/reportes.queue -> 204"
call -X DELETE "$ADMIN/api/queues/reportes.queue" -H "Authorization: Bearer $ADMIN_TOKEN"
step "DELETE /api/queues/reportes.queue otra vez -> 404"
call -X DELETE "$ADMIN/api/queues/reportes.queue" -H "Authorization: Bearer $ADMIN_TOKEN"

hr "10. CLUSTER: nodos activos"
curl -s -u "$BROKER_AUTH" "$MGMT/api/nodes" | node -e '
  let d=""; process.stdin.on("data",c=>d+=c).on("end",()=>{
    for (const n of JSON.parse(d))
      console.log(" ", n.name.padEnd(18), "running=" + n.running, "mem_used=" + Math.round(n.mem_used/1048576) + "MB");
  })'
