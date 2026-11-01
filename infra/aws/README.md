# Integracion local Pedidos360 y preparacion para EC2

Este Compose prepara PostgreSQL, Catalog, Orders, un RabbitMQ independiente y el BFF.
Su edicion y validacion no crean recursos. El inicio requiere autorizacion separada.
Los comandos se ejecutan desde la raiz del repositorio. Los ejemplos con stdin
binario y redireccion `<` corresponden a una shell Linux, no a PowerShell.

## Recursos y datos existentes

Proyecto fijo: `pedidos360-aws`. No cambiarlo con `-p` o `COMPOSE_PROJECT_NAME`.

| Servicio | Imagen | Redes | Puerto interno |
|---|---|---|---|
| postgres | postgres:16-alpine | datos | 5432 |
| catalog | pedidos360-catalog:local | datos, aplicacion | 8082 |
| orders | pedidos360-orders:local | datos, aplicacion | 8081 |
| rabbitmq | rabbitmq:4.1-management | aplicacion | 5672, Management 15672 |
| bff | pedidos360-bff:local | aplicacion, bff_salida | 8080 |

Solo el BFF publica `127.0.0.1:8080:8080`; los cuatro servicios existentes no
publican puertos. Las redes `datos` y `aplicacion` mantienen `internal: true`:
`pedidos360-aws_datos` y `pedidos360-aws_aplicacion`. No conectar redes externas,
el cluster RabbitMQ original ni `host.docker.internal` a esta prueba.

PostgreSQL conserva exactamente su bloque de servicio y el volumen
`pedidos360-aws_postgres_data`, montado en `/var/lib/postgresql/data`. Las bases
`pedidos360` y `pedidos360_orders` ya estan restauradas en el entorno local: el
ultimo conteo verificado fue 1 producto y 9 pedidos. No restaurar de nuevo sobre
ellas. Los roles limitados ya existen: `pedidos360_catalog_app` y
`pedidos360_orders_app`, cada uno con acceso a su base y sin acceso a la otra.
Los archivos Compose no crean ni alteran esos roles o sus permisos.

El nuevo broker tendra su propio contenedor `pedidos360-aws-rabbitmq-1` y volumen
`pedidos360-aws_rabbitmq_integration_data`. No usa volumenes, cookie compartida,
nodos semilla ni ordenes de union al cluster original. Nunca reutilizar el volumen
RabbitMQ original ni sus credenciales. Su usuario y vhost se proporcionan
explicitamente; no usar `guest`, `/` ni el vhost `pedidos360-dlq-test` del cluster
original. Estos nombres de entorno son comprobaciones operativas: Compose exige
valores no vacios, pero no impide que se introduzcan nombres equivocados.

## Variables y secretos

`.env.example` contiene nombres de variables, sin claves. Proporcionar en el
entorno temporal del proceso Compose, idealmente mediante campos protegidos:

- `POSTGRES_USER` y `POSTGRES_PASSWORD`: administrador EXISTENTE de PostgreSQL.
- `CATALOG_DB_PASSWORD`: clave EXISTENTE de `pedidos360_catalog_app`.
- `ORDERS_DB_PASSWORD`: clave EXISTENTE de `pedidos360_orders_app`.
- `INTEGRATION_RABBITMQ_USER`, `INTEGRATION_RABBITMQ_PASSWORD` y
  `INTEGRATION_RABBITMQ_VHOST`: identidad NUEVA exclusiva del broker independiente.

No crear archivos con secretos ni variables permanentes. Los secretos no deben
aparecer en argumentos, historial o logs. Cada contenedor recibe solo las claves
que necesita: las aplicaciones no reciben la clave administrativa PostgreSQL.
Docker conserva variables en sus metadatos, accesibles a sus administradores.
No mostrar `docker inspect` completo, `compose config` interpolado ni
`config --environment` con valores reales.

Compose puede exigir todas las variables aunque se seleccione un servicio.
Usar un proceso con entorno controlado para evitar overrides heredados, perfiles
locales o archivos Compose adicionales. No iniciar con credenciales inventadas.
Cambiar las variables no restablece contrasenas dentro de volumenes ya inicializados.

## Configuracion de las aplicaciones

Las imagenes locales deben existir; `pull_policy: never` impide descargarlas por
accidente desde un registro. No se compila codigo durante el inicio de Compose.
Ambas cargan solo `classpath:/application.properties`, perfil `integracion-test`,
`ddl-auto=validate` y SQL init `never`. No activan `local`. Las URL JDBC son:

