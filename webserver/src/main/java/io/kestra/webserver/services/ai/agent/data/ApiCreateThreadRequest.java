package io.kestra.webserver.services.ai.agent.data;

import io.kestra.core.ai.agent.models.AgentMode;

import io.micronaut.core.annotation.Nullable;

public record ApiCreateThreadRequest(@Nullable AgentMode mode, @Nullable String title) {
}
