package io.kestra.webserver.services.ai.agent.domain;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Nullable;

public record ScopeBinding(
    Kind kind,
    @Nullable String namespace,
    @Nullable String flowId,
    @Nullable String executionId
) {
    public enum Kind {
        FLOW,
        NAMESPACE,
        EXECUTION;

        @JsonCreator
        public static Kind fromString(final String value) {
            if (value == null) {
                return null;
            }
            return Kind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }
}
