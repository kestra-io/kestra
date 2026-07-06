package io.kestra.webserver.services.ai.agent.domain;

import java.time.Instant;

import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.With;

@Builder(toBuilder = true)
public record Thread(
    String uid,
    String tenant,
    @With @Nullable String title,
    @With Mode mode,
    @Nullable ScopeBinding scope,
    @With @Nullable String ownerNodeId,
    @With ThreadStatus status,
    Instant createdAt,
    @With Instant updatedAt,
    @With @Nullable Instant lastTurnAt,
    @With boolean deleted
) {
}
