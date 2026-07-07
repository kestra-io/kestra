package io.kestra.webserver.services.ai.agent.data;

import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentScopeBinding;

import io.micronaut.core.annotation.Nullable;

public record ApiCreateThreadRequest(@Nullable AgentMode mode, @Nullable String title, @Nullable AgentScopeBinding scope) {
}
