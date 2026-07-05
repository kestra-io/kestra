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
# shard also writes a `--reporter=blob` snapshot (results + coverage) to
# .vitest-reports/, and a final `--merge-reports` pass combines them into one
# real report using the reporters configured in vitest.config.js.
# `--reporter=default` runs alongside blob so each shard still prints its own
# live pass/fail output — reporters are additive, not exclusive.
#
# Every shard always runs (none of them fail fast) so the merge step, and
# therefore the final combined coverage report, always reflects the whole
# suite even if one shard has a failing test; this script's own exit code
# reflects whether anything failed along the way.
#
# Each shard only sees a slice of the story files, so its own coverage
# numbers are always incomplete and misleading on their own. Coverage data is
# still collected per shard (needed for the merge below) but its report is
# suppressed with --coverage.reporter none; the merge step reports the real,
# whole-suite numbers using the reporters from vitest.config.js.
# Note: vitest's CLI does not accept the `--coverage.reporter=none` form for
# this nested option — it silently keeps the config default. It must be
# passed as two separate arguments.
SHARD_COUNT=4
FAILED=0

rm -rf .vitest-reports

for i in $(seq 1 "$SHARD_COUNT"); do
    vitest run --project=storybook --shard="$i/$SHARD_COUNT" --reporter=blob --reporter=default "$@" --coverage.reporter none || FAILED=1
done

# Deliberately no --project=storybook filter here (unlike the shard runs
# above) — filtering the merge-only run down to a single project is the
# working theory for why it kept reporting a false "No test files found"
# despite every individual shard's results replaying successfully. Only
# storybook blobs exist in .vitest-reports/ (the "unit" project's tests never
# write there), so nothing else can get merged in by leaving this off.
vitest run --merge-reports "$@" || FAILED=1

exit $FAILED
