package io.kestra.core.contexts.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("kestra.repository")
public record RepositoryConfiguration(
    @Nullable String type) {
}
