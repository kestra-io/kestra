package io.kestra.webserver.services.ai.agent.data;

import io.kestra.webserver.services.ai.agent.domain.AgentMode;
import io.kestra.webserver.services.ai.agent.domain.AgentScopeBinding;

import io.micronaut.core.annotation.Nullable;

public record ApiChatTurnRequest(
    String prompt,
    @Nullable AgentMode mode,
    @Nullable AgentScopeBinding inFocus,
    @Nullable String providerId) {
}
