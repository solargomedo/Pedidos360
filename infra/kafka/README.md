# Kafka para Pedidos360 · EP3

Infraestructura independiente: tres ZooKeeper, tres brokers y Kafka UI. No cambia
RabbitMQ, PostgreSQL, microservicios ni el Compose AWS. Los comandos siguientes
se ejecutan desde la raíz del repositorio, en PowerShell o Bash, con Docker
Desktop en modo contenedores Linux y Docker Compose moderno.

Se fijan `confluentinc/cp-kafka:7.9.11`, `confluentinc/cp-zookeeper:7.9.11`
y `provectuslabs/kafka-ui:v0.7.2`. La línea 7.9 permite el modo ZooKeeper requerido;
la configuración depende del modo ZooKeeper. Ver [EC2](../aws/EP3-EC2.md) para el despliegue reportado y [Orders](../../ms-pedidos360-orders/KAFKA.md) para eventos, grupos y offsets.

## Validar sin iniciar

```sh
docker compose -f infra/kafka/docker-compose.yml config --quiet
docker compose -f infra/kafka/docker-compose.yml config --services
```

La validación interpreta YAML, variables y referencias Compose; no verifica
disponibilidad de imágenes, puertos libres, memoria ni conectividad efectiva.

## Inicio y creación de tópicos

Ejecutar solamente cuando se autorice el arranque:

```sh
docker compose -f infra/kafka/docker-compose.yml up -d --wait --wait-timeout 300
bash infra/kafka/create-topics.sh
```

Ejecutar el segundo comando solo si el primero termina correctamente. El script
montado en solo lectura crea ambos tópicos mediante `--if-not-exists`, comprueba
3 particiones y replicación 3, y reaplica explícitamente las políticas. Si un
tópico existente tiene otra estructura, termina con error sin borrarlo ni
reasignar réplicas. Si una operación falla, corregir la causa y repetir el script.
No hay servicio auxiliar de inicialización ni creación automática por productores.
Git conserva LF en el script; se invoca con Bash, sin requerir permiso ejecutable.

| Tópico | Particiones | Replicación | Limpieza | Retención temporal | Retención por partición |
|---|---:|---:|---|---|---|
| `pedidos.eventos` | 3 | 3 | `delete` | 604800000 ms (7 días) | 268435456 bytes (256 MiB) |
| `pedidos.analitica` | 3 | 3 | `delete` | 604800000 ms (7 días) | 268435456 bytes (256 MiB) |

Se elimina por el primer límite alcanzado, por segmentos, no inmediatamente al
cumplir el límite: segmentos de 64 MiB, rotación temporal de 1 hora y revisión de
retención cada 5 minutos. Ambos tópicos tienen `min.insync.replicas=2`;
el productor de Orders usa `acks=all` para aplicar esa garantía.
Los tópicos internos de offsets y transacciones también tienen replicación 3.

## Puertos y conectividad

| Servicio | Dirección para aplicaciones en el host | Dirección dentro de la red Kafka |
|---|---|---|
| kafka-1 | `localhost:19092` | `kafka-1:9092` |
| kafka-2 | `localhost:19093` | `kafka-2:9092` |
| kafka-3 | `localhost:19094` | `kafka-3:9092` |
| Kafka UI | `http://localhost:18080` | `http://kafka-ui:8080` |

Los brokers escuchan EXTERNAL en 29092 dentro del contenedor y anuncian el puerto
correspondiente del host; INTERNAL se usa entre brokers y por Kafka UI. Cada
ZooKeeper utiliza 2181 para clientes, 2888 para quorum y 3888 para elección,
exclusivamente dentro de Docker, sin publicar estos puertos.

Para Orders en el host:

```properties
spring.kafka.bootstrap-servers=localhost:19092,localhost:19093,localhost:19094
```

Para aplicaciones en contenedores unidos a `pedidos360-kafka_kafka`:

```properties
spring.kafka.bootstrap-servers=kafka-1:9092,kafka-2:9092,kafka-3:9092
```

