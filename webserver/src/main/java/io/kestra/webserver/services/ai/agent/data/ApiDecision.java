package io.kestra.webserver.services.ai.agent.data;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ApiDecision {
    APPROVE,
    REJECT;

    @JsonCreator
    public static ApiDecision fromString(final String value) {
        if (value == null) {
            return null;
        }
        return ApiDecision.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
