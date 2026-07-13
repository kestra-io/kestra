package io.kestra.webserver.services.ai.agent.data;

import java.time.Instant;

import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentScopeBinding;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;
import io.kestra.webserver.services.ai.agent.domain.AgentThreadStatus;

import io.micronaut.core.annotation.Nullable;

public record ApiThreadSummary(
    String uid,
    @Nullable String title,
    AgentMode mode,
    @Nullable AgentScopeBinding scope,
    AgentThreadStatus status,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant lastTurnAt
) {
    public static ApiThreadSummary from(final AgentThread thread) {
        return new ApiThreadSummary(
            thread.uid(), thread.title(), thread.mode(), thread.scope(),
            thread.status(), thread.createdAt(), thread.updatedAt(), thread.lastTurnAt()
        );
    }
}
