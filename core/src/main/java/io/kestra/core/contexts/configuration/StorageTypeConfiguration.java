package io.kestra.core.contexts.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("kestra.storage")
public record StorageTypeConfiguration(
    @Nullable String type) {
}
