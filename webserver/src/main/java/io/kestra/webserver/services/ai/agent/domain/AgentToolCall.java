package io.kestra.webserver.services.ai.agent.domain;

import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Nullable;

public record AgentToolCall(
    @Nullable String id,
    Kind kind,
    String tool,
    @Nullable AgentToolFamily family,
    Map<String, Object> arguments
) {
    public enum Kind {
        PLATFORM,
        AUTHORING;

        @JsonCreator
        public static Kind fromString(final String value) {
            if (value == null) {
                return null;
            }
            return Kind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
    }

    public static AgentToolCall platform(final String id, final String tool, final AgentToolFamily family, final Map<String, Object> arguments) {
        return new AgentToolCall(id, Kind.PLATFORM, tool, family, arguments);
    }
}
