#!/usr/bin/env bash
# setup-worktree.sh — Copy gitignored application-*.yml configs into an OSS worktree
#
# Usage:
#   setup-worktree.sh [worktree-path]   # defaults to current directory
#
# Run this once after `git worktree add` to make Kestra bootable in the new worktree.
# The script is idempotent — safe to run multiple times (skips already-present files).

set -euo pipefail

WORKTREE="${1:-$(pwd)}"
WORKTREE=$(cd "$WORKTREE" && pwd)

# Validate it is an OSS Kestra directory
if [[ ! -f "$WORKTREE/settings.gradle" ]]; then
    echo "ERROR: No settings.gradle found in $WORKTREE"
    exit 1
fi

if ! grep -qE "rootProject\.name\s*=\s*[\"']kestra[\"']" "$WORKTREE/settings.gradle"; then
    echo "ERROR: Not a Kestra OSS directory (rootProject.name != 'kestra')"
    exit 1
fi

# Locate the OSS main repo via the git common dir
GIT_COMMON_DIR=$(git -C "$WORKTREE" rev-parse --git-common-dir 2>/dev/null) || {
    echo "ERROR: Not inside a git repository: $WORKTREE"
    exit 1
}

# Resolve to absolute path (common-dir may be relative, e.g. ".git" in the main checkout)
if [[ "$GIT_COMMON_DIR" = /* ]]; then
    OSS_MAIN_REPO=$(dirname "$GIT_COMMON_DIR")
else
    OSS_MAIN_REPO=$(cd "$WORKTREE/$GIT_COMMON_DIR" && cd .. && pwd)
fi

copy_configs() {
    local src_dir="$1" dst_dir="$2"; shift 2
    local excludes=("$@")
    local copied=0
    local find_args=("$src_dir" -maxdepth 1 -name "application*.yml")
    for excl in "${excludes[@]}"; do
        find_args+=(! -name "$excl")
    done
    while IFS= read -r src; do
        local dst="$dst_dir/$(basename "$src")"
        if [[ ! -f "$dst" ]]; then
            cp "$src" "$dst"
            copied=$((copied + 1))
        fi
    done < <(find "${find_args[@]}" 2>/dev/null)
    echo "$copied"
}

OSS_COPIED=$(copy_configs \
    "$OSS_MAIN_REPO/cli/src/main/resources" \
    "$WORKTREE/cli/src/main/resources" \
    "application.yml")
if [[ "$OSS_COPIED" -gt 0 ]]; then
    echo "Copied $OSS_COPIED OSS application config file(s) from main checkout"
fi
