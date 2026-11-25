# RabbitMQ de desarrollo

Este Compose usa un broker `rabbitmq:4.1-management` con el volumen `rabbitmq_data`. Publica AMQP en 5672 y Management en 15672 en todas las interfaces del host. EC2 usa un clúster de dos nodos con su propia configuración operativa.

Validar desde la raíz con `docker compose -f infra/rabbitmq/docker-compose.yml config --no-interpolate --quiet`. En un entorno aislado, configurar `RABBITMQ_PASSWORD` por el entorno y arrancar mediante `docker compose -f infra/rabbitmq/docker-compose.yml up -d`. Conservar el volumen. Ver [topología, ACK/NACK y DLQ](../../README.md#6-rabbitmq) y [demostración de convivencia con Kafka](../../DEMO-EP3.md).
