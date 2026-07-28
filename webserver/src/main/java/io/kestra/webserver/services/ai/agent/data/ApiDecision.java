package io.kestra.webserver.services.ai.agent.data;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

public enum ApiDecision {
    APPROVE,
    REJECT;

    @JsonCreator
    public static ApiDecision fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, ApiDecision.class);
    }
}
