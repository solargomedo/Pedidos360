# Despliegue Pedidos360 EP3 en Ubuntu EC2

El equipo reportó EP3 desplegada y probada en una EC2 Ubuntu t3.large. El host ejecuta Docker Compose con tres ZooKeeper, tres brokers Kafka, Kafka UI, RabbitMQ de dos nodos y PostgreSQL con bases distintas para Catalog y Orders. Este runbook sirve para reproducir o revisar la operación después de autorizarla. Los bloques de EC2 se ejecutan en Bash desde la raíz de una copia que contenga los cambios aprobados.

## 1. Inventario y condiciones de arranque

```bash
docker compose version
docker compose ls --all
docker ps -a --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker network ls
docker volume ls
uname -m
nproc
free -h
df -h "$(docker info --format '{{.DockerRootDir}}')"
```

Confirmar el proyecto y los archivos Compose vigentes de Orders y BFF, sus nombres de servicio, la red de Catalog y RabbitMQ, el host y vhost AMQP, la base, el esquema y el rol JDBC real de Orders, los volúmenes existentes y el destino del Gateway. Para un contenedor conocido, consultar sus etiquetas Compose sin imprimir su entorno.

```bash
read -rp 'Contenedor de aplicación existente: ' APP_CONTAINER
docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}} {{index .Config.Labels "com.docker.compose.project.config_files"}}' "$APP_CONTAINER"
docker inspect --format '{{range $name, $_ := .NetworkSettings.Networks}}{{println $name}}{{end}}' "$APP_CONTAINER"
```

`infra/aws/compose.yaml` es el entorno local de integración. Declara un broker separado y deshabilita los listeners AMQP de Orders. `infra/rabbitmq/docker-compose.yml` tampoco representa el clúster de dos nodos y publica puertos AMQP y Management en todas las interfaces. Usar la configuración real de EC2 para la aplicación. El complemento supone que Orders declara explícitamente sus redes en el Compose base. Si depende de la red implícita `default`, declararla explícitamente en su base antes de combinar.

Si Kafka ya existe, confirmar su proyecto, sus volúmenes, sus tres nombres DNS internos y las asignaciones de puertos antes de usar los comandos. Si EC2 tenía IP pública dinámica, verificar el destino actual de Gateway o del proxy existente.

Para reanudar las dependencias conservando los contenedores, iniciar solo los que el inventario confirme detenidos. Los nombres pueden variar y los nodos RabbitMQ deben pertenecer al clúster existente. Comprobar PostgreSQL, RabbitMQ y Catalog con sus healthchecks o su procedimiento operativo antes de continuar.

```bash
read -rp 'Nombres de contenedores existentes y detenidos, separados por espacios: ' -a stopped
if (("${#stopped[@]}" > 0)); then
  docker start "${stopped[@]}"
fi
```

## 2. Construir las imágenes

Desde la raíz en un equipo Docker con la arquitectura de EC2, construir con el directorio de cada módulo como contexto. Si se construye fuera de EC2, transferir el archivo por el canal operativo autorizado y ejecutar `docker load` en EC2.

```bash
docker build -t pedidos360-orders:ep3 ./ms-pedidos360-orders
docker build -t pedidos360-bff:ep3 ./ms-pedidos360-bff

# Solo para transferir las imágenes a otro host.
docker save -o pedidos360-ep3-images.tar pedidos360-orders:ep3 pedidos360-bff:ep3
# En EC2, después de transferir el archivo:
docker load -i pedidos360-ep3-images.tar
```

Ambos Dockerfiles construyen con Maven y Java 17 y ejecutan el JAR como usuario sin privilegios. La fase Docker omite pruebas. Angular se entrega por su canal existente y su configuración de producción conserva API Gateway.

## 3. Preparar Kafka

Para una instalación nueva con este Compose, el proyecto predeterminado es `pedidos360-kafka`. Para reutilizar un despliegue existente, introducir el proyecto real y comprobar que el archivo de Kafka coincide con el despliegue y los volúmenes antes de continuar.

```bash
read -rp 'Proyecto Compose real de Kafka [pedidos360-kafka]: ' KAFKA_PROJECT
KAFKA_PROJECT=${KAFKA_PROJECT:-pedidos360-kafka}
kafka=(docker compose -p "$KAFKA_PROJECT" -f infra/kafka/docker-compose.yml)

"${kafka[@]}" config --quiet
"${kafka[@]}" up -d --no-recreate --wait --wait-timeout 300
"${kafka[@]}" ps

export KAFKA_DOCKER_NETWORK="${KAFKA_PROJECT}_kafka"
docker network inspect --format '{{.Name}} {{.Driver}}' "$KAFKA_DOCKER_NETWORK"

broker=("${kafka[@]}" exec -T kafka-1)
bootstrap=kafka-1:9092,kafka-2:9092,kafka-3:9092
```

Si el inventario muestra otro nombre físico de red, asignarlo a `KAFKA_DOCKER_NETWORK` y confirmar que pertenece al clúster seleccionado. Crear los tópicos solo si faltan. La descripción permite detectar una configuración existente incompatible sin alterarla.

