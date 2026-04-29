package io.kestra.core.plugins.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("kestra.plugins")
public record PluginsConfiguration(
    @Nullable String localRepositoryPath) {
}
