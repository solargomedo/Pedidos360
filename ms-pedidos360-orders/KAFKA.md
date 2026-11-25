# Proyecciones Kafka en Orders

El DTO `PedidoEvento` contiene `id`, `productoId`, `cantidad` y `estado` de
`Pedido`, más `tipo` (`CREADO`, `ACTUALIZADO`, `ESTADO_CAMBIADO`). Se toma un
snapshot inmutable después de guardar cada operación exitosa. La creación
conserva su publicación RabbitMQ previa. Consultas y eliminación no generan
eventos Kafka; no se agregan lecturas ni cambios a esas operaciones.

| Tópico | Tipos publicados | Consumer group predeterminado |
|---|---|---|
| `pedidos.eventos` | CREADO, ACTUALIZADO, ESTADO_CAMBIADO | `pedidos360-orders-eventos` |
| `pedidos.eventos` | CREADO, ACTUALIZADO, ESTADO_CAMBIADO | `pedidos360-orders-seguimiento` |
| `pedidos.analitica` | CREADO, ACTUALIZADO, ESTADO_CAMBIADO | `pedidos360-orders-analitica` |

Cada operación publica el mismo DTO en ambos tópicos, con el ID del pedido como
clave para conservar el orden por pedido dentro de cada tópico. Los listeners
delegan en `PedidoKafkaProcessor`. Eventos valida y registra; seguimiento y
analítica validan el DTO y su clave Kafka y mantienen una fila por pedido en
tablas distintas. Cada escritura aplica el snapshot si su offset es mayor y
pertenece a la misma partición. El cálculo de analítica usa su propia proyección.

### Contrato JSON, orden y offsets

```json
{"tipo":"CREADO","id":16,"productoId":3,"cantidad":2,"estado":"CREADO"}
```

La key es el ID como texto, por ejemplo 16, separado del JSON. Con el particionado estable, los snapshots de esa key llegan a la misma partición de cada tópico. El offset identifica la posición dentro de una partición y establece el orden en ese log. No hay orden global ni comparación útil de offsets entre tópicos. CREADO, ACTUALIZADO y ESTADO_CAMBIADO contienen el snapshot completo.

El grupo eventos valida y registra. Seguimiento mantiene el último snapshot por ID desde el mismo tópico. Analítica mantiene otra proyección desde pedidos.analitica y agrega pedidos distintos, cantidades vigentes y todos los estados. Con grupos distintos, cada uno recibe el flujo y confirma su propio offset. En el CLI, CURRENT-OFFSET es la próxima posición confirmada, LOG-END-OFFSET es el fin del log y LAG es la diferencia cuando existe offset confirmado. Los registros inválidos también ocupan offsets.

## Configuración

Las propiedades están en `src/main/resources/application.properties`.

- Host: `KAFKA_BOOTSTRAP_SERVERS=localhost:19092,localhost:19093,localhost:19094`
  (valor predeterminado).
- Docker, desde un contenedor conectado a la red Kafka:
  `KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092`.
- Grupos: variables `KAFKA_EVENTOS_GROUP`, `KAFKA_ANALITICA_GROUP` y
  `KAFKA_SEGUIMIENTO_GROUP`.
- IDs: `pedidos-eventos`, `pedidos-analitica` y `pedidos-seguimiento`.
- `KAFKA_SEGUIMIENTO_ENABLED=false` impide iniciar solo seguimiento. Se lee al
  arrancar Orders. Para la demo, arrancar primero con seguimiento habilitado y
  esperar a que confirme offsets. Volver a arrancar con la variable en `false`,
  generar pedidos y observar el lag. Luego arrancar con `true` y el mismo grupo.
- `KAFKA_LISTENERS_ENABLED=false` detiene el inicio de listeners Kafka;
  no desactiva la publicación de eventos.

No se crean tópicos desde Spring. Los dos tópicos deben existir antes del
arranque, como en la infraestructura ya preparada. No se modifican Compose ni
redes existentes; un contenedor de Orders requiere conexión a esa red para
resolver los nombres internos.

## Preparación de PostgreSQL y lectura

