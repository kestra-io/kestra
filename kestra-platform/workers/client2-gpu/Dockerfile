# =============================================================================
# CLIENT2 GPU WORKER GROUP - DOCKERFILE
# =============================================================================
# GPU-enabled workers for Enterprise Client 2
# Optimized for ML training and inference workloads
# Configuration via environment variables only
# =============================================================================

FROM kestra/kestra:latest

# Install NVIDIA CUDA runtime (for GPU support)
# Note: Requires host with NVIDIA drivers and nvidia-docker2

USER root

# Install CUDA runtime dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        cuda-runtime-12-1 \
        libcudnn8 && \
    rm -rf /var/lib/apt/lists/*

# Switch back to kestra user
USER kestra

# Metadata
LABEL worker-group="client2-gpu"
LABEL description="GPU-enabled workers for Enterprise Client 2"
LABEL client="client2"
LABEL gpu-enabled="true"

# NO hardcoded configuration - all via environment variables at runtime
# Client-specific secrets (Anthropic, Pinecone, etc.) injected at runtime

# Default command: run as worker
CMD ["/app/kestra", "server", "worker"]
