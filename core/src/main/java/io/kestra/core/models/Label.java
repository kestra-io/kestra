package io.kestra.core.models;

import jakarta.validation.constraints.NotNull;

public record Label(@NotNull String key, @NotNull String value) {
    public static final String SYSTEM_PREFIX = "system_";
}
