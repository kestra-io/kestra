FROM openjdk:11-slim

WORKDIR /app
COPY docker /
ENV MICRONAUT_CONFIG_FILES=/app/confs/application.yml
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["--help"]