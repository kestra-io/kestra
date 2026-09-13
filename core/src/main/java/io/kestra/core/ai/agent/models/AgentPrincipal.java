package io.kestra.core.ai.agent.models;

/**
 * Opaque handle to the caller an agent turn runs on behalf of. Captured when the turn is requested and
 * carried through the orchestrator so tools can authorize the caller before acting. Implementations
 * supply the concrete identity; this contract adds no behaviour of its own.
 */
public interface AgentPrincipal {
}
