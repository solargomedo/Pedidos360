# DEMO EP3 · Pedidos360

## Estado de evidencia y preparación

**Comprobado en AWS según el equipo:** integración Angular → Gateway → BFF → Orders/Kafka, acumulación y recuperación de lag de seguimiento y descarte de JSON inválido. **Comprobaciones para mostrar en defensa:** capturar resultados de los pasos siguientes en el entorno designado, incluida la continuidad tras el descarte. **Pendiente:** caída de un broker y reintentos ante fallos recuperables, incluidos errores de persistencia. La recuperación de lag acredita que el consumidor pudo reanudar desde su posición confirmada.

Las operaciones de creación, aceptación, publicación inválida y recreación de Orders pertenecen a una sesión de demostración autorizada. Usar un producto de prueba con stock y una cuenta Admin u Operador. No mostrar tokens, llaves ni configuraciones interpoladas.

**AWS Ubuntu**, desde la raíz de la copia desplegada, cargar los nombres reales. Si el Compose tiene más archivos, agregarlos en su orden antes del complemento de Kafka. Las rutas y los proyectos se revisan en [EP3-EC2](infra/aws/EP3-EC2.md).

```bash
read -rp 'Proyecto Compose Kafka [pedidos360-kafka]: ' KAFKA_PROJECT
k=(docker compose -p "${KAFKA_PROJECT:-pedidos360-kafka}" -f infra/kafka/docker-compose.yml)
broker=("${k[@]}" exec -T kafka-1)
bs=kafka-1:9092,kafka-2:9092,kafka-3:9092

read -rp 'Proyecto Compose de la aplicación: ' APP_PROJECT
read -rp 'Compose base de EC2: ' APP_COMPOSE
read -rp 'Archivo de entorno vigente: ' APP_ENV
app=(docker compose -p "$APP_PROJECT" --env-file "$APP_ENV" -f "$APP_COMPOSE" -f infra/aws/compose.kafka.yaml)
"${app[@]}" config --quiet

read -rp 'Grupo efectivo de eventos [pedidos360-orders-eventos]: ' EVENTS
read -rp 'Grupo efectivo de seguimiento [pedidos360-orders-seguimiento]: ' TRACK
read -rp 'Grupo efectivo de analítica [pedidos360-orders-analitica-proyeccion-v1]: ' ANALYTICS
EVENTS=${EVENTS:-pedidos360-orders-eventos}
TRACK=${TRACK:-pedidos360-orders-seguimiento}
ANALYTICS=${ANALYTICS:-pedidos360-orders-analitica-proyeccion-v1}
lag() {
  "${broker[@]}" kafka-consumer-groups --bootstrap-server "$bs" --describe --group "$1"
}
```

**Git Bash Windows**, configurar la URL HTTPS del stage, sin barra final, y leer el JWT de forma oculta. Usar un access token de Entra para la audiencia de la API y manejarlo fuera de capturas. Para ejemplos HTTP se define una función local.

```bash
read -rp 'URL base HTTPS del stage de API Gateway: ' API
API=${API%/}
read -rsp 'Access token autorizado: ' TOKEN
printf '\n'
get() {
  curl -sS -i -H "Authorization: Bearer $TOKEN" "$API$1"
}
```

## 1. Estado de tres ZooKeeper y tres brokers

**Objetivo y terminal.** En AWS Ubuntu, comprobar que arrancaron los seis nodos y que Kafka responde por DNS interno.

```bash
"${k[@]}" ps zookeeper-1 zookeeper-2 zookeeper-3 kafka-1 kafka-2 kafka-3
"${broker[@]}" kafka-broker-api-versions --bootstrap-server "$bs"
```

**Resultado y evidencia.** Mostrar seis contenedores sanos y los brokers 1, 2 y 3. Esto verifica procesos y conectividad. El ISR se verifica aparte.

## 2. Kafka UI por SSH

**Objetivo y terminal.** En una segunda ventana Git Bash Windows, abrir UI mediante el puerto loopback de EC2.

```bash
read -rp 'DNS o dirección actual de EC2: ' EC2_HOST
read -rp 'Ruta local a la llave SSH: ' SSH_KEY
ssh -N -o ExitOnForwardFailure=yes -i "$SSH_KEY" \
  -L 127.0.0.1:18080:127.0.0.1:18080 "ubuntu@$EC2_HOST"
```

**Resultado y evidencia.** Abrir `http://localhost:18080` en el navegador. Mostrar el clúster `pedidos360`, Brokers, Topics y Consumers. El panel es accesible a través del túnel.

## 3. Particiones, replicación e ISR

**Objetivo y terminal.** En AWS Ubuntu, comprobar metadatos y política efectiva.

```bash
for topic in pedidos.eventos pedidos.analitica; do
  "${broker[@]}" kafka-topics --bootstrap-server "$bs" --describe --topic "$topic"
  "${broker[@]}" kafka-configs --bootstrap-server "$bs" --entity-type topics --entity-name "$topic" --describe
done
"${broker[@]}" kafka-topics --bootstrap-server "$bs" --describe --under-replicated-partitions
```

