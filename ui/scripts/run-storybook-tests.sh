#!/bin/bash
set -e

# Browser sessions accumulate memory across story files (Monaco workers are
# created per-model and never terminated) until the tab dies mid-run with
# "[birpc] rpc is closed" — so the suite always runs as bounded shards, each
# a fresh process with a fresh browser session.
#
# Reporters are passed on the CLI everywhere: Vitest only honors root-level
# `reporters`, so the per-project junit config silently produced no file.

# CI matrix mode: SHARD="i/n" runs one slice and writes a shard-suffixed JUnit
# report; the report job globs all of them, so no merge step is needed.
if [ -n "$SHARD" ]; then
    exec vitest run --project=storybook --shard="$SHARD" \
        --reporter=default --reporter=junit \
        --outputFile="test-report.storybook-${SHARD%%/*}.junit.xml" "$@"
fi

# Local mode: same shards, sequentially, then one merged report.
SHARD_COUNT="${SHARD_COUNT:-4}"
FAILED=0

rm -rf .vitest-reports

# Coverage doesn't merge across separate `vitest run` processes on its own:
# each shard writes a --reporter=blob snapshot, merged into one report below.
# Per-shard coverage output is suppressed (only whole-suite numbers are real);
# vitest's CLI needs the nested option as two args, not --coverage.reporter=none.
for i in $(seq 1 "$SHARD_COUNT"); do
    vitest run --project=storybook --shard="$i/$SHARD_COUNT" --reporter=blob --reporter=default "$@" --coverage.reporter none || FAILED=1
done

# No --project filter here: filtering the merge-only run reported a false
# "No test files found"; only storybook blobs exist in .vitest-reports anyway.
vitest run --merge-reports --reporter=default --reporter=junit --outputFile=test-report.storybook.junit.xml "$@" || FAILED=1

exit $FAILED
