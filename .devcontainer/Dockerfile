FROM ubuntu:24.04

ARG BUILDPLATFORM
ARG DEBIAN_FRONTEND=noninteractive

USER root
WORKDIR /root

RUN apt update && apt install -y \
  apt-transport-https ca-certificates gnupg curl wget git zip unzip less zsh net-tools iputils-ping jq lsof

ENV HOME="/root"

# --------------------------------------
# Git
# --------------------------------------
# Need to add the devcontainer workspace folder as a safe directory to enable git 
# version control system to be enabled in the containers file system.
RUN git config --global --add safe.directory "/workspaces/kestra"
# --------------------------------------

# --------------------------------------
# Oh my zsh
# --------------------------------------
RUN sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" -- \
  -t robbyrussell \
  -p git -p node -p npm

ENV SHELL=/bin/zsh
# --------------------------------------

# --------------------------------------
# Java
# --------------------------------------
ARG OS_ARCHITECTURE

RUN mkdir -p /usr/java
RUN echo "Building on platform: $BUILDPLATFORM"
RUN case "$BUILDPLATFORM" in \
  "linux/amd64") OS_ARCHITECTURE="x64_linux" ;; \
  "linux/arm64") OS_ARCHITECTURE="aarch64_linux" ;; \
  "darwin/amd64") OS_ARCHITECTURE="x64_mac" ;; \
  "darwin/arm64") OS_ARCHITECTURE="aarch64_mac" ;; \
  *) echo "Unsupported BUILDPLATFORM: $BUILDPLATFORM" && exit 1 ;; \
  esac && \
  wget "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_${OS_ARCHITECTURE}_hotspot_21.0.7_6.tar.gz" && \
  mv OpenJDK21U-jdk_${OS_ARCHITECTURE}_hotspot_21.0.7_6.tar.gz openjdk-21.0.7.tar.gz
RUN tar -xzvf openjdk-21.0.7.tar.gz && \
  mv jdk-21.0.7+6 jdk-21 && \
  mv jdk-21 /usr/java/
ENV JAVA_HOME=/usr/java/jdk-21
ENV PATH="$PATH:$JAVA_HOME/bin"
# Will load a custom configuration file for Micronaut
ENV MICRONAUT_ENVIRONMENTS=local,override
# Sets the path where you save plugins as Jar and is loaded during the startup process
ENV KESTRA_PLUGINS_PATH="/workspaces/kestra/local/plugins"
# --------------------------------------

# --------------------------------------
# Node.js
# --------------------------------------
RUN curl -fsSL https://deb.nodesource.com/setup_22.x -o nodesource_setup.sh \
  && bash nodesource_setup.sh && apt install -y nodejs
# Increases JavaScript heap memory to 4GB to prevent heap out of error during startup
ENV NODE_OPTIONS=--max-old-space-size=4096
# --------------------------------------

# --------------------------------------
# Python
# --------------------------------------
RUN apt install -y python3 pip python3-venv
# --------------------------------------

# --------------------------------------
# SSH 
# --------------------------------------
RUN mkdir -p ~/.ssh
RUN touch ~/.ssh/config
RUN echo "Host github.com" >> ~/.ssh/config \
  && echo "    IdentityFile ~/.ssh/id_ed25519" >> ~/.ssh/config
RUN touch ~/.ssh/id_ed25519
# --------------------------------------
