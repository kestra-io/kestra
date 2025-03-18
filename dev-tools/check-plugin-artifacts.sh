#!/bin/bash 
#===============================================================================
# SCRIPT: check-plugin-artifacts.sh
#
# DESCRIPTION:
#   This script can be used to check if plugins are available on Maven Central or Sonatype
#
# USAGE: ./ check-plugin-artifacts.sh [options]
# OPTIONS:
#   --plugin-file                File containing the plugin list (default: .plugins)
#   --version                    Version for plugins
#   -y, --yes                    Automatically confirm prompts (non-interactive).
#   -h, --help                   Show the help message and exit

# EXAMPLES:
# To clone all plugins:
#   ./check-plugin-artifacts.sh --plugin-file .plugins --version 0.21.0

#===============================================================================

set -e;

###############################################################
# Global vars
###############################################################
BASEDIR=$(dirname "$(readlink -f $0)")
WORKING_DIR=/tmp/kestra-plugins;
PLUGIN_FILE="$BASEDIR/../.plugins"

# Maven Central URL
MAVEN_CENTRAL="https://repo1.maven.org/maven2"
SONATYPE_SNAPSHOT="https://s01.oss.sonatype.org/content/repositories/snapshots"

###############################################################
# Functions
###############################################################

# Function to display the help message
usage() {
    echo "Usage: $0 --version <version> [--plugin-file]"
    echo
    echo "Options:"
    echo "  --plugin-file                File containing the plugin list"
    echo "  --version                    Version for plugins"
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
# Get the options
while [[ "$#" -gt 0 ]]; do
    case "$1" in
        --plugin-file)
            PLUGIN_FILE="$2"
            shift 2
            ;;
        --plugin-file=*)
            PLUGIN_FILE="${1#*=}"
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
  	PLUGINS=$(cat "$PLUGIN_FILE" | grep "io\\.kestra\\." | sed -e '/#/s/^.//' | cut -d':' -f2-3 | uniq | sort);
  	PLUGINS_COUNT=$(echo "$PLUGINS" | wc -l);
  	PLUGINS_ARRAY=$(echo "$PLUGINS" | xargs || echo '');
  	PLUGINS_ARRAY=($PLUGINS_ARRAY);
  fi
else
  PLUGINS_ARRAY=("${PLUGINS_ARGS[@]}")
  PLUGINS_COUNT="${#PLUGINS_ARGS[@]}"
fi

echo "Arguments: "
echo "VERSION=$VERSION"
echo "PLUGIN_FILE=$PLUGIN_FILE"

for PLUGIN in "${PLUGINS_ARRAY[@]}"; do
    echo "$PLUGIN"
done

if [[ "$AUTO_YES" == false ]]; then
  askToContinue
fi

###############################################################
# Main
###############################################################
mkdir -p "$WORKING_DIR"

COUNTER=1;
AVAILABLE=0;
NOT_AVAILABLE=0;
for PLUGIN in "${PLUGINS_ARRAY[@]}"
do
  # Extract groupId and artifactId
  GROUP_ID="${PLUGIN%%:*}"
  ARTIFACT_ID="${PLUGIN##*:}"

  # Convert groupId to Maven repository path
  GROUP_PATH="${GROUP_ID//./\/}"
  ARTIFACT_URL="${GROUP_PATH}/${ARTIFACT_ID}/${VERSION}/${ARTIFACT_ID}-${VERSION}.jar"
  if [[ "$VERSION" == *"-SNAPSHOT" ]]; then
    ARTIFACT_URL="${SONATYPE_SNAPSHOT}/${ARTIFACT_URL}"
  else  
    ARTIFACT_URL="${MAVEN_CENTRAL}/${ARTIFACT_URL}"
  fi

  if curl --silent --fail --head "$ARTIFACT_URL" > /dev/null; then
      printf "✅ [$COUNTER/$PLUGINS_COUNT] %-45s : %s\n" "[$PLUGIN]" "Artifact is available: $ARTIFACT_URL";
      AVAILABLE=$(( AVAILABLE + 1 ))
  else
      printf "❌ [$COUNTER/$PLUGINS_COUNT] %-45s : %s\n" "[$PLUGIN]" "Artifact is NOT available: $ARTIFACT_URL";
      NOT_AVAILABLE=$(( NOT_AVAILABLE + 1 ))
  fi
  COUNTER=$(( COUNTER + 1 ));
done;

echo -e "\n\n✅ Available: $AVAILABLE, ❌  Unavailable: $NOT_AVAILABLE";

exit 0;
