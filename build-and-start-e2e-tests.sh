#!/bin/bash
set -e

# E2E main script that can be run on a dev computer or in the CI
# it will build the backend of the current git repo and the frontend
# create a docker image out of it
# run tests on this image


LOCAL_IMAGE_VERSION="local-e2e-$(date +%s)"

echo "Running E2E"
echo "Start time: $(date '+%Y-%m-%d %H:%M:%S')"
start_time=$(date +%s)

echo ""
echo "Building the image for this current repository"

if [ "${E2E_USE_PREBUILT_EXE:-false}" = "true" ]; then
  # CI already downloaded a prebuilt executable into build/executable
  # (the Build Artifacts job's artifact), so only the image remains to build.
  make build-docker-from-exec VERSION=$LOCAL_IMAGE_VERSION
elif [ -n "$CI" ]; then
  # CI runners start from a fresh checkout, so there is nothing to clean.
  make build-docker VERSION=$LOCAL_IMAGE_VERSION
else
  make clean
  make build-docker VERSION=$LOCAL_IMAGE_VERSION
fi

end_time=$(date +%s)
elapsed=$(( end_time - start_time ))

echo ""
echo "building elapsed time: ${elapsed} seconds"
echo ""
echo "Start time: $(date '+%Y-%m-%d %H:%M:%S')"
start_time2=$(date +%s)

echo "cd ./ui"
cd ./ui
echo "npm ci"
npm ci

echo 'sh ./run-e2e-tests.sh --kestra-docker-image-to-test "kestra/kestra:$LOCAL_IMAGE_VERSION"'
./run-e2e-tests.sh --kestra-docker-image-to-test "kestra/kestra:$LOCAL_IMAGE_VERSION"

end_time2=$(date +%s)
elapsed2=$(( end_time2 - start_time2 ))
echo ""
echo "Tests elapsed time: ${elapsed2} seconds"
echo ""
total_elapsed=$(( elapsed + elapsed2 ))
echo "Total elapsed time: ${total_elapsed} seconds"
echo ""

exit 0