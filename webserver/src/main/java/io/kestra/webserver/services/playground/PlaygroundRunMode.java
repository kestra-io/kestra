package io.kestra.webserver.services.playground;

import com.fasterxml.jackson.annotation.JsonCreator;

import io.kestra.core.utils.Enums;

public enum PlaygroundRunMode {
    UP_TO,
    FROM,
    UNKNOWN;

    @JsonCreator
    public static PlaygroundRunMode fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, PlaygroundRunMode.class, UNKNOWN);
    }
}
