#!/bin/sh
# Fix Docker socket permissions if mounted, then start the agent.
if [ -S /var/run/docker.sock ]; then
    chmod 666 /var/run/docker.sock
fi

exec java \
    -Xms64m -Xmx64m -XX:MaxMetaspaceSize=64m \
    -jar /root/develocity-test-distribution-agent.jar \
    "$@"
