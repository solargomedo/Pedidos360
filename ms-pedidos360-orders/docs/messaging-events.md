# Historial de observaciones de mensajer?a

Implementaci?n en Orders, con endpoint HTTP privado protegido para consulta. No requiere infraestructura para sus pruebas unitarias.

## Alcance y consulta interna

MessagingEventHistory conserva los ?ltimos 1000 eventos globales, en orden de registro, bajo sincronizaci?n para consumidores concurrentes. Los eventos son inmutables. snapshot(pedidoId, afterSequence, limit) devuelve capacidad, ?ltimo n?mero de secuencia global y una lista inmutable; limit debe estar entre 1 y 1000. Un pedidoId nulo no filtra pedidos. La secuencia se reinicia al reiniciar; eventId es UUID. El cliente futuro debe detectar reinicios y p?rdidas por retenci?n (conviene a?adir instanceId y earliestSequence al contrato HTTP). No usar lastSequence como cursor tras una p?gina truncada: usar la secuencia del ?ltimo evento entregado.

Es memoria vol?til de una sola instancia: se pierde al reiniciar y no agrega r?plicas. No es auditor?a durable. El almacenamiento acotado puede descartar pasos previos de un pedido. Los fallos ordinarios de registro no alteran el procesamiento. No se guardan cuerpos de mensajes, excepciones, JWT, credenciales ni datos personales. Los metadatos de exchange/routing key provienen de RabbitMQ; no se capturan headers arbitrarios.

## Sem?ntica

- PUBLICATION_REQUESTED: se iba a invocar convertAndSend. No demuestra retorno exitoso, aceptaci?n por el broker ni enrutamiento. Cola y consumidor desconocidos se dejan nulos.
- RECEIVED: el listener fue invocado con una entrega real.
- PROCESSING_ATTEMPT: inicio de intento local (1, 2 o 3).
- PROCESSING_ERROR: RetryTemplate observ? un fallo en ese intento. No se almacena su mensaje de excepci?n.
- PROCESSING_SUCCEEDED: termin? el procesamiento del intento, antes de ACK.
- ACK_SENT / NACK_SENT: la llamada al Channel retorn? sin excepci?n. No prueban recepci?n de la confirmaci?n por el broker. NACK conserva multiple=false y requeue=false; no demuestra llegada a una DLQ.

Los tres intentos son locales, no tres redeliveries. Se conserva el comportamiento previo incluso si falla basicAck: su excepci?n entra en la rama de basicNack existente. Una llamada ACK/NACK que lanza excepci?n nunca se registra como enviada.

El ID del pedido se reconoce ?nicamente en el formato completo producido actualmente: `Se ha creado el pedido con ID: <n?mero>`. Mensajes externos, desconocidos o pruebas PRUEBA_ERROR pueden tener pedidoId nulo. No se cambi? el cuerpo, propiedades o correlaci?n AMQP. No hay identificador de mensaje entre publicaciones y entregas: la agrupaci?n actual es por pedido cuando se reconoce su ID, sin afirmar identidad de entrega.

DeadLetterConsumer conserva autoStartup=false por defecto. Solo registra recepci?n y ACK si ya hubiera sido habilitado externamente y fuera invocado; esta etapa no lo habilita, no consume DLQ para observarlas y no infiere su llegada desde NACK. Las pruebas invocan directamente los m?todos sin iniciar listeners.

## Antecedente: propuesta de integraci?n segura (BFF pendiente)

Orders no tiene Spring Security ni Resource Server JWT en el POM actual. No publicar ahora un endpoint de historial sin protecci?n.

En una etapa posterior: a?adir GET privado /api/orders/messaging/events?pedidoId=...&afterSequence=...&limit=..., validaci?n JWT de issuer y audiencia Entra y autorizaci?n Admin expl?cita. Conservar las reglas existentes de pedidos al introducir SecurityFilterChain, mediante revisi?n y pruebas de regresi?n: no aplicar indiscriminadamente una nueva pol?tica a todas las rutas. BFF expondr?a GET /api/bff/rabbitmq/events, exigir?a Admin y reenviar?a el bearer token validado a Orders, con timeouts, errores sanitizados y Cache-Control:no-store. Agregar instanceId, earliestSequence y cursor siguiente al DTO de paginaci?n.

