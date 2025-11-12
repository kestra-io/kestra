# =============================================================================
# SHARED WORKER GROUP - DOCKERFILE
# =============================================================================
# Workers for general/shared namespaces
# Configuration via environment variables only
# =============================================================================

FROM kestra/kestra:latest

# Metadata
LABEL worker-group="shared"
LABEL description="Shared worker pool for general workloads"

# NO hardcoded configuration - all via environment variables at runtime

# Default command: run as worker
CMD ["/app/kestra", "server", "worker"]