- Catalog: `jdbc:postgresql://postgres:5432/pedidos360`.
- Orders: `jdbc:postgresql://postgres:5432/pedidos360_orders`.

Orders llama a Catalog en `http://catalog:8082`. Para AMQP usa unicamente el DNS
`rabbitmq`, puerto 5672 y el vhost/credenciales nuevos explicitamente configurados.
Los consumidores principales y los tres DLQ permanecen detenidos. El
`CommandLineRunner` de Orders abre una conexion y RabbitAdmin puede declarar sus
exchanges, colas y enlaces en el broker NUEVO, aunque los consumidores no arranquen.
No se deshabilita esa conexion ni se conecta al broker original.

Las politicas TTL/max-length existentes en el cluster original NO se copian al
broker nuevo. Su provisionamiento requiere un paso separado y autorizado.

## Healthchecks y dependencias

- PostgreSQL: `pg_isready` TCP; comprueba disponibilidad, no credenciales/tablas.
- RabbitMQ: `rabbitmq-diagnostics check_running`; comprueba la aplicacion del nodo,
  no una autenticacion AMQP con las credenciales de Orders.
- Catalog y Orders: comprobacion TCP del puerto local con Bash de la imagen JRE;
  no invoca endpoints de negocio ni comprueba funcionalidad completa.
- Catalog espera PostgreSQL healthy. Orders espera PostgreSQL, Catalog y RabbitMQ
  healthy. Estas dependencias ordenan el inicio; no garantizan recuperacion si una
  dependencia falla despues. El arranque real de estas imagenes queda por probar.

## Presupuesto inicial de recursos

Catalog y Orders: limite 512 MiB y 0.50 CPU cada uno, heap Java maximo 60% del
limite, heap inicial 10%, salida ante OOM. Cada pool Hikari tiene maximo 3
conexiones y minimo idle 1. RabbitMQ: limite 768 MiB y 0.50 CPU. Son puntos de
partida para una demostracion, NO valores de capacidad validados.

PostgreSQL no recibe nuevos limites: se conserva su configuracion actual.
Los limites suman 1792 MiB sin contar PostgreSQL, sistema operativo, Docker,
cache de disco ni futuros servicios. No asumir que el conjunto cabe en EC2 con
2 GiB. Medir arranque, memoria nativa/heap, conexiones, CPU y carga antes de elegir
instancia o trasladar limites. Limitar CPU no garantiza evitar consumo de creditos.
Construir imagenes fuera de EC2; no ejecutar Maven, Angular o watch dentro de las
imagenes de ejecucion. `restart: "no"` en los nuevos servicios evita bucles de
reinicio durante esta primera prueba; PostgreSQL conserva `unless-stopped`.

## Apagado y watermark del RabbitMQ de integracion

Se fija `hostname: 6ce32d690a78` para conservar la identidad existente
`rabbit@6ce32d690a78` y reutilizar su estado en el volumen persistente.
Los logs del nodo anterior confirman esa identidad en su directorio Mnesia.
No cambiar el hostname al recrear este contenedor: RabbitMQ deriva de el su
nombre de nodo y, por defecto, la ruta de datos; otro nombre podria iniciar
un estado vacio aunque se conserve el mismo volumen.

El servicio `rabbitmq` usa `stop_grace_period: 60s`. En la prueba anterior el
contenedor registraba un timeout de 1 segundo: recibio SIGTERM, inicio el apagado
y Docker envio SIGKILL aproximadamente un segundo despues (salida 137), con
`OOMKilled=false`. El plazo de 60 segundos da margen para cerrar conexiones y
almacenes de mensajes. No garantiza que todos los apagados terminen en ese plazo;
medir su duracion y revisar logs/eventos antes de ajustar nuevamente.

Se configura `RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS` con:

```text
-rabbit vm_memory_high_watermark {absolute,419430400}
```

Es el parametro Erlang de RabbitMQ 4.1 para un umbral absoluto de 400 MiB
(419430400 bytes). Se conserva `mem_limit: 768m` (805306368 bytes). Antes, el nodo
habia calculado 4688 MiB a partir de la memoria del host, por encima del limite del
contenedor. El umbral absoluto evita depender de esa deteccion. Activa una alarma
y frena publicadores al superar el watermark; no es un tope duro de memoria ni
una garantia contra OOM. El margen nominal hasta el limite Docker es de 368 MiB,
pero el consumo real y transitorio debe medirse.

