#!/usr/bin/env bash
# Cold-run benchmark: hyperfine for wall time, GNU time for the largest process, measure.mjs for the tree.
# Linux only (GNU time -v, ps); on macOS arm64 swap `/usr/bin/time -v` for `/usr/bin/time -l`.
set -euo pipefail
cd "$(dirname "$0")/.."
RUNS="${RUNS:-5}"
OUT=results/raw
mkdir -p "$OUT"
# ONLY="a b" reruns those configs and keeps the other results.
[ -z "${ONLY:-}" ] && rm -f "$OUT"/mem.jsonl "$OUT"/*.time

TSC="node node_modules/typescript7/bin/tsc"
TSRS="node_modules/.bin/tsrs"
declare -A CMDS=(
    [vue-tsc]="cd .. && node_modules/.bin/vue-tsc --build packages/design-system/tsconfig.app.json --pretty false && node_modules/.bin/vue-tsc --build tsconfig.app.json --pretty false && node_modules/.bin/vue-tsc --project tsconfig.test.json"
    [tsgo]="$TSC --build tsconfig.test.json --runExternalCode --pretty false"
    [tsrs]="$TSRS --build tsconfig.test.json --runExternalCode --pretty false"
    [tsrs-fast]="$TSRS --build tsconfig.test.json --runExternalCode --fast --pretty false"
    [tsgo-flat]="$TSC -p tsconfig.flat.json --runExternalCode --pretty false"
    [tsrs-flat-4g]="$TSRS -p tsconfig.flat.json --runExternalCode --fast --noEmit --maxMemory 4g --pretty false"
    [tsrs-flat-384m]="$TSRS -p tsconfig.flat.json --runExternalCode --fast --noEmit --maxMemory 384m --pretty false"
    [tsrs-flat-go]="$TSRS -p tsconfig.flat.json --runExternalCode --checkerAssignment go --noLazyMembers --pretty false"
)
ORDER=(vue-tsc tsgo tsrs tsrs-fast tsgo-flat tsrs-flat-4g tsrs-flat-384m tsrs-flat-go)
PREPARE="rm -rf .tmp ../node_modules/.tmp ../packages/design-system/node_modules/.tmp"

for name in "${ORDER[@]}"; do printf '%s\t%s\n' "$name" "$([ "$name" = vue-tsc ] && echo .. || echo .)"; done > "$OUT/runs.tsv"
for name in ${ONLY:-${ORDER[@]}}; do
    cmd="${CMDS[$name]}"
    hyperfine --runs "$RUNS" --ignore-failure --prepare "$PREPARE" \
        --export-json "$OUT/$name.hyperfine.json" \
        "/usr/bin/time -v -a -o $OUT/$name.time node scripts/measure.mjs $OUT/mem.jsonl $name '$cmd' > $OUT/$name.txt 2>&1"
done
node scripts/report.mjs
