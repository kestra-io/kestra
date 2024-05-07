#
# Makefile used to build and deploy Kestra locally.
# By default Kestra will be installed under: $HOME/.kestra/current. Set $KESTRA_HOME to override default.
#
# Usage:
# make install
# make install-plugins
# make start-standalone-postgres
#
# NOTE: This file is intended for development purposes only.

SHELL := /bin/bash

KESTRA_BASEDIR := $(shell echo $${KESTRA_HOME:-$$HOME/.kestra/current})
KESTRA_WORKER_THREAD := $(shell echo $${KESTRA_WORKER_THREAD:-4})
VERSION := $(shell ./gradlew properties -q | awk '/^version:/ {print $$2}')
GIT_COMMIT := $(shell git rev-parse --short HEAD)
GIT_BRANCH := $(shell git rev-parse --abbrev-ref HEAD)
DATE := $(shell date --rfc-3339=seconds)

DOCKER_IMAGE = kestra/kestra
DOCKER_PATH = ./

.SILENT:

.PHONY: clean build build-exec test install

all: clean build-exec install

version:
	echo "${VERSION}"

clean:
	./gradlew clean

build: clean
	./gradlew build

buildSkipTests: clean
	./gradlew build -x test -x integrationTest -x testCodeCoverageReport --refresh-dependencies

test: clean
	./gradlew test

build-exec:
	./gradlew -q executableJar --no-daemon --priority=normal

