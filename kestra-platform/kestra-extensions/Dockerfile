# =============================================================================
# KESTRA PLATFORM EXTENSIONS - DOCKERFILE
# =============================================================================
# Builds Kestra with custom worker group routing extensions
# NO hardcoded values - all configuration via environment variables
# =============================================================================

# Stage 1: Build the extensions
FROM gradle:8.5-jdk21 AS builder

WORKDIR /app

# Copy gradle files first (for caching)
COPY build.gradle settings.gradle ./

# Copy source code
COPY src ./src

# Build the shadow JAR
RUN gradle shadowJar --no-daemon

# Stage 2: Runtime image based on official Kestra
FROM kestra/kestra:latest

# Copy our custom extensions
COPY --from=builder /app/build/libs/kestra-platform-extensions.jar /app/plugins/

# Metadata
LABEL maintainer="Kestra Platform Team"
LABEL description="Kestra with multi-worker-group routing extensions"
LABEL version="1.0.0"

# Environment variables are injected at runtime via docker-compose
# NO hardcoded values in this Dockerfile

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:${KESTRA_SERVER_PORT:-8080}/health || exit 1

# Default command (can be overridden)
CMD ["/app/kestra", "server", "standalone"]
