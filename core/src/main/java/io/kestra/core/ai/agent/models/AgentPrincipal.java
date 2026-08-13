package io.kestra.core.ai.agent.models;

import jakarta.annotation.Nullable;

/**
 * Opaque handle to the caller an agent turn runs on behalf of. Captured when the turn is requested and
 * carried through the orchestrator so tools can authorize the caller before acting. Implementations
 * supply the concrete identity; this contract adds almost no behaviour of its own.
 */
public interface AgentPrincipal {
    /**
     * The caller's user id, or {@code null} when the edition has no notion of one.
     *
     * <p>The one detail this contract exposes, because it is needed outside authorization: metering a hosted
     * AI provider per user requires an id, and the alternatives are worse. A request-scoped lookup cannot be
     * used — an agent turn does not necessarily run on the request thread — so the id has to travel with the
     * turn, and the principal is what already does.
     */
    @Nullable
    default String userId() {
        return null;
    }
}
