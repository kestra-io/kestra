set -e

OWNER='kestra-io'
REPO=$1

if [[ -z "$REPO" ]]; then
  echo -e "Missing required argument repo\n";
fi

echo "get caches for /repo/$OWNER/$REPO/action/caches"

gh api \
  -H "Accept: application/vnd.github+json" \
  -H "X-GitHub-Api-Version: 2022-11-28" \
  /repos/$OWNER/$REPO/actions/caches > caches.json

for id in $(jq -r '.actions_caches[].id' caches.json); do
  echo "delete cache with ID: $id"
  gh api --method DELETE "/repos/$OWNER/$REPO/actions/caches/$id"
done

