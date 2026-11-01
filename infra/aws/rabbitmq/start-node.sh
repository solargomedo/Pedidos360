#!/bin/sh
set -eu
# Shared by the two fresh EC2 nodes. Never log or replace a different cookie.
source_cookie=/run/secrets/rabbitmq_cluster_cookie
target_cookie=/var/lib/rabbitmq/.erlang.cookie
test -s "$source_cookie"
if [ -e "$target_cookie" ]; then
    if ! cmp -s "$source_cookie" "$target_cookie"; then
        echo 'Cookie mismatch: refusing to start RabbitMQ.' >&2
        exit 1
    fi
else
    install -o rabbitmq -g rabbitmq -m 0600 "$source_cookie" "$target_cookie"
fi
# Only rabbit1 receives definitions. Copy with restrictive permissions so the
# unprivileged RabbitMQ process can read a root-only host secret.
if [ "${RABBITMQ_NODENAME:-}" = 'rabbit@rabbit1' ]; then
    test -s /run/secrets/rabbitmq_definitions
    install -o rabbitmq -g rabbitmq -m 0600 \
        /run/secrets/rabbitmq_definitions /var/lib/rabbitmq/bootstrap-definitions.json
fi
exec /usr/local/bin/docker-entrypoint.sh "$@"
