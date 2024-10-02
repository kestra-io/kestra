FROM eclipse-temurin:21-jre-jammy

ARG KESTRA_PLUGINS=""
ARG APT_PACKAGES=""
ARG PYTHON_LIBRARIES=""

WORKDIR /app

RUN groupadd kestra && \
    useradd -m -g kestra kestra

COPY --chown=kestra:kestra docker /

# Download the correct Kestra JAR file
RUN wget -O /app/kestra https://github.com/kestra-io/kestra/releases/download/v0.19.1/kestra-0.19.1-all.jar

# Ensure it's executable
RUN chmod +x /app/kestra

# Install additional packages, if any
RUN apt-get update -y && \
    apt-get upgrade -y && \
    if [ -n "${APT_PACKAGES}" ]; then apt-get install -y --no-install-recommends ${APT_PACKAGES}; fi && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/* /var/tmp/* /tmp/*

# Install Kestra plugins, if any
RUN if [ -n "${KESTRA_PLUGINS}" ]; then /app/kestra plugins install ${KESTRA_PLUGINS} && rm -rf /tmp/*; fi

# Install Python libraries, if any
RUN if [ -n "${PYTHON_LIBRARIES}" ]; then pip install ${PYTHON_LIBRARIES}; fi

# Adjust ownership
RUN chown -R kestra:kestra /app

USER kestra

# Ensure entrypoint script is available
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

ENTRYPOINT ["docker-entrypoint.sh"]

CMD ["--help"]