Ambos valores son iniciales: validar watermark efectivo, alarmas, consumo y tiempo
de apagado bajo carga antes del despliegue en AWS. No cambiar PostgreSQL, Orders,
Catalog ni el cluster original para aplicar este ajuste.

Editar Compose no cambia el contenedor existente. Una futura aplicacion de esta
configuracion puede requerir recrear UNICAMENTE el contenedor RabbitMQ del proyecto,
conservando su volumen y sus credenciales. Esa operacion requiere autorizacion
separada; `start` no aplica nuevas variables a un contenedor ya creado. No usar
`down -v`, `prune` ni eliminar el volumen para aplicar estos ajustes.

Referencias:
- https://www.rabbitmq.com/docs/4.1/memory
- https://www.rabbitmq.com/docs/4.1/configure

## Validar sin credenciales ni recursos

```sh
docker compose -f infra/aws/compose.yaml config --no-interpolate --quiet
```

No crea contenedores, redes o volumenes y no valida passwords, datos o conectividad.
Revisar las reglas Git antes de cualquier commit.

## Inicio futuro, solo despues de autorizacion

Confirmar volumen PostgreSQL, datos, roles, imagenes locales y ausencia de recursos
RabbitMQ del proyecto antes del primer inicio. Proporcionar las variables requeridas
en memoria y conservar PostgreSQL sin recrearlo durante la primera integracion:

```sh
docker compose -f infra/aws/compose.yaml up -d --no-recreate postgres rabbitmq catalog orders
docker compose -f infra/aws/compose.yaml ps
```

Verificar logs sin secretos, conexiones JDBC con usuarios limitados, AMQP al vhost
nuevo, seis colas del broker nuevo y cero consumidores. No publicar mensajes ni
invocar endpoints de escritura durante esta comprobacion. No abrir puertos para
facilitarla; las consultas se haran desde el entorno Docker.

## Detener conservando datos

```sh
docker compose -f infra/aws/compose.yaml stop orders catalog rabbitmq postgres
```

Para detener solo PostgreSQL, primero asegurar que las aplicaciones ya esten
paradas y ejecutar `stop postgres`. Contenedores, volumenes y redes permanecen.
**No usar `docker compose down -v`, `docker volume prune` ni limpiezas globales.**
Eliminar datos requiere autorizacion expresa y respaldo verificado.

## Inicializacion y restauracion en un destino nuevo

El script LF `postgres/init/01-create-orders-db.sh` se monta solo lectura.
`POSTGRES_DB=pedidos360` crea la primera base y el script crea `pedidos360_orders`.
Solo se ejecuta al inicializar un directorio de datos vacio. Los roles limitados y
la restauracion no forman parte de ese script. Si falla la inicializacion, detenerse
sin borrar el volumen ni reintentar automaticamente.

Para un destino futuro VACIO, iniciar primero solo PostgreSQL y mantener Orders y
Catalog detenidos. Guardar los respaldos fuera del checkout, transferirlos de forma
protegida y verificar sus SHA-256. Restaurar uno por uno comprobando codigo 0:

```sh
docker compose -f infra/aws/compose.yaml exec -T postgres \
  sh -c 'exec pg_restore --username="$POSTGRES_USER" --dbname=pedidos360 \
    --no-owner --no-privileges --single-transaction --exit-on-error' \
  < /ruta-protegida/pedidos360.dump

docker compose -f infra/aws/compose.yaml exec -T postgres \
  sh -c 'exec pg_restore --username="$POSTGRES_USER" --dbname=pedidos360_orders \
    --no-owner --no-privileges --single-transaction --exit-on-error' \
  < /ruta-protegida/pedidos360_orders.dump
```

Estos comandos contienen stdin binario en shell Linux. No usar pipelines de texto
PowerShell para los dumps. Nunca restaurar sobre las bases locales ya pobladas.
`pg_restore` recupera automaticamente el estado de las secuencias mediante setval
incluidos en el dump. No usar nextval/setval manuales en verificaciones.
Despues crear roles limitados, revocar CONNECT/TEMPORARY de PUBLIC en ambas bases y
conceder permisos por base, tabla y secuencia mediante procedimiento autorizado.
`--no-owner --no-privileges` no reproduce automaticamente propietarios ni permisos.

El volumen persistente no sustituye respaldos externos. El BFF usa una red adicional con salida para consultar Microsoft Entra;
no depende exclusivamente de redes internal.
No se ha desplegado ni creado ningun recurso AWS con estos archivos.

