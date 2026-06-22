ARG BASE_IMAGE="ghcr.io/kestra-io/kestra-base:latest-no-plugins"
FROM ${BASE_IMAGE}

ENV PATH="/app/.venv/bin:$PATH"

COPY --chown=kestra:kestra docker /

RUN --mount=type=bind,target=/mnt/context \
    mkdir -p /app/plugins && \
    { cp -r /mnt/context/plugins/. /app/plugins/ 2>/dev/null || true; } && \
    chown -R kestra:kestra /app

USER kestra

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["--help"]
