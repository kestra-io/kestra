#!/bin/bash
set -e

# Runs the storybook Vitest project as N sequential shards instead of one.
# Each shard is a fresh process with its own fresh browser session, bounding
# the number of story files any single browser tab accumulates memory across
# (Monaco editor workers, created per-model and never explicitly terminated,
# are a likely contributor to that growth). This avoided a
# "[birpc] rpc is closed, cannot call \"createTesters\"" crash that persisted
# under CI load even with capped Vitest worker concurrency.
#
# Any arguments this script is called with (e.g. --coverage) are forwarded to
# every shard.
SHARD_COUNT=4

for i in $(seq 1 "$SHARD_COUNT"); do
    vitest run --project=storybook --shard="$i/$SHARD_COUNT" "$@"
done
