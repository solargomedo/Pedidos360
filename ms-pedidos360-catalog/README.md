# Catalog

Servicio Java 17 y Spring Boot, puerto 8082. Persiste los productos en una base PostgreSQL independiente de Orders. La API interna `/api/catalog/productos` admite GET de colección y detalle, POST, PUT y DELETE. El PATCH `/{id}/stock?cantidad=N` descuenta unidades si hay stock. Orders lo llama al aceptar un pedido. Catalog carece de listeners RabbitMQ o Kafka.

BFF autoriza la lectura a Admin, Operador y Cliente. Solo Admin modifica el catálogo. Usar `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` desde el entorno. Desde el módulo, ejecutar `./mvnw test` con una base de pruebas aislada y `./mvnw spring-boot:run` para desarrollo. Desde la raíz, `docker build -t pedidos360-catalog:local ./ms-pedidos360-catalog` construye la imagen.

Ver [contrato completo](../README.md#4-servicios-y-contratos-rest).
