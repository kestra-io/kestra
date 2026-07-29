package io.kestra.webserver.services.ai.agent;

import java.util.Map;

import io.kestra.core.ai.agent.models.AgentThread;
import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentPrincipal;

import io.micronaut.core.annotation.Nullable;

public record AgentTurnContext(
    AgentThread thread,
    String prompt,
    AgentMode mode,
    String tenant,
    @Nullable String providerId,
    @Nullable AgentPrincipal principal,
    @Nullable Map<String, Object> additionalContext) {
}