**Resultado y evidencia.** Mostrar tres particiones y tres réplicas por tópico, líder válido, ISR de tres por partición estable y `min.insync.replicas=2`. La última consulta queda vacía si no hay subreplicación. ISR es el conjunto de réplicas sincronizadas en este momento.

## 4. Crear un pedido desde Angular

**Objetivo y terminal.** En el navegador, iniciar sesión como Admin u Operador, abrir **Pedidos**, ingresar un ID de producto de prueba con stock y cantidad 1, y pulsar **Crear pedido**.

**Resultado y evidencia.** En Network, mostrar el POST al Gateway y su 201 sin mostrar Authorization. Anotar solo el ID del pedido. Se comprueba el recorrido HTTP con sesión real.

## 5. Ver el evento JSON

**Objetivo y terminal.** En AWS Ubuntu, localizar el ACK del productor. En el navegador, abrir Kafka UI → Topics → Messages en ambos tópicos, filtrar por la key del ID anotado o abrir la partición y offset del log.

```bash
read -rp 'ID del pedido creado: ' ID
"${app[@]}" logs --tail 250 orders | grep -F "pedido=$ID tipo=CREADO"
```

**Resultado y evidencia.** Mostrar confirmación por tópico y el JSON con tipo CREADO, ID, producto, cantidad y estado. La key del registro es el ID como texto y se ve aparte del valor JSON.

## 6. Grupos independientes

**Objetivo y terminal.** En AWS Ubuntu, comparar posiciones confirmadas.

```bash
for group in "$EVENTS" "$TRACK" "$ANALYTICS"; do lag "$group"; done
```

**Resultado y evidencia.** Dos grupos distintos muestran `pedidos.eventos`. El tercero muestra `pedidos.analitica`. Cada grupo tiene su propia posición y lag. Mostrar offsets y particiones para la misma publicación.

## 7. Actualización automática de analítica y seguimiento

**Objetivo y terminal.** En navegador, conservar Pedidos abierto y observar las llamadas de Network sin recargar. En Git Bash Windows, consultar seguimiento con el ID creado.

```bash
read -rp 'ID del pedido creado: ' ID
get /api/bff/orders/analitica
get "/api/bff/orders/seguimiento/$ID"
```

**Resultado y evidencia.** Mostrar los GET automáticos cada cinco segundos y el aumento del total y unidades tras la creación, si no hay otras operaciones concurrentes. Seguimiento devuelve 200 y el snapshot CREADO. La pantalla consulta analítica automáticamente. El seguimiento individual se comprueba por API.

## 8. Cambiar el estado

**Objetivo y terminal.** En navegador, seleccionar ACEPTADO en la fila del pedido y pulsar **Cambiar estado**. En Git Bash Windows:

```bash
get "/api/bff/orders/seguimiento/$ID"
get /api/bff/orders/analitica
```

**Resultado y evidencia.** Con stock suficiente, el cambio responde 200 y la proyección muestra ACEPTADO tras procesarse. El conteo se mueve de CREADO a ACEPTADO, mientras el total de pedidos y las unidades de ese pedido se conservan.

## 9. Orden con la misma key

**Objetivo y terminal.** En AWS Ubuntu, comparar los ACK del productor y confirmar en Kafka UI.

```bash
"${app[@]}" logs --tail 300 orders | grep -F "pedido=$ID tipo="
```

**Resultado y evidencia.** En cada tópico, mostrar que CREADO y ESTADO_CAMBIADO comparten key y partición y que el segundo tiene un offset mayor. Los offsets de un tópico no se comparan con los del otro.

## 10. Deshabilitar solo seguimiento

**Objetivo y terminal.** En AWS Ubuntu, anotar primero el offset confirmado del grupo. Luego recrear únicamente Orders con seguimiento inactivo. Confirmar previamente las redes efectivas, imagen y configuración del despliegue.

```bash
lag "$TRACK"
KAFKA_SEGUIMIENTO_ENABLED=false KAFKA_EVENTOS_GROUP="$EVENTS" \
  KAFKA_SEGUIMIENTO_GROUP="$TRACK" KAFKA_ANALITICA_GROUP="$ANALYTICS" \
  "${app[@]}" up -d --no-deps --force-recreate --pull never --wait orders
"${app[@]}" ps orders
```

**Resultado y evidencia.** Tras el breve reinicio de Orders, eventos y analítica vuelven a consumir. El grupo seguimiento queda sin consumidor activo si hay una sola instancia de Orders. La propiedad se lee al arrancar y no cambia offsets.

## 11. Acumular lag

**Objetivo y terminal.** En navegador, crear uno o más pedidos de prueba. En AWS Ubuntu:

```bash
lag "$TRACK"
lag "$EVENTS"
lag "$ANALYTICS"
```

