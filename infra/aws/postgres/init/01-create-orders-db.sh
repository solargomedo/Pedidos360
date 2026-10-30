#!/bin/sh
set -eu

# La imagen oficial ejecuta este script solo al inicializar un PGDATA vacio.
# POSTGRES_DB crea pedidos360; este script crea la segunda base.
createdb \
  --username="$POSTGRES_USER" \
  --owner="$POSTGRES_USER" \
  --template=template0 \
  pedidos360_orders
