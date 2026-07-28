package io.kestra.webserver.services.ai.agent;

import io.kestra.core.ai.agent.models.AgentPrincipal;

import io.micronaut.core.annotation.Nullable;

public interface AgentPrincipalResolver {
    @Nullable
    AgentPrincipal resolve();
}
