FROM ubuntu:24.04

ARG BUILDPLATFORM
ARG DEBIAN_FRONTEND=noninteractive

USER root
WORKDIR /root

RUN apt update && apt install -y \
  apt-transport-https ca-certificates gnupg \
  curl wget git zip unzip less zsh net-tools iputils-ping jq lsof ripgrep

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
# Docker
# --------------------------------------
# Add Docker's official GPG key:
RUN install -m 0755 -d /etc/apt/keyrings
RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
RUN chmod a+r /etc/apt/keyrings/docker.asc

# Add the repository to Apt sources:
RUN tee /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

# Install the Docker packages.
RUN apt update && apt install -y \ 
  docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
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
  wget "https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.2%2B10/OpenJDK25U-jdk_${OS_ARCHITECTURE}_hotspot_25.0.2_10.tar.gz" && \
  mv OpenJDK25U-jdk_${OS_ARCHITECTURE}_hotspot_25.0.2_10.tar.gz openjdk-25.0.2.tar.gz
RUN tar -xzvf openjdk-25.0.2.tar.gz && \
  mv jdk-25.0.2+10 jdk-25 && \
  mv jdk-25 /usr/java/
ENV JAVA_HOME=/usr/java/jdk-25
ENV PATH="$PATH:$JAVA_HOME/bin"
# Will load a custom configuration file for Micronaut
ENV MICRONAUT_ENVIRONMENTS=local,override
# Sets the path where you save plugins as Jar and is loaded during the startup process
ENV KESTRA_PLUGINS_PATH="/workspaces/kestra/local/plugins"
# --------------------------------------

# --------------------------------------
# Node.js
# --------------------------------------
RUN curl -fsSL https://deb.nodesource.com/setup_24.x -o nodesource_setup.sh \
  && bash nodesource_setup.sh && apt install -y nodejs
  # Increases JavaScript heap memory to 4GB to prevent heap out of error during startup
ENV NODE_OPTIONS="--max-old-space-size=4096"

# Install required dependencies for playwright as e2e tests rely on it.
RUN npx playwright install-deps
# Install browsers for playwright
RUN npx playwright install
# --------------------------------------

# --------------------------------------
# Python
# --------------------------------------
RUN apt install -y python3 pip python3-venv
# Install uv
RUN curl -LsSf https://astral.sh/uv/install.sh | sh
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