## Git

`.gitignore` excluye `.env`, variantes, `secrets/`, `backups/`, `*.dump` y
`*.backup`, conservando `.env.example`. No colocar respaldos ni secretos en otras
rutas del repositorio. Los ignores no eliminan archivos previamente versionados.
El script shell mantiene LF mediante `.gitattributes`.

## BFF Docker: Angular hacia Catalog y Orders

El BFF comparte `aplicacion` con Catalog y Orders y usa exclusivamente
`bff_salida` como red adicional con salida (`internal: false`) para consultar los
metadatos y claves publicas de Microsoft Entra por HTTPS. Ningun otro servicio
se agrega a esa red. El BFF no pertenece a `datos` ni recibe claves PostgreSQL
o RabbitMQ. Esta separacion de redes no sustituye controles de firewall de salida.

`CATALOG_SERVICE_URL=http://catalog:8082` y
`ORDERS_SERVICE_URL=http://orders:8081` reemplazan los destinos localhost mediante
las variables que ya admite la aplicacion. Solo se publica el BFF en
`127.0.0.1:8080`; Angular local mantiene `http://localhost:8080/api/bff/...`.
JWT, issuer, audiencia, roles y CORS se conservan en el codigo y la configuracion
existentes. No se activa el perfil local ni se agregan secretos al Compose.

La imagen sigue el patron de Catalog: build Maven con Java 17, runtime JRE 17,
usuario `10001:10001` y JAR de solo lectura. `.dockerignore` limita el contexto a
Maven y fuentes principales, excluyendo configuracion local, secretos y respaldos.
El limite inicial es 512 MiB y 0.50 CPU, heap maximo 60% e inicial 10%; medir antes
de EC2. Se agregan 512 MiB al presupuesto anterior (2304 MiB sin PostgreSQL ni host).
El healthcheck TCP solo comprueba el puerto, no JWT ni llamadas a otros servicios.

Los siguientes comandos son para un paso POSTERIOR AUTORIZADO, desde la raiz:

```sh
docker build -t pedidos360-bff:local ./ms-pedidos360-bff
docker compose -f infra/aws/compose.yaml -p pedidos360-aws up -d --no-deps bff
```

Antes de iniciar, verificar que 8080 este libre, que Catalog y Orders ya esten
healthy y proporcionar las variables existentes al entorno temporal de Compose,
sin imprimirlas. `--no-deps` evita iniciar o recrear sus dependencias; no ejecutar
un `up` global para incorporar el BFF. La red nueva se creara solo al iniciar.
Despues verificar 401 sin JWT y acceso de lectura con JWT real segun rol, sin
registrar tokens ni realizar operaciones de escritura. Para detener solo el BFF:

```sh
docker compose -f infra/aws/compose.yaml -p pedidos360-aws stop bff
```

La creacion y validacion de estos archivos no construye imagenes ni opera
contenedores. La conectividad real con Entra y el flujo completo quedan pendientes.

## Segundo nodo preparado: aun no formar el cluster

El servicio existente sigue siendo `rabbitmq` (identificado como rabbit1 por su
alias). Conserva hostname `6ce32d690a78`, nodo `rabbit@6ce32d690a78`, imagen,
credenciales, vhost y volumen `rabbitmq_integration_data`. No se renombra el
servicio ni se restablece el nodo. Orders sigue usando el DNS `rabbitmq`.
Los alias nuevos `rabbit1` y `6ce32d690a78` permiten resolver la identidad original.
Cambiar Compose NO aplica esos alias al contenedor ya activo: antes del arranque
de rabbit2 comprobar su resolucion; aplicarlos puede requerir recrear SOLO el
contenedor original, en una ventana separada y autorizada, conservando su volumen.
No ejecutar un up global ni asumir que los alias ya estan disponibles.

`rabbit2` tendra identidad `rabbit@rabbit2`, volumen propio `rabbit2_data`, sin
puertos publicados ni acceso a datos. Ambos usan rabbitmq:4.1-management; antes de
arrancar, verificar que la imagen local corresponde exactamente a 4.1.8 y al mismo
Erlang del nodo original, sin pull ni actualizaciones implicitas. El segundo nodo
usa descubrimiento classic_config hacia el nodo existente. No se declaran usuarios
ni vhost nuevos en rabbit2: al unirse recibe las definiciones existentes.

