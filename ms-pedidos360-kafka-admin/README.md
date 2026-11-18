# Administrador Kafka — EP5

Microservicio independiente con Java 17 y Spring Boot 4.1.1. Administra tópicos
mediante un único Kafka AdminClient con cierre al apagar y timeout configurable.
No necesita Orders, RabbitMQ, PostgreSQL ni conexión directa a ZooKeeper.
No crea ni altera tópicos al arrancar.

## Ejecución desde Git Bash

Desde la raíz del repositorio:

```bash
cd ms-pedidos360-kafka-admin
mvn test package
KAFKA_BOOTSTRAP_SERVERS=localhost:19092,localhost:19093,localhost:19094 \
  mvn spring-boot:run
```

API: `http://localhost:8084/api/admin/kafka/topics`. Requiere Maven y JDK 17.
Variables: `PORT` (8084), `SERVER_ADDRESS` (127.0.0.1),
`KAFKA_BOOTSTRAP_SERVERS` (los tres listeners externos anteriores) y
`KAFKA_ADMIN_TIMEOUT_MS` (10000, positivo). El puerto 8084 debe estar libre.

Desde un futuro contenedor conectado a `pedidos360-kafka_kafka`, utilizar
`KAFKA_BOOTSTRAP_SERVERS=kafka-1:9092,kafka-2:9092,kafka-3:9092` y
`SERVER_ADDRESS=0.0.0.0`. No se modifica el Compose existente.

## Endpoints

| Método | Ruta relativa | Respuesta exitosa |
|---|---|---|
| POST | `/` | 201 y cabecera Location |
| GET | `/` | 200, nombres de tópicos no internos |
| GET | `/{nombre}` | 200, particiones, líder, réplicas e ISR |
| GET | `/{nombre}/configs` | 200, valores efectivos, origen y atributos |
| PATCH | `/{nombre}/configs` | 200, sin cuerpo |
| DELETE | `/{nombre}` | 204, sin cuerpo |

Para las rutas raíz usar exactamente `/api/admin/kafka/topics` sin barra final.

POST recibe:

```json
{
  "nombre": "prueba.ep5",
  "particiones": 3,
  "factorReplicacion": 3,
  "configuraciones": {
    "cleanup.policy": "delete",
    "retention.ms": "604800000",
    "retention.bytes": "268435456",
    "min.insync.replicas": "2"
  }
}
```

`configuraciones` es opcional al crear; si no se envía se aplican los defaults
del broker. Nombres válidos: 1–249 caracteres alfanuméricos, punto, guion o
guion bajo, excepto `.` y `..`. Particiones y replicación deben ser positivas;
la replicación no puede superar el número de brokers disponibles.

PATCH recibe únicamente las propiedades que se quieren establecer:

```json
{"configuraciones":{"retention.ms":"3600000"}}
```

El mapa debe ser no vacío y sus claves y valores no pueden ser nulos ni blancos.
Kafka valida las configuraciones admitidas y sus valores. La actualización usa
`incrementalAlterConfigs` con SET: conserva las propiedades omitidas; no cambia
el número de particiones ni la replicación. Los valores sensibles consultados
se devuelven como null. Las solicitudes se ejecutan solo al invocar los endpoints.

## Verificación de solo lectura

```bash
curl --fail-with-body http://localhost:8084/api/admin/kafka/topics
curl --fail-with-body http://localhost:8084/api/admin/kafka/topics/pedidos.eventos
curl --fail-with-body http://localhost:8084/api/admin/kafka/topics/pedidos.eventos/configs
```

Las pruebas automatizadas usan AdminClient y servicio simulados, sin brokers,
contenedores ni operaciones reales. Las pruebas de creación, modificación y
eliminación usan exclusivamente `prueba.ep5`. No ejecutar pruebas manuales de
escritura o eliminación hasta recibir autorización; nunca usar los tópicos
`pedidos.eventos` o `pedidos.analitica` para probar DELETE.

## Errores

Formato `application/problem+json`: 400 solicitud/configuración inválida, 404
recurso o tópico inexistente, 409 tópico duplicado, 503 error transitorio Kafka,
504 timeout y 500 fallo inesperado. Los detalles técnicos quedan en logs.
Con brokers inaccesibles puede agotarse el timeout y devolverse 504. Ante un
timeout de escritura, consultar el estado antes de repetir: Kafka podría haber
completado la operación. DELETE confirma aceptación del broker; la eliminación
y propagación de metadatos pueden terminar después.

No hay autenticación: el servidor escucha en loopback por defecto. Los endpoints
de escritura pueden modificar/eliminar cualquier tópico válido accesible al
cliente; no invocarlos contra datos existentes durante la evaluación.
