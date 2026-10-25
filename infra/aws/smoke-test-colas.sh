#!/bin/bash
# Prueba de humo de las colas en la nube SIN necesitar un token de Entra ID.
# Publica en los exchanges (por la API de management de RabbitMQ) los mismos mensajes que el
# backend publica al crear un pedido, y muestra como los procesa el procesador-service.
#
#   RABBITMQ_USERNAME=... RABBITMQ_PASSWORD=... ./smoke-test-colas.sh <IP_PUBLICA_EC2>
#
# El puerto 15672 del security group debe permitir tu IP. Para probar el flujo real
# (frontend -> API Gateway -> backend -> RabbitMQ) hay que iniciar sesion con Entra ID.
set -uo pipefail

IP="${1:?uso: $0 <ip-publica-ec2>}"
: "${RABBITMQ_USERNAME:?defina RABBITMQ_USERNAME}"
: "${RABBITMQ_PASSWORD:?defina RABBITMQ_PASSWORD}"
MGMT="http://$IP:15672"
AUTH="$RABBITMQ_USERNAME:$RABBITMQ_PASSWORD"
BASE="${BASE_ID:-$(date +%s)}"

hr() { printf '\n=================================================================\n%s\n=================================================================\n' "$*"; }

publish() {  # $1=exchange  $2=routing key  $3=payload JSON (string)  $4=messageId
  local body
  body=$(node -e '
    const [rk, payload, id] = process.argv.slice(1);
    process.stdout.write(JSON.stringify({
      properties: { content_type: "application/json", delivery_mode: 2, message_id: id },
      routing_key: rk, payload, payload_encoding: "string" }));' "$2" "$3" "$4")
  printf '  %-26s rk=%-18s -> ' "$1" "$2"
  curl -s -m 10 -u "$AUTH" -H 'content-type: application/json' -X POST \
    "$MGMT/api/exchanges/%2F/$1/publish" -d "$body"
  echo
}

evento() {   # $1=pedidoId $2=cliente $3=descripcion
  node -e 'console.log(JSON.stringify({eventId:process.argv[1]+"-evt",occurredAt:new Date().toISOString(),
    pedidoId:Number(process.argv[1]),cliente:process.argv[2],descripcion:process.argv[3],total:19990}))' "$1" "$2" "$3"
}
comando() {  # $1=pedidoId $2=cliente $3=descripcion
  node -e 'console.log(JSON.stringify({commandId:process.argv[1]+"-cmd",requestedAt:new Date().toISOString(),
    pedidoId:Number(process.argv[1]),cliente:process.argv[2],descripcion:process.argv[3],total:19990,tipoDocumento:"COMPROBANTE"}))' "$1" "$2" "$3"
}

estado() {
  curl -s -m 10 -u "$AUTH" "$MGMT/api/queues/%2F" | node -e '
    let d=""; process.stdin.on("data",c=>d+=c).on("end",()=>{
      const rows = JSON.parse(d).sort((a,b)=>a.name.localeCompare(b.name));
      console.log("COLA".padEnd(26)+"MENSAJES".padEnd(10)+"CONSUMIDORES".padEnd(14)+"NODO");
      for (const q of rows) console.log(q.name.padEnd(26)+String(q.messages).padEnd(10)+String(q.consumers).padEnd(14)+q.node);
    })'
}

hr "Estado inicial"
estado

hr "Publicacion (equivalente a lo que hace el backend al crear pedidos)"
echo "Pedido normal -> evento (topic) + comando (direct):"
publish pedidos.events.exchange pedido.creado "$(evento 1$BASE Ana 'Cafe de especialidad')"          "ok-evt-$BASE"
publish pedidos.tasks.exchange  documento.generar "$(comando 1$BASE Ana 'Cafe de especialidad')"     "ok-cmd-$BASE"
echo "Pedido con #FAIL-TICKET (recuperable que no se resuelve -> 3 intentos -> DLQ):"
publish pedidos.events.exchange pedido.creado "$(evento 2$BASE Bruno 'Servicio caido #FAIL-TICKET')" "fail-evt-$BASE"
echo "Pedido con #INVALID-NOTIFICACION (permanente -> DLQ inmediata):"
publish pedidos.events.exchange pedido.creado "$(evento 3$BASE Carla 'Dato invalido #INVALID-NOTIFICACION')" "inv-evt-$BASE"
echo "Comando con #FAIL-DOCUMENTO (cola direct -> 3 intentos -> DLQ):"
publish pedidos.tasks.exchange  documento.generar "$(comando 4$BASE Diego 'Disco lleno #FAIL-DOCUMENTO')" "fail-cmd-$BASE"
echo "Routing key pedido.desconocido: coincide con el patron topic pedido.* -> solo llega a notificaciones.queue:"
publish pedidos.events.exchange pedido.desconocido "$(evento 5$BASE Elena 'sin ruta')" "noroute-$BASE"

echo; echo "Esperando 15 s (reintentos con backoff 1 s + 2 s)..."
sleep 15

hr "Estado final (mensajes en cada DLQ)"
estado
