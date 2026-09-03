package io.kestra.core.contexts.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra")
public record KestraConfiguration(
    @Nullable String url,
    @Nullable KestraEnvironment environment,
    @Bindable(defaultValue = "0") Integer allocatedCpuCores) {

    @ConfigurationProperties("environment")
    public record KestraEnvironment(
        @Nullable String name,
        @Nullable String color) {
    }
}
