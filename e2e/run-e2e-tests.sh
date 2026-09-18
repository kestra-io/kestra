#!/bin/bash
set -e
KESTRA_DOCKER_IMAGE_TO_TEST="kestra/kestra:develop-slim"

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

cd "$(dirname "${BASH_SOURCE[0]}")"

cleanup() {
  echo "Stop the backend"
  ./stop-e2e-tests-backend.sh
}
trap 'cleanup' EXIT

# This package is installed on its own, so a caller that only ran `npm ci` in ui/ still works.
[ -d node_modules ] || npm ci

# The browser revision is tied to the Playwright version this package resolves, which is not
# necessarily the one whoever installed the browsers used; a present revision makes this a no-op.
npx playwright install chromium

echo "Start backend"
./start-e2e-tests-backend.sh --kestra-docker-image-to-test $KESTRA_DOCKER_IMAGE_TO_TEST

echo "Run tests"
npm run test:e2e-without-starting-backend

exit 0