Orders debe seguir accesible solo por red privada desde BFF; no crear ruta p?blica directa de Gateway a Orders. La exposici?n de BFF por API Gateway requiere authorizer JWT y CORS del frontend. No usar credenciales RabbitMQ para consultar este historial. Nada de esa integraci?n, dependencias de seguridad, AWS o Compose se cambi? en esta etapa.

## Validaci?n local

mvn package -Dtest=MessagingEventsTest compila y ejecuta pruebas sin PostgreSQL ni RabbitMQ. Los mensajes de fallo usados en pruebas son entradas controladas y no se insertan en el historial de la aplicaci?n en ejecuci?n.

## Endpoint privado implementado

GET /api/orders/messaging/events?after=0&limit=100&pedidoId=42

pedidoId es opcional y positivo. after es una secuencia exclusiva, >=0; por defecto 0. limit es 1..1000, por defecto 100. Sin filtro se recorre el historial retenido en orden ascendente, incluyendo los eventos m?s recientes dentro de la retenci?n. Para continuar usar nextAfter; no usar lastSequence si la p?gina est? limitada. Si no hay resultados, nextAfter conserva after. Par?metros inv?lidos devuelven 400 con INVALID_PARAMETERS sin reproducir su contenido.

Respuesta: instanceId (UUID por historial/instancia), capacity, earliestSequence (primera secuencia retenida o lastSequence+1 si vac?o), lastSequence, nextAfter, truncated y events. truncated indica que el cursor precede eventos descartados globalmente; no asegura que fueran del pedido filtrado. Si cambia instanceId, reiniciar el cursor. Un after superior a lastSequence produce una lista vac?a: el cliente debe comprobar instanceId. Cache-Control: no-store se aplica tanto a ?xito como a par?metros inv?lidos.

Se a?adi? ?nicamente spring-boot-starter-security-oauth2-resource-server, sin versiones ajenas al BOM existente. La cadena @Order(1) protege /api/orders/messaging y sus subrutas. Solo GET exacto /events permite ROLE_Admin; otras rutas/m?todos se deniegan. Sin bearer o bearer inv?lido: 401; JWT v?lido sin Admin: 403. Claim roles -> ROLE_*. El Resource Server valida firma, expiraci?n, issuer y audiencia mediante la configuraci?n est?ndar compatible con BFF.

ENTRA_JWT_ISSUER_URI y ENTRA_JWT_AUDIENCE permiten sustituir los valores predeterminados de la API Entra del BFF. El arranque/primera validaci?n puede necesitar acceso HTTPS a metadatos y claves de Entra; no se verific? conectividad real con AWS o Entra.

No hay cadena fallback ni permitAll global: las rutas previas de pedidos quedan fuera de la cadena espec?fica y mantienen el acceso interno previo, sin nuevos requisitos JWT o CSRF. Esto NO protege los endpoints de negocio frente a acceso directo: Orders debe permanecer exclusivamente en la red interna de servicios autorizados y el BFF sigue aplicando su autorizaci?n. No publicar Orders ni puertos nuevos ni crear una ruta directa desde Internet. No se cambi? red, Compose ni AWS.

La integraci?n BFF del historial sigue pendiente de autorizaci?n. Los p?rrafos de propuesta anteriores describen la evoluci?n prevista; la validaci?n JWT y el endpoint privado de Orders ya est?n implementados.

Pruebas: mvn package -Dtest=MessagingSecurityTest,MessagingEventsTest. La prueba MVC usa JwtDecoder controlado, historial real en memoria y PedidoService sustituido; verifica filtros de seguridad y regresi?n de rutas sin JWT/CSRF, sin escribir datos reales. No sustituye una validaci?n extremo a extremo con Entra, PostgreSQL y RabbitMQ. La prueba contextLoads existente contin?a requiriendo infraestructura externa.
