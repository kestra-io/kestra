package io.kestra.webserver.services.ai.agent;

import io.kestra.webserver.services.ai.agent.domain.AgentPrincipal;

import io.micronaut.core.annotation.Nullable;

public interface AgentPrincipalResolver {
    @Nullable
    AgentPrincipal resolve();
}
