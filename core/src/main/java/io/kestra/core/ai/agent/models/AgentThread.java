package io.kestra.core.ai.agent.models;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.With;

/**
 * The durable unit of Copilot conversation memory: one row in the {@code ai_agent_thread} table. Holds the
 * metadata/governance record for a conversation — never the messages themselves (those are appended to
 * {@code ai_agent_message}). Keyed by its globally-unique {@link #uid()} and scoped by {@link #tenant()};
 * {@link #userId()} is the owning principal. Soft-deleted rather than hard-deleted so history is
 * retained until purge.
 */
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentThread(
    String uid,
    String tenant,
    @With @Nullable String userId,
    @With @Nullable String title,
    @With AgentMode mode,
    @With @Nullable String ownerNodeId,
    @With AgentThreadStatus status,
    @With @Nullable String pendingConfirmationId,
    Instant createdAt,
    @With Instant updatedAt,
    @With @Nullable Instant lastTurnAt,
    @With boolean deleted) implements HasUID, SoftDeletable<AgentThread> {

    /**
     * {@inheritDoc}
     * <p>
     * The server-minted {@code uid} is globally unique, so it doubles as the durable primary key.
     */
    @Override
    public String uid() {
        return uid;
    }

    /** {@inheritDoc} */
    @Override
    @JsonIgnore
    public boolean isDeleted() {
        return deleted;
    }

    /** {@inheritDoc} */
    @Override
    public AgentThread toDeleted() {
        return this.withDeleted(true).withUpdatedAt(Instant.now());
    }

    /**
     * Returns a copy of this thread reset to the idle state: status set to
     * {@link AgentThreadStatus#IDLE}, the owning node released, and the update timestamp refreshed.
     *
     * @return the idle copy of this thread.
     */
    public AgentThread toIdle() {
        return this.withStatus(AgentThreadStatus.IDLE)
            .withOwnerNodeId(null)
            .withUpdatedAt(Instant.now());
    }
}
