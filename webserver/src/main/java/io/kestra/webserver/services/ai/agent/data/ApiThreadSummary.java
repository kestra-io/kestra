package io.kestra.webserver.services.ai.agent.data;

import java.time.Instant;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentThreadStatus;

import io.micronaut.core.annotation.Nullable;

public record ApiThreadSummary(
    String uid,
    @Nullable String title,
    AgentMode mode,
    AgentThreadStatus status,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant lastTurnAt) {
    public static ApiThreadSummary from(final AgentThread thread) {
        return new ApiThreadSummary(
            thread.uid(), thread.title(), thread.mode(),
            thread.status(), thread.createdAt(), thread.updatedAt(), thread.lastTurnAt()
        );
    }
}
