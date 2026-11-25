# BFF Pedidos360

Servicio Java 17 y Spring Boot 4.1.1, puerto 8080. Recibe el JWT de Entra ID desde Gateway, valida emisor y audiencia, y aplica los roles. Delega por REST a los servicios internos, sin reenviar el JWT. Configurar `ORDERS_SERVICE_URL` y `CATALOG_SERVICE_URL` con los destinos vigentes.

| GET externo | GET interno de Orders | Roles |
|---|---|---|
| `/api/bff/orders/analitica` | `/api/orders/analitica` | Admin, Operador |
| `/api/bff/orders/seguimiento/{id}` | `/api/orders/seguimiento/{id}` | Admin, Operador |

Conserva los estados HTTP de Orders, incluido 404 para un pedido aún sin seguimiento. Un JWT ausente o inválido devuelve 401 y un rol insuficiente, 403. Las rutas anteriores de pedidos y catálogo conservan sus permisos.

Desde el módulo, ejecutar `mvn clean verify`. MockMvc comprueba el enrutamiento, la regla de roles y la respuesta HTTP con decoder y cliente de Orders simulados. Desde la raíz, ejecutar `docker build -t pedidos360-bff:ep3 ./ms-pedidos360-bff`. Ver [EC2](../infra/aws/EP3-EC2.md).
