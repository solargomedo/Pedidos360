# Consulta del historial de Orders mediante BFF

GET /api/bff/rabbitmq/events?after=0&limit=100&pedidoId=42

Requiere JWT Entra v?lido y claim roles con Admin. Sin JWT: 401; sin Admin: 403.
La validaci?n JWT del BFF no cambia. El token validado se reenv?a por Authorization
?nicamente al destino configurado en orders.service.url (ORDERS_SERVICE_URL).
El cliente dedicado no sigue redirecciones y no almacena tokens ni credenciales.
Orders debe seguir en red interna; no publicar puertos ni rutas directas desde Internet.

after es exclusivo, >=0, por defecto 0. limit es 1..1000, por defecto 100.
pedidoId es opcional y positivo. Par?metros inv?lidos devuelven 400 con
{"error":"INVALID_PARAMETERS"}, sin llamar a Orders.
Se preservan instanceId, capacity, earliestSequence, lastSequence, nextAfter,
truncated y events. El BFF no genera eventos ni transforma errores en listas vac?as.
Una lista vac?a con HTTP 200 solo proviene de una respuesta v?lida de Orders.
La memoria sigue limitada a una instancia; el cliente futuro debe reiniciar el cursor
cuando cambie instanceId y advertir si truncated indica p?rdida por retenci?n.

Errores de Orders: 400, 401 y 403 conservan su c?digo con cuerpo sanitizado.
Otros c?digos, timeout, fallo de conexi?n o respuesta incompleta/inv?lida generan
503 {"error":"ORDERS_UNAVAILABLE"}. No se reenv?an cuerpos de error ni cabeceras
internas de Orders. Las respuestas del controlador/cliente incluyen Cache-Control:no-store.

Timeouts independientes para no alterar operaciones existentes de pedidos:
ORDERS_EVENTS_CONNECT_TIMEOUT=2s y ORDERS_EVENTS_READ_TIMEOUT=5s.
No hay retries autom?ticos. orders.service.url ya existente es reutilizada;
OrdersClient y sus operaciones previas no se modifican.

Validaci?n local: mvn package. Pruebas con servidor HTTP local y decoder JWT controlado,
sin datos de demostraci?n en respuestas productivas ni llamadas a AWS.
Se comprueban seguridad, token, query, respuesta vac?a leg?tima, cursores/retenci?n,
errores sanitizados, timeout y regresi?n de las seis operaciones de Orders y GET Catalog.
La suite previa de m?tricas tambi?n se ejecuta.

Pendiente de revisi?n futura en AWS: ruta GET /api/bff/rabbitmq/events (o cobertura
por proxy existente), authorizer JWT, paso de query y Authorization, CORS GET/OPTIONS
y conectividad privada BFF -> Orders con issuer/audiencia coincidentes.
No se modific? AWS, Compose ni Angular en esta etapa.