**Resultado y evidencia.** Mostrar que en seguimiento `LOG-END-OFFSET` crece mientras `CURRENT-OFFSET` se mantiene y el lag es positivo en las particiones afectadas. Eventos y analítica siguen avanzando. La consulta de seguimiento de un ID nuevo puede devolver 404 durante la pausa.

## 12. Reactivar y recuperar lag

**Objetivo y terminal.** En AWS Ubuntu, volver a habilitar seguimiento con los mismos grupos.

```bash
KAFKA_SEGUIMIENTO_ENABLED=true KAFKA_EVENTOS_GROUP="$EVENTS" \
  KAFKA_SEGUIMIENTO_GROUP="$TRACK" KAFKA_ANALITICA_GROUP="$ANALYTICS" \
  "${app[@]}" up -d --no-deps --force-recreate --pull never --wait orders
lag "$TRACK"
```

**Resultado y evidencia.** Repetir `lag "$TRACK"` hasta ver 0. En Git Bash, consultar `get "/api/bff/orders/seguimiento/$ID_NUEVO"` tras asignar a `ID_NUEVO` el ID creado durante la pausa, por ejemplo con `read -rp 'ID durante la pausa: ' ID_NUEVO`. Mostrar 200 y avance desde los offsets anteriores. Esta recuperación fue reportada como comprobada en AWS.

## 13. Publicar un JSON inválido

**Objetivo y terminal.** En AWS Ubuntu, publicar un único JSON malformado al tópico de analítica de la demo autorizada.

```bash
printf '%s\n' '{"tipo":' | "${broker[@]}" kafka-console-producer \
  --bootstrap-server "$bs" --topic pedidos.analitica
"${app[@]}" logs --since 2m orders | grep -E 'Kafka descartado|DeserializationException'
```

**Resultado y evidencia.** Mostrar tópico, partición y offset del descarte en logs. El evento no produce una fila de analítica. Se verifica validación de entrada y manejo de deserialización. El descarte fue reportado como comprobado en AWS.

## 14. Continuar después del inválido

**Objetivo y terminal.** En el navegador crear otro pedido válido. En AWS Ubuntu y en la vista de Pedidos, comprobar el evento posterior y el incremento esperado.

```bash
"${app[@]}" logs --since 3m orders | grep -E 'Kafka publicado topic=pedidos.analitica|Kafka descartado'
lag "$ANALYTICS"
```

**Resultado y evidencia.** Mostrar descarte anterior, publicación válida posterior e indicadores actualizados. Si se requiere probar continuidad **en la misma partición**, comparar partición y offset de los logs y repetir con pedidos de prueba hasta observar un válido posterior en esa partición. Lag 0 indica que el consumidor alcanzó el fin, y el indicador comprueba que procesó datos útiles.

## 15. RabbitMQ y Kafka funcionan juntos

**Objetivo y terminal.** En AWS Ubuntu, consultar cada nodo real de RabbitMQ y correlacionar los logs de Orders con un pedido nuevo.

```bash
read -rp 'Contenedor RabbitMQ del primer nodo: ' RMQ1
read -rp 'Contenedor RabbitMQ del segundo nodo: ' RMQ2
for node in "$RMQ1" "$RMQ2"; do
  docker exec "$node" rabbitmq-diagnostics -q ping
done
docker exec "$RMQ1" rabbitmqctl cluster_status
"${app[@]}" logs --since 3m orders | grep -E 'Notificacion enviada|Evento de auditoria enviado|Solicitud de comprobante enviada|ACK enviado|Kafka publicado'
```

**Resultado y evidencia.** Mostrar los dos nodos y las tres publicaciones AMQP junto con las confirmaciones Kafka. Mostrar ACK si los consumidores AMQP del entorno están activos. Esto comprueba convivencia de ambos flujos. Comparar IDs donde aparezcan y evitar atribuir un ACK sin ID a un pedido específico.

## 16. Proteger las rutas con JWT

**Objetivo y terminal.** En Git Bash Windows, probar ambas rutas sin token, con token autorizado y, si existe una cuenta de prueba, con rol Cliente.

```bash
for path in /api/bff/orders/analitica "/api/bff/orders/seguimiento/$ID"; do
  curl -sS -o /dev/null -w 'Sin JWT: %{http_code}\n' "$API$path"
  get "$path"
done

read -rsp 'Access token Cliente (Enter para omitir): ' CLIENT_TOKEN
printf '\n'
if [[ -n "$CLIENT_TOKEN" ]]; then
  for path in /api/bff/orders/analitica "/api/bff/orders/seguimiento/$ID"; do
    curl -sS -o /dev/null -w 'Cliente: %{http_code}\n' \
      -H "Authorization: Bearer $CLIENT_TOKEN" "$API$path"
  done
fi
unset TOKEN CLIENT_TOKEN
```

**Resultado y evidencia.** Mostrar 401 sin JWT, 200 con Admin u Operador para un ID proyectado, y 403 con rol Cliente. El comprobante del rol insuficiente requiere un token Cliente válido. Mostrar los códigos y el cuerpo de una respuesta autorizada sin revelar credenciales.