Preparar, SOLO con autorizacion futura, una copia binaria de la cookie EXISTENTE
del broker en una ubicacion protegida FUERA de Git. No generar otra cookie ni usar
la del cluster original de otro entorno. Definir `RABBITMQ_CLUSTER_COOKIE_FILE`
con su ruta absoluta en el entorno temporal de Compose, nunca con su contenido.
Compose monta el archivo como secret de solo lectura; esto no cifra el archivo en
el host. Restringir sus permisos/ACL y no imprimirlo. `secrets/` ya esta ignorado,
pero la ubicacion recomendada es externa al repositorio. No se crea ningun secreto
con estos cambios. Compose puede exigir la ruta incluso para otros servicios.

El script de rabbit2 instala la cookie con propietario rabbitmq y modo 0600 solo
si no existe; ante una cookie distinta falla sin reemplazarla. Luego delega en el
entrypoint oficial, que inicia el servidor como rabbitmq. No ejecuta reset,
force_boot ni comandos destructivos. Un nodo saludable no garantiza que se haya
unido: si no encuentra su peer podria quedar standalone; detener la prueba e
informar sin reintentos correctivos ni borrar su volumen.

Pendiente bajo autorizacion: comprobar respaldos, imagen exacta y DNS/cookie;
iniciar SOLO rabbit2; consultar cluster_status desde ambos nodos y exigir los dos
running_nodes, sin particiones ni alarmas; comparar usuario, vhost, permisos,
seis colas, cantidades, exchanges y bindings con el respaldo; verificar Orders.
No se crean ni eliminan colas ni mensajes como parte de la preparacion.

Cada broker conserva limite 768 MiB, watermark 400 MiB y parada 60s. Con rabbit2,
los limites suman 3072 MiB sin PostgreSQL ni sistema operativo: comprobar memoria
y recursos permitidos por Learner Lab antes de EC2. Dos nodos no replican los
mensajes classic automaticamente. Para la pauta, rabbit1 es alias del primer
servicio; su nodename persistido se conserva deliberadamente.

Referencia: https://www.rabbitmq.com/docs/4.1/cluster-formation

## RabbitMQ Admin preparado, pendiente de construir e iniciar

Servicio `rabbitmq-admin`, imagen `pedidos360-rabbitmq-admin:local`, Java 17 JRE,
usuario 10001:10001 y build Maven multi-stage. No publica puertos al host.
`aplicacion` permite acceso HTTP a `http://rabbitmq:15672`; `admin_salida` es una
red bridge exclusiva con salida para metadatos/claves HTTPS de Microsoft Entra.
No recibe credenciales PostgreSQL ni monta volumenes o cookie Erlang.

Las seis operaciones REST y sus restricciones de borrado de recursos `prueba.`
se conservan. `RABBITMQ_MANAGEMENT_VHOST` es obligatorio y se codifica una sola vez
como segmento de URI para colas, exchanges y bindings. Compose usa el vhost de
integracion existente; no se administra `/` por defecto. Se reutilizan las
credenciales existentes mediante las variables INTEGRATION_RABBITMQ_*; comprobar
que el usuario tenga acceso Management y permisos sobre ese vhost antes de probar.
No se crean usuarios ni se modifican permisos.

Proporcionar ENTRA_JWT_ISSUER_URI y ENTRA_JWT_AUDIENCE reales en el entorno temporal,
coherentes con el BFF. `.env.example` deja los valores vacios; no crear secretos
en Git. Compose puede exigir estas variables incluso al seleccionar otro servicio.
JWT, ROLE_Admin, sesiones stateless y denegacion de otras rutas no se alteran.
Los secretos en variables Docker quedan accesibles a administradores del daemon;
no imprimir inspect completo ni config interpolado.

Presupuesto inicial: 512 MiB y 0.50 CPU, heap maximo 60%; suma configurada 3584 MiB
sin PostgreSQL ni host. El healthcheck TCP comprueba disponibilidad del puerto,
no autenticacion JWT ni conectividad Management/Entra. Estas ultimas siguen
pendientes de prueba real: no se construye ni arranca el servicio al editar.

Tras autorizacion separada: construir la imagen, iniciar SOLO rabbitmq-admin con
--no-deps cuando el broker este healthy, comprobar JWT (401/403/Admin), y probar
las seis operaciones exclusivamente con recursos `prueba.` del vhost autorizado.
La entrada futura de API Gateway a este servicio queda pendiente; no se modifican
BFF, Angular ni CORS. No usar un up global para incorporar el administrador.

