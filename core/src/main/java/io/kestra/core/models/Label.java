package io.kestra.core.models;

import javax.validation.constraints.NotNull;

public record Label(@NotNull String key, @NotNull String value) {}
