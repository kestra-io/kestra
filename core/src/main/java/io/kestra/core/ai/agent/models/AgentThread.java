package io.kestra.core.ai.agent.models;

import java.time.Instant;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.With;

/**
 * A Copilot conversation thread. Persisted with {@code uid} as its cluster-wide key
 * ({@link HasUID}) and soft-deleted ({@link SoftDeletable}).
 */
@Builder(toBuilder = true)
public record AgentThread(
    String uid,
    String tenant,
    @With @Nullable String title,
    @With AgentMode mode,
    @With @Nullable String ownerNodeId,
    @With AgentThreadStatus status,
    @With @Nullable String pendingConfirmationId,
    Instant createdAt,
    @With Instant updatedAt,
    @With @Nullable Instant lastTurnAt,
    @With boolean deleted) implements HasUID, SoftDeletable<AgentThread> {

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public AgentThread toDeleted() {
        return this.withDeleted(true);
    }

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
