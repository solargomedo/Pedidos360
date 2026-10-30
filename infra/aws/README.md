# Integracion local Pedidos360 y preparacion para EC2

Este Compose prepara PostgreSQL, Catalog, Orders y un RabbitMQ independiente.
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

Ningun servicio publica puertos al host. Ambas redes son `internal: true`:
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

El volumen persistente no sustituye respaldos externos. Los futuros BFF y clientes
Entra requeriran otra red con salida; no agregarlos solamente a una red internal.
No se ha desplegado ni creado ningun recurso AWS con estos archivos.

## Git

`.gitignore` excluye `.env`, variantes, `secrets/`, `backups/`, `*.dump` y
`*.backup`, conservando `.env.example`. No colocar respaldos ni secretos en otras
rutas del repositorio. Los ignores no eliminan archivos previamente versionados.
El script shell mantiene LF mediante `.gitattributes`.