```bash
for topic in pedidos.eventos pedidos.analitica; do
  "${broker[@]}" kafka-topics --bootstrap-server "$bootstrap" --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 3 \
    --config min.insync.replicas=2 --config cleanup.policy=delete \
    --config retention.ms=604800000 --config retention.bytes=268435456 \
    --config segment.ms=3600000 --config segment.bytes=67108864 || break
  "${broker[@]}" kafka-topics --bootstrap-server "$bootstrap" --describe --topic "$topic" || break
  "${broker[@]}" kafka-configs --bootstrap-server "$bootstrap" --entity-type topics \
    --entity-name "$topic" --describe || break
done
```

Avanzar cuando ambos tópicos estén saludables, tengan tres particiones con tres réplicas y el ISR esperado. El script local `create-topics.sh` reaplica políticas de retención a tópicos existentes, por lo que el procedimiento de EC2 usa los comandos anteriores.

## 4. Aplicar el SQL sobre PostgreSQL existente

Confirmar respaldo, base objetivo, esquema en el `search_path` del JDBC y cuenta con autoridad para crear tablas y conceder permisos. El ejemplo usa un PostgreSQL existente en un contenedor con autenticación local configurada. Para PostgreSQL externo, usar su cliente `psql` y los parámetros de conexión ya autorizados. Si las tablas ya existen, verificar su estructura antes de aplicar el script idempotente.

```bash
read -rp 'Contenedor PostgreSQL existente: ' PG_CONTAINER
read -rp 'Usuario administrador PostgreSQL existente: ' PG_ADMIN
read -rp 'Base existente de Orders: ' ORDERS_DB
read -rp 'Esquema efectivo de Orders: ' ORDERS_SCHEMA
read -rp 'Rol JDBC existente de Orders: ' ORDERS_ROLE

ddl=ms-pedidos360-orders/src/main/resources/db/manual/V001__proyecciones_kafka.sql
grants=infra/aws/postgres/grant-orders-projections.sql

test -n "$PG_CONTAINER" && test -n "$PG_ADMIN" && test -n "$ORDERS_DB" &&
test -n "$ORDERS_SCHEMA" && test -n "$ORDERS_ROLE" &&
test -r "$ddl" && test -r "$grants" && (
  set -o pipefail
  {
    printf '%s\n' 'SET LOCAL search_path TO :"orders_schema";'
    cat "$ddl" "$grants"
  } | docker exec -i "$PG_CONTAINER" psql -X -v ON_ERROR_STOP=1 \
    -v "orders_schema=$ORDERS_SCHEMA" -v "orders_role=$ORDERS_ROLE" \
    -U "$PG_ADMIN" -d "$ORDERS_DB" --single-transaction --file=-
)
```

Continuar solo al confirmar `COMMIT`. Se crean las dos tablas si faltan y se conceden `USAGE` sobre el esquema y `SELECT`, `INSERT`, `UPDATE` sobre ambas tablas. Si falla alguna sentencia, la transacción revierte. Mantener el mecanismo de autenticación existente al ejecutar `psql`.

## 5. Activar Orders y BFF según el inventario

Usar el proyecto y la cadena real de archivos Compose en su orden original, seguida de `infra/aws/compose.kafka.yaml`. El siguiente bloque supone que el archivo efectivo principal usa los servicios `orders` y `bff` y que el entorno vigente se carga desde un archivo existente. Si hay más archivos, añadir sus opciones `-f` antes del complemento. Confirmar la fuente de variables y el directorio del proyecto usados por el despliegue vigente.

```bash
read -rp 'Proyecto Compose real de aplicación: ' APP_PROJECT
read -rp 'Ruta al Compose base real: ' APP_COMPOSE
read -rp 'Ruta al archivo de entorno existente: ' APP_ENV
app=(docker compose -p "$APP_PROJECT" --env-file "$APP_ENV" \
  -f "$APP_COMPOSE" -f infra/aws/compose.kafka.yaml)

export KAFKA_SEGUIMIENTO_ENABLED=true
export KAFKA_EVENTOS_GROUP=pedidos360-orders-eventos
export KAFKA_ANALITICA_GROUP=pedidos360-orders-analitica-proyeccion-v1
export KAFKA_SEGUIMIENTO_GROUP=pedidos360-orders-seguimiento

"${app[@]}" config --quiet
"${app[@]}" config --format json | python3 -c 'import json, sys; s = json.load(sys.stdin)["services"]; print(*(s[k]["image"] for k in ("orders", "bff")), sep="\n")'
"${app[@]}" ps --all orders bff
```

Usar los IDs de grupo del inventario si la proyección ya se inicializó. El valor nuevo de analítica se aplica a una proyección vacía para leer el historial aún retenido. Mantener el mismo grupo en los reinicios. Verificar por separado que los nombres de imagen del Compose apunten a los artefactos EP3. Si el Compose usa etiquetas diferentes, asociarlas en el host después de confirmar las referencias.

