#!/bin/bash
set -e

cleanup() {
  echo "This runs at the end (like finally)"
  cd ./tests/e2e
  ./stop-e2e-tests-backend.sh
  cd ../..
}
trap 'cleanup' EXIT

cd ./tests/e2e
./start-e2e-tests-backend.sh
cd ../..

npm run test:e2e-without-starting-backend

exit 0