#!/usr/bin/env sh

set -e

if [ "${KESTRA_CONFIGURATION}" ]; then
    echo "${KESTRA_CONFIGURATION}" > /app/confs/application.yml
fi

exec /app/kestra "$@"
