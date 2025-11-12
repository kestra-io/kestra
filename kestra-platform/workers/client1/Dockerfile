# =============================================================================
# CLIENT1 WORKER GROUP - DOCKERFILE
# =============================================================================
# Dedicated CPU workers for Enterprise Client 1
# Isolated execution environment with client-specific secrets
# Configuration via environment variables only
# =============================================================================

FROM kestra/kestra:latest

# Metadata
LABEL worker-group="client1-cpu"
LABEL description="Dedicated CPU workers for Enterprise Client 1"
LABEL client="client1"

# NO hardcoded configuration - all via environment variables at runtime
# Client-specific secrets (AWS, OpenAI, etc.) injected at runtime

# Default command: run as worker
CMD ["/app/kestra", "server", "worker"]
