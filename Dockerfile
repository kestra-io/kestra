FROM eclipse-temurin:21-jre-jammy

ARG KESTRA_PLUGINS=""
ARG APT_PACKAGES=""
ARG PYTHON_LIBRARIES=""

WORKDIR /app

RUN groupadd kestra && \
    useradd -m -g kestra kestra

COPY --chown=kestra:kestra docker /

RUN apt-get update -y && \
    apt-get upgrade -y && \
    if [ -n "${APT_PACKAGES}" ]; then apt-get install -y --no-install-recommends ${APT_PACKAGES}; fi && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /var/tmp/* /tmp/* && \
    if [ -n "${KESTRA_PLUGINS}" ]; then /app/kestra plugins install ${KESTRA_PLUGINS} && rm -rf /tmp/*; fi && \
    if [ -n "${PYTHON_LIBRARIES}" ]; then pip install ${PYTHON_LIBRARIES}; fi && \
    chown -R kestra:kestra /app

USER kestra

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["--help"]