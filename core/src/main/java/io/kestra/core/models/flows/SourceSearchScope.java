package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

public enum SourceSearchScope {
    ALL,
    TASKS,
    TRIGGERS,
    INPUTS;

    @JsonCreator
    public static SourceSearchScope fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, SourceSearchScope.class, ALL);
    }
}
