#!/bin/bash
set -e

echo "stopping backend for E2E tests"
docker compose -f "docker-compose-postgres.yml" down
echo "backend stopped"

exit 0