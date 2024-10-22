package io.kestra.core.models;

import jakarta.validation.constraints.NotNull;

public record Label(@NotNull String key, @NotNull String value) {
    public static final String SYSTEM_PREFIX = "system_";

    // system labels
    public static final String CORRELATION_ID = SYSTEM_PREFIX + "correlationId";
    public static final String USERNAME = SYSTEM_PREFIX + "username";
    public static final String APP = SYSTEM_PREFIX + "app";
}
