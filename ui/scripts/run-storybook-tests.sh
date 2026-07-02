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
# Coverage (or any other flag this script is called with, e.g. --coverage)
# does NOT merge across separate `vitest run` processes on its own — each
# shard would otherwise overwrite the previous one's report. Instead, each
# shard writes a `--reporter=blob` snapshot (results + coverage) to
# .vitest-reports/, and a final `--merge-reports` pass combines them into one
# real report using the reporters configured in vitest.config.js.
#
# `--reporter=blob` also suppresses per-test pass/fail output during each
# shard, so shards are never allowed to fail fast: every shard runs, then the
# merge step (which produces the actual human-readable report) always runs
# too, and only then does this script exit non-zero if anything failed.
SHARD_COUNT=4
FAILED=0

rm -rf .vitest-reports

for i in $(seq 1 "$SHARD_COUNT"); do
    vitest run --project=storybook --shard="$i/$SHARD_COUNT" --reporter=blob "$@" || FAILED=1
done

vitest run --project=storybook --merge-reports "$@" || FAILED=1

exit $FAILED
