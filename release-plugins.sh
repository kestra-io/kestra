#!/bin/bash 
#===============================================================================
# SCRIPT: release-plugins.sh
#
# DESCRIPTION:
#   This script can be used to run a ./gradlew release command on each kestra plugin repository.
#   By default, if no `GITHUB_PAT` environment variable exist, the script will attempt to clone GitHub repositories using SSH_KEY.
#
# USAGE: ./release-plugins.sh [options]
# OPTIONS:
#   --release-version <version>  Specify the release version (required)
#   --next-version    <version>  Specify the next version (required)
#   --dry-run                    Specify to run in DRY_RUN.
#   -y, --yes                    Automatically confirm prompts (non-interactive).
#   -h, --help                   Show the help message and exit

# EXAMPLES:
# To release all plugins:
#   ./release-plugins.sh --release-version=0.20.0 --next-version=0.21.0-SNAPSHOT
# To release a specific plugin:
#   ./release-plugins.sh --release-version=0.20.0 --next-version=0.21.0-SNAPSHOT plugin-kubernetes
# To release specific plugins from file:
#   ./release-plugins.sh --release-version=0.20.0 --plugin-file .plugins
#===============================================================================

set -e;

###############################################################
# Global vars
###############################################################
BASEDIR=$(dirname "$(readlink -f $0)")
WORKING_DIR=/tmp/kestra-release-plugins-$(date +%s);
PLUGIN_FILE="$BASEDIR/.plugins"
GIT_BRANCH=master

###############################################################
# Functions
###############################################################

# Function to display the help message
usage() {
    echo "Usage: $0 --release-version <version> --next-version [plugin-repositories...]"
    echo
    echo "Options:"
    echo "  --release-version <version>  Specify the release version (required)."
    echo "  --next-version    <version>  Specify the next version (required)."
    echo "  --plugin-file                File containing the plugin list (default: .plugins)"
    echo "  --dry-run                    Specify to run in DRY_RUN."
    echo "  -y, --yes                    Automatically confirm prompts (non-interactive)."
    echo "  -h, --help                   Show this help message and exit."
    exit 1
}

# Function to ask to continue
function askToContinue() {
  read -p "Are you sure you want to continue? [y/N] " confirm
  [[ "$confirm" =~ ^[Yy]$ ]] || { echo "Operation cancelled."; exit 1; }
}

###############################################################
# Options
###############################################################

PLUGINS_ARGS=()
AUTO_YES=false
DRY_RUN=false
# Get the options
while [[ "$#" -gt 0 ]]; do
    case "$1" in
        --release-version)
            RELEASE_VERSION="$2"
            shift 2
            ;;
        --release-version=*)
            RELEASE_VERSION="${1#*=}"
            shift
            ;;
        --next-version)
            NEXT_VERSION="$2"
            shift 2
            ;;
        --next-version=*)
            NEXT_VERSION="${1#*=}"
            shift
            ;;
        --plugin-file)
            PLUGIN_FILE="$2"
            shift 2
            ;;
        --plugin-file=*)
            PLUGIN_FILE="${1#*=}"
            shift
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -y|--yes)
            AUTO_YES=true
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            PLUGINS_ARGS+=("$1")
            shift
            ;;
    esac
done

## Check options
if [[ -z "$RELEASE_VERSION" ]]; then
   echo -e "Missing required argument: --release-version\n";
   usage
fi

if [[ -z "$NEXT_VERSION" ]]; then
    echo -e "Missing required argument: --next-version\n";
    usage
fi

## Get plugin list
if [[ "${#PLUGINS_ARGS[@]}" -eq 0 ]]; then
  if [ -f "$PLUGIN_FILE" ]; then
  	PLUGINS=$(cat "$PLUGIN_FILE" | grep "io\\.kestra\\." | sed -e '/#/s/^.//' | cut -d':' -f1 | uniq | sort);
  	PLUGINS_COUNT=$(echo "$PLUGINS" | wc -l);
  	PLUGINS_ARRAY=$(echo "$PLUGINS" | xargs || echo '');
  	PLUGINS_ARRAY=($PLUGINS_ARRAY);
  fi
