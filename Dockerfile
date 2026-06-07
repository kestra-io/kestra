ARG BASE_IMAGE="ghcr.io/kestra-io/kestra-base:latest-no-plugins"
FROM ${BASE_IMAGE}

ARG APT_PACKAGES=""
ARG PYTHON_LIBRARIES=""

ENV PATH="/app/.venv/bin:$PATH"

COPY --chown=kestra:kestra docker /

RUN --mount=type=bind,target=/mnt/context \
    mkdir -p /app/plugins && \
    { cp -r /mnt/context/plugins/. /app/plugins/ 2>/dev/null || true; }; \
    if [ -n "${APT_PACKAGES}" ]; then \
        apt-get update -y && \
        apt-get install -y --no-install-recommends ${APT_PACKAGES} && \
        apt-get clean && \
        rm -rf /var/lib/apt/lists/*; \
    fi && \
    if [ -n "${PYTHON_LIBRARIES}" ]; then uv venv /app/.venv && PURE_PYTHON=1 uv pip install --python /app/.venv/bin/python ${PYTHON_LIBRARIES}; fi && \
    chown -R kestra:kestra /app

USER kestra

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["--help"]
