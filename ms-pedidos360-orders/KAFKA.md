# Kafka EP5 en Orders

El DTO `PedidoEvento` contiene `id`, `productoId`, `cantidad` y `estado` de
`Pedido`, más `tipo` (`CREADO`, `ACTUALIZADO`, `ESTADO_CAMBIADO`). Se toma un
snapshot inmutable después de guardar cada operación exitosa. La creación
conserva su publicación RabbitMQ previa. Consultas y eliminación no generan
eventos Kafka; no se agregan lecturas ni cambios a esas operaciones.

| Tópico | Tipos publicados | Consumer group predeterminado |
|---|---|---|
| `pedidos.eventos` | CREADO, ACTUALIZADO, ESTADO_CAMBIADO | `pedidos360-orders-eventos` |
| `pedidos.analitica` | CREADO, ACTUALIZADO, ESTADO_CAMBIADO | `pedidos360-orders-analitica` |

Cada operación publica el mismo DTO en ambos tópicos, con el ID del pedido como
clave para conservar el orden por pedido dentro de cada tópico. Los listeners
delegan en `PedidoKafkaProcessor`: validación Bean Validation y registro de los
campos recibidos, sin repetir escrituras, descuentos de stock o envíos RabbitMQ.

## Configuración

Las propiedades están en `src/main/resources/application.properties`.

- Host: `KAFKA_BOOTSTRAP_SERVERS=localhost:19092,localhost:19093,localhost:19094`
  (valor predeterminado).
- Docker, desde un contenedor conectado a la red Kafka:
  `KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092`.
- Grupos: variables `KAFKA_EVENTOS_GROUP` y `KAFKA_ANALITICA_GROUP`.
- `KAFKA_LISTENERS_ENABLED=false` detiene el inicio de listeners Kafka;
  no desactiva la publicación de eventos.

No se crean tópicos desde Spring. Los dos tópicos deben existir antes del
arranque, como en la infraestructura ya preparada. No se modifican Compose ni
redes existentes; un contenedor de Orders requiere conexión a esa red para
resolver los nombres internos.

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

Los consumidores desactivan autocommit y confirman por registro. Para errores
transitorios, `DefaultErrorHandler` usa dos reintentos separados por 1 segundo
(tres intentos totales), configurables con `pedidos360.kafka.retry.*`.
Validación y deserialización inválidas no se reintentan. Al agotar los intentos
se registra topic, partición, offset, clave y excepción; se avanza el offset.
Ese descarte es explícito y no tiene almacenamiento de recuperación: no se
crean DLT ni tópicos adicionales. Una caída antes del commit puede redeliverar
un registro; el procesamiento actual solo valida y registra.

## Pruebas

Desde `ms-pedidos360-orders`, ejecutar `mvn test package` (o `./mvnw test package`).
Las pruebas de contexto usan H2 en memoria, listeners desactivados y el runner
AMQP simulado, sin PostgreSQL ni brokers externos. Las unitarias verifican el
contrato JSON, mensajes ilegibles, publicación a ambos tópicos, validación,
delegación, propagación de errores, reintentos y operaciones de negocio sin
duplicar persistencia/stock/RabbitMQ. No acreditan una prueba de integración
contra el cluster real.

Referencias:
- [Spring Boot: autoconfiguración Kafka](https://docs.spring.io/spring-boot/reference/messaging/kafka.html)
- [Spring Kafka: serialización JSON](https://docs.spring.io/spring-kafka/reference/kafka/serdes.html)
- [Spring Kafka: manejo de errores](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