Aplicar `src/main/resources/db/manual/V001__proyecciones_kafka.sql` a la base
`pedidos360_orders` antes de iniciar la nueva imagen. El script agrega dos
tablas y la cuenta de Orders necesita permisos SELECT, INSERT y UPDATE sobre ellas.

Para poblar una proyección de analítica vacía en un clúster con offsets ya
confirmados, iniciar Orders con
`KAFKA_ANALITICA_GROUP=pedidos360-orders-analitica-proyeccion-v1` y conservar ese
valor en los siguientes arranques. Un grupo nuevo parte del primer registro
retenido mediante `earliest`. Los offsets del grupo anterior se conservan.

- `GET /api/orders/analitica` devuelve pedidos distintos, suma de cantidades
  actuales y conteos por estado, con cero para estados sin pedidos.
- `GET /api/orders/seguimiento/{id}` devuelve el último snapshot observado o
  HTTP 404 si el pedido todavía no está en la proyección.

Estas rutas son internas de Orders. El historial vencido o que falló al
publicarse queda fuera. Una eliminación de negocio no emite evento, por lo que
la última proyección sigue disponible. Recrear un tópico o cambiar su asignación
por clave requiere reconstruir las proyecciones desde el historial disponible.

## Entrega y errores

Se utiliza JSON Jackson 3 de Spring Kafka 4, tipo DTO explícito y headers de tipo
desactivados. `ErrorHandlingDeserializer` captura mensajes ilegibles. Las
validaciones rechazan campos nulos o cantidades/IDs no positivos.

Los productores usan `acks=all`, idempotencia, hasta tres reintentos de cliente,
espera de metadatos máxima de 2 segundos por envío y plazo de entrega de 20
segundos. Se registran confirmaciones con partición/offset y fallos síncronos o
asíncronos; el fallo de un tópico no impide intentar el otro. La validación de
un snapshot inválido registra error y evita su envío.

Los fallos de envío no revierten ni repiten la operación de negocio. PostgreSQL,
RabbitMQ y Kafka no comparten transacción: puede haber publicación parcial o
eventos perdidos ante fallo del broker/proceso. No se garantiza entrega entre
estos sistemas ni exactly-once de extremo a extremo.

Los consumidores desactivan autocommit y confirman por registro. El listener
de proyección retorna después del commit JDBC de su upsert. Si la confirmación
Kafka falla, el mismo offset puede llegar de nuevo y su escritura es inocua.
Validación y deserialización inválidas se registran y se omiten. Los errores de
acceso a PostgreSQL mantienen el offset pendiente y se reintentan sin límite con
una espera de un segundo por defecto. Para otros errores se conserva el límite
de `pedidos360.kafka.retry.max-retries`. El descarte se registra con tópico,
partición y offset.

## Pruebas

Desde `ms-pedidos360-orders`, ejecutar `mvn test package` (o `./mvnw test package`).
Las pruebas de contexto usan H2 en memoria, listeners desactivados y el runner
AMQP simulado, sin PostgreSQL ni brokers externos. Las unitarias verifican el
contrato JSON, mensajes ilegibles, publicación a ambos tópicos, validación,
delegación, propagación de errores, reintentos y operaciones de negocio sin
duplicar persistencia/stock/RabbitMQ. Los GET se prueban con MockMvc y H2.

`PedidoProyeccionPostgresTest` requiere una base PostgreSQL dedicada de pruebas
y una cuenta con permiso de crear esquemas. Usa las variables
`ORDERS_TEST_POSTGRES_URL`, `ORDERS_TEST_POSTGRES_USER` y
`ORDERS_TEST_POSTGRES_PASSWORD`. Ejecutar
`mvn -Dtest=PedidoProyeccionPostgresTest test` desde Orders. Crea un esquema
aleatorio dentro de una transacción y ejecuta rollback al terminar. Sin URL se
omite. La medición de lag y la recuperación requieren Kafka real.

Referencias:
- [Spring Boot: autoconfiguración Kafka](https://docs.spring.io/spring-boot/reference/messaging/kafka.html)
- [Spring Kafka: serialización JSON](https://docs.spring.io/spring-kafka/reference/kafka/serdes.html)
- [Spring Kafka: manejo de errores](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
