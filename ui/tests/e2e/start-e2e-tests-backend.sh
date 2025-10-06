#!/bin/bash
set -e

# Default values
KESTRA_DOCKER_IMAGE_TO_TEST="kestra/kestra:develop-no-plugins"
E2E_TEST_CONFIG_DIR="$(dirname "${BASH_SOURCE[0]}")"
KESTRA_BASE_URL="http://127.0.0.1:9011/ui/"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
    --kestra-docker-image-to-test)
      KESTRA_DOCKER_IMAGE_TO_TEST="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1"
      exit 1
      ;;
  esac
done

echo "Start E2E backend with:"
echo "  KESTRA_DOCKER_IMAGE_TO_TEST=$KESTRA_DOCKER_IMAGE_TO_TEST"

# Prepare configuration
mkdir -p "$E2E_TEST_CONFIG_DIR/data"
touch "$E2E_TEST_CONFIG_DIR/data/application-secrets.yml"

# Start Docker Compose (default to postgres backend)
cd "$E2E_TEST_CONFIG_DIR"
echo "KESTRA_DOCKER_IMAGE=$KESTRA_DOCKER_IMAGE_TO_TEST" > .env
docker compose -f "docker-compose-postgres.yml" up -d

# Wait for Kestra UI
echo "Waiting for Kestra UI at $KESTRA_BASE_URL"

START_TIME=$(date +%s)
TIMEOUT_DURATION=$((2 * 60))

while [ "$(curl -s -L -o /dev/null -w '%{http_code}' $KESTRA_BASE_URL)" != "200" ]; do
  echo -e "$(date)\tKestra server HTTP state: $(curl -k -L -s -o /dev/null -w '%{http_code}' $KESTRA_BASE_URL) (waiting for 200)"
  CURRENT_TIME=$(date +%s)
  ELAPSED_TIME=$((CURRENT_TIME - START_TIME))

  if [ $ELAPSED_TIME -ge $TIMEOUT_DURATION ]; then
    echo "Timeout reached: Exiting after 5 minutes."
    echo "printing 'docker logs kestra-e2e-backend'"
    docker logs kestra-e2e-backend
    exit 1
  fi

  sleep 2
done

echo "Kestra is running: $KESTRA_BASE_URL 🚀"

exit 0