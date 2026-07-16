package io.kestra.webserver.services.ai.agent.data;

import io.kestra.core.ai.agent.models.AgentMode;
import io.kestra.core.ai.agent.models.AgentScopeBinding;

import io.micronaut.core.annotation.Nullable;

public record ApiChatTurnRequest(
    String prompt,
    @Nullable AgentMode mode,
    @Nullable AgentScopeBinding inFocus,
    @Nullable String providerId) {
}