install: build-exec
	echo "Installing Kestra: ${KESTRA_BASEDIR}"
	mkdir -p ${KESTRA_BASEDIR}/bin ${KESTRA_BASEDIR}/plugins ${KESTRA_BASEDIR}/flows ${KESTRA_BASEDIR}/logs
	cp build/executable/* ${KESTRA_BASEDIR}/bin/kestra && chmod +x ${KESTRA_BASEDIR}/bin
	VERSION_INSTALLED=$$(${KESTRA_BASEDIR}/bin/kestra --version); \
	echo "Kestra installed successfully (version=$$VERSION_INSTALLED) 🚀"

# Install plugins for Kestra from (.plugins file).
install-plugins:
	if [[ ! -f ".plugins" && ! -f ".plugins.override" ]]; then \
		echo "[ERROR] file '$$(pwd)/.plugins' and '$$(pwd)/.plugins.override' not found."; \
		exit 1; \
	fi; \

	PLUGIN_LIST="./.plugins"; \
	if [[ -f ".plugins.override" ]]; then \
		PLUGIN_LIST="./.plugins.override"; \
	fi; \
	while IFS= read -r plugin; do \
		[[ $$plugin =~ ^#.* ]] && continue; \
		PLUGINS_PATH="${KESTRA_INSTALL_DIR}/plugins"; \
		CURRENT_PLUGIN=$${plugin/LATEST/"${VERSION}"}; \
		PLUGIN_FILE="$$PLUGINS_PATH/$$(echo $$CURRENT_PLUGIN | awk -F':' '{print $$2"-"$$3}').jar"; \
		echo "Installing Kestra plugin $$CURRENT_PLUGIN > ${KESTRA_INSTALL_DIR}/plugins"; \
		if [ -f "$$PLUGIN_FILE" ]; then \
		    echo "Plugin already installed in > $$PLUGIN_FILE"; \
        else \
		${KESTRA_BASEDIR}/bin/kestra plugins install $$CURRENT_PLUGIN \
		--plugins ${KESTRA_BASEDIR}/plugins \
		--repositories=https://s01.oss.sonatype.org/content/repositories/snapshots || exit 1; \
		fi \
    done < $$PLUGIN_LIST

# Build docker image from Kestra source.
build-docker: build-exec
	cp build/executable/* docker/app/kestra && chmod +x docker/app/kestra
	echo "${DOCKER_IMAGE}:${VERSION}"
	docker build \
		--compress \
		--rm \
		-f ./Dockerfile \
		-t ${DOCKER_IMAGE}:${VERSION} ${DOCKER_PATH} || exit 1 ;

# Verify whether Kestra is running
health:
	PID=$$(ps aux | grep java | grep 'kestra' | grep -v 'grep' | awk '{print $$2}'); \
	if [ ! -z "$$PID" ]; then \
	    echo -e "\n⏳ Waiting for Kestra server..."; \
        KESTRA_URL=http://localhost:8080; \
        while [ $$(curl -s -L -o /dev/null -w %{http_code} $$KESTRA_URL) != 200 ]; do \
          echo -e $$(date) "\tKestra server HTTP state: " $$(curl -k -L -s -o /dev/null -w %{http_code} $$KESTRA_URL) " (waiting for 200)"; \
          sleep 2; \
        done; \
		echo "Kestra is running (pid=$$PID): $$KESTRA_URL 🚀"; \
	fi


# Kill Kestra running process
kill:
	PID=$$(ps aux | grep java | grep 'kestra' | grep -v 'grep' | awk '{print $$2}'); \
	if [ ! -z "$$PID" ]; then \
		echo "Killing Kestra process (pid=$$PID)."; \
		kill $$PID; \
	else \
		echo "No Kestra process to kill."; \
	fi
	docker compose -f ./docker-compose-ci.yml down;

# Default configuration for using Kestra with Postgres as backend.
define KESTRA_POSTGRES_CONFIGURATION =
micronaut:
  server:
    port: 8080
datasources:
  postgres:
    url: jdbc:postgresql://localhost:5432/kestra_unit
    driverClassName: org.postgresql.Driver
    username: kestra
    password: k3str4
kestra:
  server:
    basic-auth:
    enabled: false
  encryption:
    secret-key: 3ywuDa/Ec61VHkOX3RlI9gYq7CaD0mv0Pf3DHtAXA6U=
  repository:
    type: postgres
  storage:
    type: local
    local:
      base-path: "/tmp/kestra/storage"
  queue:
    type: postgres
endef
export KESTRA_POSTGRES_CONFIGURATION

# Build and deploy Kestra in standalone mode (using Postgres backend)
--private-start-standalone-postgres:
	docker compose -f ./docker-compose-ci.yml up postgres -d;
	echo "Waiting for postgres to be running"
	until [ "`docker inspect -f {{.State.Running}} kestra-postgres-1`"=="true" ]; do \
		sleep 1; \
	done; \
	rm -rf ${KESTRA_BASEDIR}/bin/confs/ && \
	mkdir -p ${KESTRA_BASEDIR}/bin/confs/ ${KESTRA_BASEDIR}/logs/ && \
	touch ${KESTRA_BASEDIR}/bin/confs/application.yml
	echo "Starting Kestra Standalone server"
	KESTRA_CONFIGURATION=$$KESTRA_POSTGRES_CONFIGURATION ${KESTRA_BASEDIR}/bin/kestra \
	server standalone \
	--worker-thread ${KESTRA_WORKER_THREAD} \
	--plugins "${KESTRA_BASEDIR}/plugins" \
	--flow-path "${KESTRA_BASEDIR}/flows" 2>${KESTRA_BASEDIR}/logs/err.log 1>${KESTRA_BASEDIR}/logs/out.log &

start-standalone-postgres: kill --private-start-standalone-postgres health

# Build and deploy Kestra in standalone mode (using In-Memory backend)
--private-start-standalone-local:
	rm -f "${KESTRA_BASEDIR}/logs/*.log"; \
	${KESTRA_BASEDIR}/bin/kestra \
	server local \
	--worker-thread ${KESTRA_WORKER_THREAD} \
	--plugins "${KESTRA_BASEDIR}/plugins" \
	--flow-path "${KESTRA_BASEDIR}/flows" 2>${KESTRA_BASEDIR}/logs/err.log 1>${KESTRA_BASEDIR}/logs/out.log &

start-standalone-local: kill --private-start-standalone-local health

