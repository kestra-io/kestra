#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="kestra/td-agent:local"

# Validate required env vars.
missing=()
[[ -z "${TEST_DISTRIBUTION_AGENT_SERVER:-}"           ]] && missing+=("TEST_DISTRIBUTION_AGENT_SERVER")
[[ -z "${TEST_DISTRIBUTION_AGENT_POOL:-}"             ]] && missing+=("TEST_DISTRIBUTION_AGENT_POOL")
[[ -z "${TEST_DISTRIBUTION_AGENT_REGISTRATION_KEY:-}" ]] && missing+=("TEST_DISTRIBUTION_AGENT_REGISTRATION_KEY")

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "ERROR: missing required env var(s): ${missing[*]}" >&2
  echo "All three are generated in the Develocity admin panel under Test Distribution > Agent Pools." >&2
  exit 1
fi

# Build the custom image if it doesn't exist yet.
if ! docker image inspect "${IMAGE_NAME}" &>/dev/null; then
  echo "Building agent image (JDK 25)..."
  docker build -f "${SCRIPT_DIR}/Dockerfile.td-agent" -t "${IMAGE_NAME}" "${SCRIPT_DIR}"
fi

echo "Starting test distribution agent (Ctrl-C to stop)..."
docker run --rm \
  -e TEST_DISTRIBUTION_AGENT_SERVER \
  -e TEST_DISTRIBUTION_AGENT_POOL \
  -e TEST_DISTRIBUTION_AGENT_REGISTRATION_KEY \
  -v /var/run/docker.sock:/var/run/docker.sock \
  "${IMAGE_NAME}"
