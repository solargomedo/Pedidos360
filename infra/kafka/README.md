# Kafka para Pedidos360 — EP5 y EP6

Infraestructura independiente: tres ZooKeeper, tres brokers y Kafka UI. No cambia
RabbitMQ, PostgreSQL, microservicios ni el Compose AWS. Los comandos siguientes
se ejecutan desde la raíz del repositorio, en PowerShell o Bash, con Docker
Desktop en modo contenedores Linux y Docker Compose moderno.

Se fijan `confluentinc/cp-kafka:7.9.11`, `confluentinc/cp-zookeeper:7.9.11`
y `provectuslabs/kafka-ui:v0.7.2`. La línea 7.9 permite el modo ZooKeeper requerido;
no sustituir por imágenes Kafka 4.x/KRaft. Las imágenes no se descargan durante
la validación estática. Su descarga y arranque quedan pendientes de ejecución.

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
docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1 bash /opt/pedidos360/create-topics.sh
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
los futuros productores deben usar `acks=all` para aplicar esa garantía.
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

Para futuras aplicaciones Spring Boot ejecutadas localmente:

```properties
spring.kafka.bootstrap-servers=localhost:19092,localhost:19093,localhost:19094
```

Para aplicaciones en contenedores unidos a `pedidos360-kafka_kafka`:

```properties
spring.kafka.bootstrap-servers=kafka-1:9092,kafka-2:9092,kafka-3:9092
```

Un futuro Compose de aplicaciones puede declarar esta red mediante:

```yaml
networks:
  kafka:
    external: true
    name: pedidos360-kafka_kafka
```

El servicio de la aplicación debe incluir `kafka` en sus redes; la red tiene que
existir antes de iniciarlo. No se modifica ni conecta ahora ningún contenedor
existente. `localhost` dentro de un contenedor apunta al propio contenedor.

Todos los puertos publicados se vinculan a `127.0.0.1`, sin conflictos con
8080–8083, 5672, 15672 o 5432. No usar `-p` ni cambiar `COMPOSE_PROJECT_NAME`:
el nombre fijo mantiene la red y los volúmenes del proyecto. El acceso remoto no
está configurado. PLAINTEXT y Kafka UI sin autenticación se destinan a esta
evaluación local; no publicar estos endpoints directamente en AWS.

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
de los tópicos. El CLI anterior consulta el listener interno; la conexión externa
debe verificarse posteriormente desde la aplicación local, todavía sin implementar.

## Detener y volver a iniciar conservando datos

```sh
docker compose -f infra/kafka/docker-compose.yml stop -t 60 kafka-ui kafka-1 kafka-2 kafka-3
docker compose -f infra/kafka/docker-compose.yml stop -t 30 zookeeper-1 zookeeper-2 zookeeper-3
```

Para volver a iniciar, repetir los dos comandos de la sección de inicio.
No ejecutar `down`, `down -v`, `rm` ni `prune`. Hay nueve volúmenes propios:
dos por ZooKeeper (datos y log) y uno por broker; sus nombres llevan el prefijo
`pedidos360-kafka_`. No se reutilizan volúmenes de otros entornos.

## Recursos y límites

| Componente | Cantidad | Límite por contenedor | Heap máximo | CPU máxima por contenedor |
|---|---:|---:|---:|---:|
| ZooKeeper | 3 | 512 MiB | 256 MiB | 0.5 |
| Kafka | 3 | 1536 MiB | 768 MiB | 1 |
| Kafka UI | 1 | 512 MiB | 256 MiB | 0.5 |

Los límites suman **6656 MiB (6.5 GiB)** y 5 CPU, sin contar Docker, sistema
operativo ni aplicaciones existentes. Como presupuesto inicial de laboratorio,
asignar al menos 8 GiB y 4 CPU a Docker; con RabbitMQ, PostgreSQL y Spring Boot
simultáneos, prever 10–12 GiB para Docker y un host de al menos 16 GiB. Estos
valores requieren medición, no son capacidad validada. Una instancia de 2 GiB
no alcanza para esta configuración.

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