else
  PLUGINS_ARRAY=("${PLUGINS_ARGS[@]}")
  PLUGINS_COUNT="${#PLUGINS_ARGS[@]}"
fi

# Extract the major and minor versions
BASE_VERSION=$(echo "$RELEASE_VERSION" | sed -E 's/^([0-9]+\.[0-9]+)\..*/\1/')
PUSH_RELEASE_BRANCH="releases/v${BASE_VERSION}.x"

## Get plugin list
echo "RELEASE_VERSION=$RELEASE_VERSION"
echo "NEXT_VERSION=$NEXT_VERSION"
echo "PUSH_RELEASE_BRANCH=$PUSH_RELEASE_BRANCH"
echo "GIT_BRANCH=$GIT_BRANCH"
echo "DRY_RUN=$DRY_RUN"
echo "Found ($PLUGINS_COUNT) plugin repositories:";

for PLUGIN in "${PLUGINS_ARRAY[@]}"; do
    echo "$PLUGIN"
done

if [[ "$AUTO_YES" == false ]]; then
  askToContinue
fi

###############################################################
# Main
###############################################################
mkdir -p $WORKING_DIR

COUNTER=1;
for PLUGIN in "${PLUGINS_ARRAY[@]}"
do
  cd $WORKING_DIR;

  echo "---------------------------------------------------------------------------------------"
  echo "[$COUNTER/$PLUGINS_COUNT] Release Plugin: $PLUGIN"
  echo "---------------------------------------------------------------------------------------"
  if [[ -z "${GITHUB_PAT}" ]]; then
    git clone git@github.com:kestra-io/$PLUGIN
  else
    echo "Clone git repository using GITHUB PAT"
    git clone https://${GITHUB_PAT}@github.com/kestra-io/$PLUGIN.git
  fi
  cd "$PLUGIN";

  if [[ "$PLUGIN" == "plugin-transform" ]] && [[ "$GIT_BRANCH" == "master" ]]; then # quickfix
    git checkout main;
  else
    git checkout "$GIT_BRANCH";
  fi

  if [[ "$DRY_RUN" == false ]]; then
    CURRENT_BRANCH=$(git branch --show-current);

    echo "Run gradle release for plugin: $PLUGIN";
    echo "Branch: $CURRENT_BRANCH";

    if [[ "$AUTO_YES" == false ]]; then
      askToContinue
    fi

    # Create and push release branch
    git checkout -b "$PUSH_RELEASE_BRANCH";
    git push -u origin "$PUSH_RELEASE_BRANCH";

    # Run gradle release
    git checkout "$CURRENT_BRANCH";

    if [[ "$RELEASE_VERSION" == *"-SNAPSHOT" ]]; then
      # -SNAPSHOT qualifier maybe used to test release-candidates
      ./gradlew release -Prelease.useAutomaticVersion=true \
        -Prelease.releaseVersion="${RELEASE_VERSION}" \
        -Prelease.newVersion="${NEXT_VERSION}" \
        -Prelease.pushReleaseVersionBranch="${PUSH_RELEASE_BRANCH}" \
        -Prelease.failOnSnapshotDependencies=false
    else
      ./gradlew release -Prelease.useAutomaticVersion=true \
        -Prelease.releaseVersion="${RELEASE_VERSION}" \
        -Prelease.newVersion="${NEXT_VERSION}" \
        -Prelease.pushReleaseVersionBranch="${PUSH_RELEASE_BRANCH}"
    fi

    git push;
    sleep 5; # add a short delay to not spam Maven Central
  else
    echo "Skip gradle release [DRY_RUN=true]";
  fi
  COUNTER=$(( COUNTER + 1 ));
done;

exit 0;