```bash
read -rp 'Etiqueta configurada para Orders: ' ORDERS_IMAGE
read -rp 'Etiqueta configurada para BFF: ' BFF_IMAGE
docker tag pedidos360-orders:ep3 "$ORDERS_IMAGE"
docker tag pedidos360-bff:ep3 "$BFF_IMAGE"
```

Si Orders o BFF ya tienen contenedores, conservarlos y dejar pendiente el corte. Compose reemplazaría el contenedor al actualizarlo y `--no-recreate` dejaría su imagen anterior activa. Para esos servicios, acordar un corte o una activación paralela según el enrutamiento real antes de continuar. Si ninguno existe y sus dependencias reales ya están operativas, el siguiente bloque crea únicamente Orders y BFF.

```bash
if existing=$("${app[@]}" ps --all --quiet orders bff) && [[ -z "$existing" ]]; then
  "${app[@]}" up -d --no-deps --no-recreate --pull never --wait orders bff
else
  printf '%s\n' 'Corte pendiente o inventario no disponible.'
fi
"${app[@]}" ps orders bff
```

En un mantenimiento autorizado de la instalación existente, aplicar primero el SQL, comprobar que las referencias del Compose señalan las imágenes aprobadas y actualizar solamente las aplicaciones, en este orden. Este procedimiento reemplaza los contenedores de aplicación y puede causar una interrupción breve.

```bash
"${app[@]}" up -d --no-deps --pull never --wait orders &&
  "${app[@]}" up -d --no-deps --pull never --wait bff
```

Luego, comprobar los grupos sin cambiar offsets.

```bash
for group in "$KAFKA_EVENTOS_GROUP" "$KAFKA_ANALITICA_GROUP" "$KAFKA_SEGUIMIENTO_GROUP"; do
  "${broker[@]}" kafka-consumer-groups --bootstrap-server "$bootstrap" --describe --group "$group"
done
```

### API Gateway y validación

Las rutas desplegadas reportadas son:

| Método y ruta | Autorizador | Destino lógico |
|---|---|---|
| GET /api/bff/orders/analitica | JWT existente | BFF, misma ruta |
| GET /api/bff/orders/seguimiento/{id} | JWT existente | BFF, mismo ID de la solicitud |

El mapeo es dinámico. Una solicitud con ID 16 llega al BFF como /api/bff/orders/seguimiento/16. En HTTP API, el parámetro de ruta se puede referenciar con `$request.path.id`. La expresión concreta, el tipo de integración y el stage efectivo deben consultarse en la consola AWS. CORS admite el origen del frontend en GitHub Pages y http://localhost:4200. Verificar los encabezados autorizados, en particular Authorization. [Mapeo HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-parameter-mapping.html) · [CORS HTTP API](https://docs.aws.amazon.com/apigateway/latest/developerguide/http-api-cors.html)

Validar con JWT de Admin u Operador `GET /api/bff/orders/analitica` y `GET /api/bff/orders/seguimiento/{id}` a través del stage existente. Confirmar reenvío de `Authorization`, CORS y polling de la vista de Pedidos.

## 6. Kafka UI y capacidad

Desde la estación de trabajo, con SSH habilitado desde el origen administrativo autorizado y el host vigente:

```bash
ssh -N -o ExitOnForwardFailure=yes -i /ruta/a/llave.pem \
  -L 127.0.0.1:18080:127.0.0.1:18080 ubuntu@HOST_EC2
```

Abrir `http://127.0.0.1:18080`. Kafka UI y los listeners externos de los brokers están publicados en loopback. Mantener 18080, 19092–19094, 9092, los puertos de ZooKeeper y PostgreSQL fuera de las reglas públicas del Security Group. Kafka y Orders comparten una red bridge porque ambos corren en la misma EC2. Los listeners internos son PLAINTEXT.

El Compose vigente suma **4,5 GiB** de límites para Kafka, ZooKeeper y Kafka UI, y **5 vCPU** de cuotas agregadas. La t3.large del despliegue reportado tiene **2 vCPU y 8 GiB** y utiliza créditos de CPU. Las cuotas individuales no reservan CPU. Vigilar memoria, disco y créditos durante el ensayo, especialmente con PostgreSQL, los RabbitMQ y los servicios en el mismo host. Los dos tópicos representan nominalmente 4,5 GiB entre todas sus réplicas, más segmentos activos, tópicos internos y logs. Los tres brokers y ZooKeeper comparten el riesgo de caída de la EC2. [Especificaciones de instancias EC2](https://docs.aws.amazon.com/ec2/latest/instancetypes/gp.html)

## 7. Evidencia y límites

El equipo comprobó en AWS la integración Angular, el descarte de un JSON inválido y la acumulación y recuperación de lag. Ver [DEMO-EP3](../../DEMO-EP3.md) para repetirlo. Una prueba de caída de broker y los reintentos ante fallos recuperables permanecen pendientes. Los nombres reales de proyectos, servicios, redes, roles, volúmenes, stage y destino del Gateway dependen del inventario. Consultar la dirección o DNS actual de EC2 en cada sesión.
