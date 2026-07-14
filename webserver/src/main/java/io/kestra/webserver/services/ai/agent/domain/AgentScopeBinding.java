package io.kestra.webserver.services.ai.agent.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

import io.micronaut.core.annotation.Nullable;

public record AgentScopeBinding(
    Kind kind,
    @Nullable String namespace,
    @Nullable String flowId,
    @Nullable String executionId) {
    public enum Kind {
        FLOW,
        NAMESPACE,
        EXECUTION;

        @JsonCreator
        public static Kind fromString(final String value) {
            return Enums.getForNameIgnoreCase(value, Kind.class);
        }
    }
}
