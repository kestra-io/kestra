package io.kestra.core.contexts.configuration;

import java.util.Optional;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("kestra.storage")
public record StorageConfiguration(
    Optional<String> type) {
}
