package io.kestra.webserver.services.ai.agent.domain;

import java.time.Instant;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.With;

@Builder(toBuilder = true)
public record AgentThread(
    String uid,
    String tenant,
    @With @Nullable String title,
    @With AgentMode mode,
    @Nullable AgentScopeBinding scope,
    @With @Nullable String ownerNodeId,
    @With AgentThreadStatus status,
    @With @Nullable String pendingConfirmationId,
    Instant createdAt,
    @With Instant updatedAt,
    @With @Nullable Instant lastTurnAt,
    @With boolean deleted) {

    /**
     * Returns a copy of this thread reset to the idle state: status set to
     * {@link AgentThreadStatus#IDLE}, the owning node released, any pending confirmation cleared, and
     * the update timestamp refreshed.
     *
     * @return the idle copy of this thread.
     */
    public AgentThread toIdle() {
        return this.withStatus(AgentThreadStatus.IDLE)
            .withOwnerNodeId(null)
            .withPendingConfirmationId(null)
            .withUpdatedAt(Instant.now());
    }
}
