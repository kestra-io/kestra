package io.kestra.webserver.services.ai.agent;

import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentThread;

import io.micronaut.core.annotation.Nullable;

public record AgentTurnContext(
    AgentThread thread,
    String prompt,
    AgentMode mode,
    String tenant,
    @Nullable String providerId
) {
}
