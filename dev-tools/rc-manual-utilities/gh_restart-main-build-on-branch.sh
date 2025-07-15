#!/bin/bash

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

echo "delete cache and restart build for '$REPO' on branch '$BRANCH'..."
sh ./empty-cache.sh $REPO
sh ./launch-release-workflow.sh $REPO $BRANCH
echo "delete and restart done for '$REPO' on branch '$BRANCH'..."
