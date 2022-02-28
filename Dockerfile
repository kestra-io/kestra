FROM openjdk:11-jre-slim

ARG KESTRA_PLUGINS=""
ARG APT_PACKAGES=""

WORKDIR /app
COPY docker /

RUN mkdir -p /app/plugins && \
  apt-get update -y && \
  apt-get install -y --no-install-recommends curl wait-for-it ${APT_PACKAGES} && \
  apt-get upgrade -y && \
  apt-get clean && rm -rf /var/lib/apt/lists/* /var/tmp/*

RUN if [ -n "${KESTRA_PLUGINS}" ]; then /app/kestra plugins install ${KESTRA_PLUGINS}; fi

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["--help"]
