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
    Instant createdAt,
    @With Instant updatedAt,
    @With @Nullable Instant lastTurnAt,
    @With boolean deleted
) {
}
