package io.kestra.core.ai.agent.models;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

public enum ArtefactKind {
    FLOW,
    DASHBOARD,
    APP;

    @JsonCreator
    public static ArtefactKind fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, ArtefactKind.class);
    }
}
