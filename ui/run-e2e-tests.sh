#!/bin/bash
set -e
KESTRA_DOCKER_IMAGE_TO_TEST="kestra/kestra:develop-no-plugins"

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

cleanup() {
  echo "Stop the backend"
  cd ./tests/e2e
  ./stop-e2e-tests-backend.sh
  cd ../..
}
trap 'cleanup' EXIT

cd ./tests/e2e

echo "Start backend"
./start-e2e-tests-backend.sh --kestra-docker-image-to-test $KESTRA_DOCKER_IMAGE_TO_TEST
cd ../..

echo "Run tests"
npm run test:e2e-without-starting-backend

exit 0