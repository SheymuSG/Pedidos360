# Despliegue en AWS (Learner Lab) - Pedidos360

Infraestructura mínima para la Evaluación 2. **Sin IP elástica**: la IP pública de la EC2 cambia al
detener/iniciar la instancia (ver "Si la EC2 cambia de IP").

## Qué hay desplegado

| Recurso | Detalle |
|---|---|
| EC2 `pedidos360-ec2` | `t3.medium`, Amazon Linux 2023, 30 GB, perfil `LabInstanceProfile`, key pair `vockey` |
| Contenedores en la EC2 | `rabbit1`, `rabbit2` (clúster RabbitMQ 4.2), `procesador`, `rabbit-admin`, `backend` (ver `docker-compose.aws.yml`) |
| RDS `pedidos360-db` | MySQL 8.0, `db.t3.micro`, 20 GB, no público; solo accesible desde el security group de la EC2 |
| API Gateway HTTP `pedidos360-api` | Autorizador JWT de Entra ID, CORS, rutas hacia el backend y el administrador |
| S3 `pedidos360-deploy-<cuenta>` | Bucket **privado**: transferencia del bundle (se elimina tras el arranque) y archivos del frontend (`web/`) |
| Lambda `pedidos360-web` | Sirve el frontend Angular desde el bucket privado, detrás del API Gateway (HTTPS, sin puertos ni buckets públicos) |

Evidencias en `docs/evidencias/aws/` (inventario, flujo de colas, logs y capturas de la consola de RabbitMQ).

## Puertos (security group de la EC2)

| Puerto | Origen | Para qué |
|---|---|---|
| 22 | tu IP /32 | SSH (opcional: hay acceso por SSM) |
| 8080 | 0.0.0.0/0 | backend (exige JWT; lo usa el API Gateway) |
| 15672-15673 | tu IP /32 | consola de RabbitMQ de cada nodo |
| 8082 | **cerrado** | administrador de RabbitMQ (ver "Pendiente") |
| 5672 | cerrado | AMQP: solo entre contenedores de la EC2 |

## Credenciales

`infra/aws/.env.cloud` (ignorado por git) trae el usuario/clave de RabbitMQ y de la base de datos generadas al desplegar.
También están en `/opt/pedidos360/.env` dentro de la EC2.

## Repetir el despliegue desde cero

```bash
# 0. Credenciales del lab en el entorno (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN)
# 1. Red + RDS (usa key pair existente, p. ej. vockey)
export RDS_MASTER_PASSWORD='<clave>' && ./phase1-rds-ec2-keypair.sh
# 2. Jars
for s in backend procesador-service rabbit-admin-service; do (cd ../../$s && ./mvnw -DskipTests package); done
# 3. Bundle -> S3 (bucket privado)
RDS_ENDPOINT=<endpoint> RDS_MASTER_PASSWORD=<clave> ./make-bundle.sh <bucket>
# 4. Lanzar la EC2 con bootstrap-ec2.sh como user-data (reemplazando __BUCKET__)
# 5. API Gateway
./api-gateway.sh <IP_PUBLICA_EC2>
# 6. Frontend (requiere 'ng build' previo): S3 privado + Lambda detras del gateway
./deploy-frontend.sh <bucket>
# 6b. CORS: autorizar el dominio del gateway en backend y administrador (OBLIGATORIO, si no
#     crear pedidos falla con 403 "Invalid CORS request")
./set-allowed-origins.sh <instance-id> "http://localhost:4200,https://<api-id>.execute-api.us-east-1.amazonaws.com"
# 7. Prueba de humo de las colas (no requiere token)
RABBITMQ_USERNAME=... RABBITMQ_PASSWORD=... ./smoke-test-colas.sh <IP_PUBLICA_EC2>
```

## Si la EC2 cambia de IP

Al detener/iniciar la instancia cambia la IP pública. Los contenedores se reinician solos
(`restart: unless-stopped`), pero hay que actualizar las integraciones del API Gateway:

```bash
IP=$(aws ec2 describe-instances --instance-ids <id> --query "Reservations[0].Instances[0].PublicIpAddress" --output text)
./api-gateway.sh $IP
```

Si además cambió tu IP de casa, actualiza en el security group la regla de 15672-15673 (y 22).

## Ahorrar créditos

Cuando no estés trabajando: detener la EC2 y la RDS (`aws ec2 stop-instances`, `aws rds stop-db-instance`).
Learner Lab detiene los recursos al terminar la sesión; RDS vuelve a arrancar sola a los 7 días.

## Estado del administrador de RabbitMQ (puerto 8082)

El servicio corre (`/actuator/health` = UP dentro de la EC2) y las rutas `/api/queues`, `/api/exchanges`, `/api/bindings`
y `/api/monitoring` existen en el gateway, **pero el puerto 8082 está cerrado en el security group**, así que el
gateway no puede alcanzarlo. Abrirlo a internet lo expone igual que el 8080 (el servicio exige JWT y rol Admin):

```bash
aws ec2 authorize-security-group-ingress --group-id <EC2_SG_ID> --protocol tcp --port 8082 --cidr 0.0.0.0/0
```

Mientras esté cerrado, la vista `/mensajeria` del frontend no recibe datos. Para la presentación no es necesario:
las colas, DLQ y exchanges se muestran en la consola de RabbitMQ.

## Frontend

URL: la del API Gateway (`https://<api-id>.execute-api.us-east-1.amazonaws.com`). Para iniciar sesión hay que
registrar esa URL como *redirect URI* de tipo **SPA** en la app `Pedidos360-Web` de Entra ID (Entra exige HTTPS fuera
de localhost, por eso el frontend se sirve por el gateway y no por un sitio web S3 en HTTP).

## Error 403 "Invalid CORS request" al crear pedidos

Si el frontend se sirve desde el API Gateway, el navegador envía el header `Origin` en los POST y el backend
(Spring) responde `403 Invalid CORS request` cuando ese dominio no está en `PEDIDOS360_ALLOWED_ORIGINS`
(por defecto solo `http://localhost:4200`). Los GET no lo envían, por eso listar pedidos funciona y crearlos no.
Se corrige con `./set-allowed-origins.sh` (ver arriba). Al repetir el despliegue desde cero hay que ejecutarlo
después de `api-gateway.sh`, cuando ya se conoce la URL del gateway.
