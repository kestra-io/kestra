#!/bin/bash

# Usage: ./trigger-workflow.sh <repo> <branch>
set -e

if [ -z "$1" ]; then
  echo "Usage: $0 <repo> <branch-name>"
  exit 1
fi

if [ -z "$2" ]; then
   echo "Usage: $0 <repo> <branch-name>"
   exit 1
fi

REPO="$1"
BRANCH="$2"
OWNER="kestra-io"
WORKFLOW="main.yml"

echo "Triggering workflow '$WORKFLOW' on branch '$BRANCH'..."

gh workflow run "$WORKFLOW" \
  --repo "$OWNER/$REPO" \
  --ref "$BRANCH"

echo "Triggered. Run 'gh run list --repo $OWNER/$REPO --branch $BRANCH' to check status."

