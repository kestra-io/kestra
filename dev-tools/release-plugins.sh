#!/bin/bash
#===============================================================================
# SCRIPT: release-plugins.sh
#
# DESCRIPTION:
#   This script releases modified Kestra plugins using the Gradle release plugin.
#   It supports version bumping, tag creation, and release branch management.
#   Supports auto-detecting bump type (major, minor, patch) per plugin.
#===============================================================================

set -e

BASEDIR=$(dirname "$(readlink -f $0)")
WORKING_DIR=/tmp/kestra-release-plugins-$(date +%s)
GIT_BRANCH=main

# Bump Detection Function
detect_bump_type() {
  COMMITS=$(git log -10 --pretty=%B)
  if echo "$COMMITS" | grep -Eq 'BREAKING CHANGE|^feat!|feat\([^)]+\)!'; then
    echo "major"
  elif echo "$COMMITS" | grep -Eq '^feat'; then
    echo "minor"
  else
    echo "invalid"
  fi
}

# Parse Arguments
PLUGINS_ARGS=()
DRY_RUN=false
KESTRA_VERSION=""

while [[ "$#" -gt 0 ]]; do
    case "$1" in
        --kestra-version)
            KESTRA_VERSION="$2"
            shift 2
            ;;
        --kestra-version=*)
            KESTRA_VERSION="${1#*=}"
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        *)
            PLUGINS_ARGS+=("$1")
            shift
            ;;
    esac
done

# Load Plugins List
PLUGINS_ARRAY=("${PLUGINS_ARGS[@]}")
PLUGINS_COUNT="${#PLUGINS_ARRAY[@]}"

echo "Found ($PLUGINS_COUNT) plugin repositories:"
for PLUGIN in "${PLUGINS_ARRAY[@]}"; do echo "$PLUGIN"; done

mkdir -p "$WORKING_DIR"
COUNTER=1

for PLUGIN in "${PLUGINS_ARRAY[@]}"; do
  cd "$WORKING_DIR"

  echo "---------------------------------------------------------------------------------------"
  echo "[$COUNTER/$PLUGINS_COUNT] Releasing Plugin: $PLUGIN"
  echo "---------------------------------------------------------------------------------------"

  if [[ -z "${GITHUB_PAT}" ]]; then
    git clone git@github.com:kestra-io/$PLUGIN
  else
    echo "Clone git repository using GITHUB PAT"
    git clone https://${GITHUB_PAT}@github.com/kestra-io/$PLUGIN.git
  fi

  cd $PLUGIN

  # Per Plugin Version Calculation
  PLUGIN_LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
  BUMP_TYPE=$(detect_bump_type)
  if [[ "$BUMP_TYPE" == "invalid" ]]; then
    echo "No valid bump type (major/minor) found for $PLUGIN. Skipping..."
    continue
  fi
  IFS='.' read -r MAJOR MINOR PATCH <<< "${PLUGIN_LAST_TAG#v}"
  case "$BUMP_TYPE" in
    major) ((MAJOR++)); MINOR=0; PATCH=0 ;;
    minor) ((MINOR++)); PATCH=0 ;;
  esac
  PLUGIN_RELEASE_VERSION="$MAJOR.$MINOR.$PATCH"
  PLUGIN_NEXT_VERSION="$MAJOR.$((MINOR + 1)).$PATCH-SNAPSHOT"

  BASE_VERSION=$(echo "$PLUGIN_RELEASE_VERSION" | sed -E 's/^([0-9]+\\.[0-9]+)\\..*/\\1/')
  PUSH_RELEASE_BRANCH="releases/v${BASE_VERSION}.x"

  echo "Plugin $PLUGIN → RELEASE_VERSION=$PLUGIN_RELEASE_VERSION, NEXT_VERSION=$PLUGIN_NEXT_VERSION"

  TAG_EXISTS=$(git ls-remote --tags origin "refs/tags/v${PLUGIN_RELEASE_VERSION}" | wc -l)
  if [[ "$TAG_EXISTS" -ne 0 ]]; then
    echo "Tag v${PLUGIN_RELEASE_VERSION} already exists for $PLUGIN. Skipping..."
    continue
  fi

  if [[ "$DRY_RUN" == false ]]; then
    git checkout -b "$PUSH_RELEASE_BRANCH"
    git push -u origin "$PUSH_RELEASE_BRANCH"
    git checkout "$GIT_BRANCH"

    if [[ "$PLUGIN_RELEASE_VERSION" == *"-SNAPSHOT" ]]; then
      ./gradlew release -Prelease.useAutomaticVersion=true \
        -Prelease.releaseVersion="$PLUGIN_RELEASE_VERSION" \
        -Prelease.newVersion="$PLUGIN_NEXT_VERSION" \
        -Prelease.pushReleaseVersionBranch="$PUSH_RELEASE_BRANCH" \
        -Prelease.failOnSnapshotDependencies=false
    else
      ./gradlew release -Prelease.useAutomaticVersion=true \
        -Prelease.releaseVersion="$PLUGIN_RELEASE_VERSION" \
        -Prelease.newVersion="$PLUGIN_NEXT_VERSION" \
        -Prelease.pushReleaseVersionBranch="$PUSH_RELEASE_BRANCH"
    fi

    git push

    if [[ -n "$KESTRA_VERSION" && "$BUMP_TYPE" == "major" ]]; then
      PLUGIN_KESTRA_VERSION="$KESTRA_VERSION"
      git checkout "$PUSH_RELEASE_BRANCH" && git pull
      sed -i "s/^kestraVersion=.*/kestraVersion=${PLUGIN_KESTRA_VERSION}/" ./gradle.properties
      git add ./gradle.properties
      if ! git diff --cached --quiet; then
        git commit -m "chore(deps): update kestraVersion to ${PLUGIN_KESTRA_VERSION}."
        git push
      fi
    fi
    sleep 5
  else
    echo "Dry run: skipping release for $PLUGIN"
  fi
  COUNTER=$(( COUNTER + 1 ))
done

exit 0
