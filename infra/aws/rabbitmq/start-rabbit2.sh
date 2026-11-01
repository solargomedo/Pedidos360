#!/bin/sh
set -eu
# Never print the shared secret or replace an existing node's cookie.
source_cookie=/run/secrets/rabbitmq_cluster_cookie
target_cookie=/var/lib/rabbitmq/.erlang.cookie
test -s "$source_cookie"
if [ -e "$target_cookie" ]; then
    if ! cmp -s "$source_cookie" "$target_cookie"; then
        echo 'Cookie mismatch: refusing to start rabbit2.' >&2
        exit 1
    fi
else
    install -o rabbitmq -g rabbitmq -m 0600 "$source_cookie" "$target_cookie"
fi
exec /usr/local/bin/docker-entrypoint.sh "$@"
