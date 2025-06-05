#!/bin/bash
#===============================================================================
# SCRIPT: update-plugin-kestra-version.sh
#
# DESCRIPTION:
#   This script can be used to update the gradle 'kestraVersion' property on each kestra plugin repository.
#   By default, if no `GITHUB_PAT` environment variable exist, the script will attempt to clone GitHub repositories using SSH_KEY.
#
#USAGE:
#   ./dev-tools/update-plugin-kestra-version.sh --branch <branch> --version <version> [plugin-repositories...]
#
#OPTIONS:
#  --branch <branch>           Specify the branch on which to update the kestraCoreVersion (default: master).
#  --version    <version>      Specify the Kestra core version (required).
#  --plugin-file               File containing the plugin list (default: .plugins)
#  --dry-run                   Specify to run in DRY_RUN.
#  -y, --yes                   Automatically confirm prompts (non-interactive).
#  -h, --help                  Show this help message and exit.


# EXAMPLES:
# To release all plugins:
#   ./update-plugin-kestra-version.sh --branch=releases/v0.23.x --version="[0.23,0.24)"
# To release a specific plugin:
#   ./update-plugin-kestra-version.sh --branch=releases/v0.23.x --version="[0.23,0.24)" plugin-kubernetes
# To release specific plugins from file:
#   ./update-plugin-kestra-version.sh --branch=releases/v0.23.x --version="[0.23,0.24)" --plugin-file .plugins
#===============================================================================

set -e;

###############################################################
# Global vars
###############################################################
BASEDIR=$(dirname "$(readlink -f $0)")
SCRIPT_NAME=$(basename "$0")
SCRIPT_NAME="${SCRIPT_NAME%.*}"
WORKING_DIR="/tmp/kestra-$SCRIPT_NAME-$(date +%s)"
PLUGIN_FILE="$BASEDIR/../.plugins"
GIT_BRANCH=master

###############################################################
# Functions
###############################################################

# Function to display the help message
usage() {
    echo "Usage: $0 --branch <branch> --version <version> [plugin-repositories...]"
    echo
    echo "Options:"
    echo "  --branch <branch>           Specify the branch on which to update the kestraCoreVersion (default: master)."
    echo "  --version    <version>      Specify the Kestra core version (required)."
    echo "  --plugin-file               File containing the plugin list (default: .plugins)"
    echo "  --dry-run                   Specify to run in DRY_RUN."
    echo "  -y, --yes                   Automatically confirm prompts (non-interactive)."
    echo "  -h, --help                  Show this help message and exit."
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
        --branch)
            GIT_BRANCH="$2"
            shift 2
            ;;
        --branch=*)
            GIT_BRANCH="${1#*=}"
            shift
            ;;
        --version)
            VERSION="$2"
            shift 2
            ;;
        --version=*)
            VERSION="${1#*=}"
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
if [[ -z "$VERSION" ]]; then
   echo -e "Missing required argument: --version\n";
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


## Get plugin list
echo "VERSION=$RELEASE_VERSION"
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
  echo "[$COUNTER/$PLUGINS_COUNT] Update Plugin: $PLUGIN"
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

  CURRENT_BRANCH=$(git branch --show-current);

  echo "Update kestraVersion for plugin: $PLUGIN on branch $CURRENT_BRANCH:";
  # Update the kestraVersion property
  sed -i "s/^kestraVersion=.*/kestraVersion=${VERSION}/" ./gradle.properties
  # Display diff
  git diff --exit-code --unified=0 ./gradle.properties | grep -E '^\+|^-' | grep -v -E '^\+\+\+|^---'

  if [[ "$DRY_RUN" == false ]]; then
    if [[ "$AUTO_YES" == false ]]; then
      askToContinue
    fi
    git add ./gradle.properties
    git commit -m"chore(deps): update kestraVersion to ${VERSION}."
    git push
  else
    echo "Skip git commit/push [DRY_RUN=true]";
  fi
  COUNTER=$(( COUNTER + 1 ));
done;

exit 0;
