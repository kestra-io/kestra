FROM eclipse-temurin:11-jre

ARG KESTRA_PLUGINS=""
ARG APT_PACKAGES=""

WORKDIR /app
COPY docker /

RUN apt-get update -y && \
    apt-get upgrade -y && \
    if [ -n "${APT_PACKAGES}" ]; then apt-get install -y --no-install-recommends ${APT_PACKAGES}; fi && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /var/tmp/* /tmp/* && \
    if [ -n "${KESTRA_PLUGINS}" ]; then /app/kestra plugins install ${KESTRA_PLUGINS} && rm -rf /tmp/*; fi

RUN groupadd kestra && \
  useradd -m -g kestra kestra && \
  chown -R kestra:kestra /app

USER kestra

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["--help"]
