#!/usr/bin/env bash
set -euo pipefail

bootstrap='kafka-1:9092,kafka-2:9092,kafka-3:9092'
compose_exec=(docker compose -f infra/kafka/docker-compose.yml exec -T kafka-1)
for topic in pedidos.eventos pedidos.analitica; do
  "${compose_exec[@]}" kafka-topics --bootstrap-server "$bootstrap" --create --if-not-exists \
    --topic "$topic" --partitions 3 --replication-factor 3 \
    --config cleanup.policy=delete \
    --config retention.ms=604800000 \
    --config retention.bytes=268435456 \
    --config segment.bytes=67108864 \
    --config segment.ms=3600000 \
    --config min.insync.replicas=2

  # No alterar silenciosamente un topico preexistente incompatible.
  description=$("${compose_exec[@]}" kafka-topics --bootstrap-server "$bootstrap" --describe --topic "$topic")
  printf '%s\n' "$description"
  if ! grep -Eq 'PartitionCount: 3[[:space:]]+ReplicationFactor: 3([[:space:]]|$)' <<< "$description"; then
    echo "ERROR: $topic ya existe con particiones o replicacion incompatibles; revisar manualmente." >&2
    exit 1
  fi

  # Reaplicar solamente la politica declarada: ejecucion repetible sin borrar datos.
  "${compose_exec[@]}" kafka-configs --bootstrap-server "$bootstrap" --entity-type topics \
    --entity-name "$topic" --alter \
    --add-config 'cleanup.policy=delete,retention.ms=604800000,retention.bytes=268435456,segment.bytes=67108864,segment.ms=3600000,min.insync.replicas=2'
  "${compose_exec[@]}" kafka-configs --bootstrap-server "$bootstrap" --entity-type topics \
    --entity-name "$topic" --describe
done