[compose.kafka.yaml](../aws/compose.kafka.yaml) une Orders a la red externa. Los listeners internos sirven a brokers, Kafka UI y Orders. Los externos se anuncian en loopback. Para acceder a Kafka UI en EC2, usar [el túnel SSH](../aws/EP3-EC2.md#6-kafka-ui-y-capacidad). PLAINTEXT solo se utiliza en las redes controladas. Mantener el nombre real del proyecto para reutilizar sus volúmenes.

## Inspección y verificación

```sh
docker compose -f infra/kafka/docker-compose.yml ps
docker compose -f infra/kafka/docker-compose.yml logs --tail 100 zookeeper-1 zookeeper-2 zookeeper-3 kafka-1 kafka-2 kafka-3 kafka-ui
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 kafka-broker-api-versions --bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 kafka-topics --bootstrap-server kafka-1:9092 --list
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 kafka-topics --bootstrap-server kafka-1:9092 --describe --topic pedidos.eventos
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 kafka-topics --bootstrap-server kafka-1:9092 --describe --topic pedidos.analitica
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 kafka-configs --bootstrap-server kafka-1:9092 --entity-type topics --entity-name pedidos.eventos --describe
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 kafka-configs --bootstrap-server kafka-1:9092 --entity-type topics --entity-name pedidos.analitica --describe
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 kafka-topics --bootstrap-server kafka-1:9092 --describe --under-replicated-partitions
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 kafka-topics --bootstrap-server kafka-1:9092 --describe --unavailable-partitions
```

Verificar tres brokers (IDs 1, 2 y 3); cada tópico debe indicar `PartitionCount: 3`
y `ReplicationFactor: 3`. Cada partición 0, 1 y 2 debe tener líder válido,
tres IDs en `Replicas` y tres en `Isr` una vez estabilizado el cluster. Las dos
últimas consultas no deben mostrar particiones. `kafka-configs` debe mostrar las
políticas de la tabla y `min.insync.replicas=2`.

Abrir `http://localhost:18080`: cluster `pedidos360`, secciones Brokers y Topics.
Verificar los tres brokers y las particiones/réplicas de ambos tópicos. El estado
del contenedor UI por sí solo no demuestra conectividad del navegador ni salud
de los tópicos. El CLI anterior consulta el listener interno. ISR identifica las réplicas sincronizadas. Con acks=all y min.insync.replicas=2, una partición con menos de dos réplicas sincronizadas rechaza nuevas publicaciones con esa política.

## Detener y volver a iniciar conservando datos

```sh
docker compose -f infra/kafka/docker-compose.yml stop -t 60 kafka-ui kafka-1 kafka-2 kafka-3
docker compose -f infra/kafka/docker-compose.yml stop -t 30 zookeeper-1 zookeeper-2 zookeeper-3
```

Para volver a iniciar contenedores existentes, ejecutar el primer comando de la sección de inicio con --no-recreate. Consultar los tópicos existentes mediante describe.
No ejecutar `down`, `down -v`, `rm` ni `prune`. Hay nueve volúmenes propios:
dos por ZooKeeper (datos y log) y uno por broker; sus nombres llevan el prefijo
`pedidos360-kafka_`. No se reutilizan volúmenes de otros entornos.

## Recursos y límites

| Componente | Cantidad | Límite por contenedor | Heap máximo | CPU máxima por contenedor |
|---|---:|---:|---:|---:|
| ZooKeeper | 3 | 384 MiB | 192 MiB | 0.5 |
| Kafka | 3 | 1024 MiB | 512 MiB | 1 |
| Kafka UI | 1 | 384 MiB | 192 MiB | 0.5 |

Los límites actuales suman **4608 MiB (4,5 GiB)** y 5 vCPU en cuotas agregadas. La EC2 t3.large reportada tiene 2 vCPU y 8 GiB y comparte recursos con PostgreSQL, RabbitMQ y los servicios. Medir memoria, CPU y créditos para evaluar el margen de operación. El límite de memoria por contenedor y el heap máximo miden cosas distintas.

La retención de los dos tópicos equivale nominalmente a 4.5 GiB contando las
tres réplicas; segmentos activos, logs internos, ZooKeeper e imágenes agregan
espacio y la retención no es una cuota dura de disco. Prever al menos 15 GiB
libres inicialmente y vigilar crecimiento. No se limitan físicamente los volúmenes.

```sh
docker compose -f infra/kafka/docker-compose.yml stats --no-stream
```

CPU limitada puede demorar el inicio; si vence `--wait-timeout`, inspeccionar
estados/logs y recursos antes de repetir. Los healthchecks verifican conexión
ZooKeeper/consulta Kafka; no certifican quorum completo ni réplicas sincronizadas.
Los tres brokers y nodos comparten host: no proporcionan tolerancia a su pérdida.

Referencias de configuración:
- [Confluent 7.9: variables Docker y modo ZooKeeper](https://docs.confluent.io/platform/7.9/installation/docker/config-reference.html)
- [Kafka UI: configuración Docker Compose](https://github.com/provectus/kafka-ui/blob/master/documentation/compose/kafka-ui.yaml)
