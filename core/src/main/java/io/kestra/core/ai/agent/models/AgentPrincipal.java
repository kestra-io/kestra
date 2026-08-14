package io.kestra.core.ai.agent.models;

import jakarta.annotation.Nullable;

/**
 * Opaque handle to the caller an agent turn runs on behalf of. Captured when the turn is requested and
 * carried through the orchestrator so tools can authorize the caller before acting. Implementations
 * supply the concrete identity; this contract adds almost no behaviour of its own.
 */
public interface AgentPrincipal {
    /**
     * The caller's user id, or {@code null} where there is no notion of one.
     *
     * <p>Exposed here because per-user metering needs it outside authorization, and it has to travel with the
     * turn: a turn does not necessarily run on the request thread, so a request-scoped lookup would come back
     * empty.
     */
    @Nullable
    default String userId() {
        return null;
    }
}
