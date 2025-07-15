set -e

OWNER='kestra-io'

gh repo list "$OWNER" --limit 1000 --json name -q '.[] | select(.name | startswith("plugin-")) | .name' |
while read -r repo; do
  echo "Calling script for $repo"
  sh launch-release-workflow.sh $repo master &
done

