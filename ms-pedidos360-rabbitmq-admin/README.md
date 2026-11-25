# RabbitMQ Admin

Servicio en el puerto 8083 con base REST `/api/admin/rabbitmq`. Usa Basic Auth para invocar RabbitMQ Management en el vhost `/`. Spring Security requiere JWT válido y rol Admin. Permite crear colas, exchanges y bindings. La eliminación de colas y exchanges se limita a `prueba.*`, y la de bindings exige ese prefijo en ambos extremos. Las colas deben estar vacías y sin uso. El servicio no participa en el procesamiento de Kafka.

Desde el módulo, ejecutar `mvn test`. Para iniciar con `mvn spring-boot:run`, configurar `ENTRA_JWT_ISSUER_URI`, `ENTRA_JWT_AUDIENCE`, `RABBITMQ_MANAGEMENT_URL`, `RABBITMQ_MANAGEMENT_USERNAME` y `RABBITMQ_PASSWORD` en el entorno. Las pruebas de escritura corresponden a un entorno aislado. Ver [contratos y topología](../README.md#6-rabbitmq).
