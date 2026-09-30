#!/bin/bash
set -e

E2E_TEST_CONFIG_DIR="$(dirname "${BASH_SOURCE[0]}")"

echo "Stopping backend for E2E tests"
cd "$E2E_TEST_CONFIG_DIR"
docker compose -f "docker-compose-postgres.yml" down
echo "Backend stopped"

exit 0