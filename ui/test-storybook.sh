#!/usr/bin/env sh

set -e
set -x

npm run build-storybook --quiet

npx concurrently -k -s first -n "SB,TEST" -c "magenta,blue" \
            "npx http-server storybook-static --port 6006 --silent" \
            "npx wait-on tcp:127.0.0.1:6006 && npx test-storybook"
