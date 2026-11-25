# Orders

Servicio Java 17 y Spring Boot, puerto 8081. Cada pedido tiene un producto, cantidad y estado. La creación persiste el pedido CREADO y publica en RabbitMQ y Kafka. Editar un pedido CREADO o cambiar su estado genera un snapshot Kafka. Aceptar un pedido también descuenta stock en Catalog. El borrado físico carece de evento Kafka.

La API interna `/api/orders` ofrece CRUD, PATCH `/{id}/estado?estado=...`, GET `/analitica` y GET `/seguimiento/{id}`. Las lecturas EP3 consultan sus propias tablas PostgreSQL. Los estados son CREADO, ACEPTADO, EN_PREPARACION, DESPACHADO, ENTREGADO y CANCELADO.

Desde el módulo, ejecutar `mvn clean verify`. La prueba del upsert requiere PostgreSQL de prueba cuando se habilita explícitamente. Aplicar `V001__proyecciones_kafka.sql` antes de arrancar la nueva imagen. Desde la raíz, ejecutar `docker build -t pedidos360-orders:ep3 ./ms-pedidos360-orders`.

- [Eventos, grupos y proyecciones](KAFKA.md)
- [Topología RabbitMQ](../README.md#6-rabbitmq)
- [Migración y despliegue en EC2](../infra/aws/EP3-EC2.md)