## Arranque desde cero en EC2: usar DOS archivos Compose

Esta seccion rige el despliegue NUEVO en EC2. No aplicar compose.ec2.yaml a los
volumenes locales, ni restaurar el TAR del nodo local bajo rabbit@rabbit1.
El Compose base mantiene la identidad local anterior; la variante EC2 reemplaza
hostnames/descubrimiento para usar rabbit@rabbit1 y rabbit@rabbit2, sin depender
de identificadores locales. El servicio original sigue llamandose rabbitmq para
conservar las conexiones de Orders y Admin. No se renombran los volumenes ni las
redes: en EC2 seran nuevos recursos del proyecto pedidos360-aws.

Ambos servicios fijan rabbitmq:4.1.8-management. El script start-node.sh instala
la MISMA cookie externa en cada volumen vacio, con propietario rabbitmq y modo
0600, antes de delegar al entrypoint oficial. No genera otra cookie, no imprime
secretos, y falla si una cookie persistida es diferente. Solo rabbit1 recibe
las definiciones y las copia con modo 0600 para que las lea el proceso no root.
El archivo copiado contiene hashes de contrasena: proteger tambien el volumen.
Los montajes de secrets son de lectura; no equivalen a cifrado en el host.

Fuera de Git, preparar en EC2 una cookie protegida y un JSON de definiciones
ACTUALIZADO con las seis colas funcionales, exchanges, bindings, vhost, usuarios,
permisos y las DOS politicas. El respaldo inicial anterior a las politicas NO es
suficiente. Exportar/transferir requiere un paso autorizado separado. Revisar
antes del inicio que no incluya colas auxiliares y que los hashes de usuario
correspondan a las credenciales existentes que reciben Orders y Admin.
Las definiciones importan topologia, no mensajes; no se declara migracion de
mensajes locales con este procedimiento. No se guardan JSON sensibles en Git.

Proporcionar las variables existentes y estas rutas absolutas Linux en el entorno
controlado del proceso Compose (sin imprimir secretos):
- RABBITMQ_CLUSTER_COOKIE_FILE: archivo externo legible por Docker, protegido.
- RABBITMQ_DEFINITIONS_FILE: JSON externo validado, protegido.

En EC2, archivos privados con modo 0600 y directorio 0700; verificar que el daemon
pueda leerlos. Preservar finales LF en ambos scripts. Se invocan mediante /bin/sh,
por lo que no dependen del bit ejecutable del archivo transferido. Rabbit1 importa
las definiciones al arrancar; rabbit2 descubre rabbit@rabbit1 y comparte sus
metadatos. No configurar importacion independiente ni usuarios nuevos en rabbit2.
El JSON debe seguir disponible para reinicios; no editarlo como sustituto de
operaciones controladas, porque se importa en cada arranque del primer nodo.

Comandos FUTUROS, solo tras autorizacion, desde la raiz del paquete:

```sh
docker compose -f infra/aws/compose.yaml -f infra/aws/compose.ec2.yaml config --no-interpolate --quiet
docker compose -f infra/aws/compose.yaml -f infra/aws/compose.ec2.yaml -p pedidos360-aws up -d --no-deps rabbitmq
# Verificar primer nodo healthy, topologia/politicas y credenciales antes de seguir.
docker compose -f infra/aws/compose.yaml -f infra/aws/compose.ec2.yaml -p pedidos360-aws up -d --no-deps rabbit2
```

No ejecutar estos comandos sobre el Docker local. Mantener los dos archivos -f
en TODAS las operaciones del despliegue EC2. Requiere Compose >=2.24.4 por !override
(la version 2.40.3 declarada para EC2 es compatible). Los limites de memoria,
watermarks, parada 60s, redes, volumenes y conexiones previas se conservan.
Los puertos BFF/Admin aun requieren adaptar su entrada de API Gateway por separado.

Comprobar cluster_status en ambos nodos: exactamente rabbit@rabbit1 y
rabbit@rabbit2 activos, sin alarmas ni particiones; validar seis colas, DLX,
routing keys, direct/topic, bindings, politicas efectivas, vhost/permisos y
conexiones de Orders/Admin. Un healthcheck aislado no prueba la union al cluster.
No ejecutar reset, force_boot, down -v ni borrar volumenes como recuperacion.

La validacion de archivos no demuestra el arranque real: importacion, permisos de
montaje y formacion del cluster se verificaran al desplegar bajo autorizacion.
