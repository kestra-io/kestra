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

# Pull the images needed later (docker build base + compose services) in the
# background so the downloads overlap with the Gradle/npm build instead of
# sitting on the critical path.
BASE_IMAGE="$(sed -n 's/^ARG BASE_IMAGE="\(.*\)"/\1/p' Dockerfile)"
docker pull -q "${BASE_IMAGE:-ghcr.io/kestra-io/kestra-base:latest-no-plugins}" > /dev/null 2>&1 &
docker pull -q postgres > /dev/null 2>&1 &
docker pull -q docker:dind-rootless > /dev/null 2>&1 &

if [ -n "$CI" ]; then
  # CI runners start from a fresh checkout (nothing to clean) and the workflow
  # has already run `npm ci`, so skip both.
  make build-docker VERSION=$LOCAL_IMAGE_VERSION SKIP_NPM_CI=true
else
  ./gradlew clean -q
  make build-docker VERSION=$LOCAL_IMAGE_VERSION
fi

# Let any still-running background pull finish before compose starts.
wait

end_time=$(date +%s)
elapsed=$(( end_time - start_time ))

echo ""
echo "building elapsed time: ${elapsed} seconds"
echo ""
echo "Start time: $(date '+%Y-%m-%d %H:%M:%S')"
start_time2=$(date +%s)

echo "cd ./ui"
cd ./ui

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