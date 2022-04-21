FROM openjdk:11-jre-slim

ARG KESTRA_PLUGINS=""
ARG APT_PACKAGES=""

WORKDIR /app
COPY docker /

RUN if [ -n "${APT_PACKAGES}" ]; then apt-get update -y; apt-get install -y --no-install-recommends ${APT_PACKAGES}; apt-get clean; rm -rf /var/lib/apt/lists/* /var/tmp/* /tmp/*; fi && \
    if [ -n "${KESTRA_PLUGINS}" ]; then /app/kestra plugins install ${KESTRA_PLUGINS}; rm -rf /tmp/*; fi

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["--help"]
