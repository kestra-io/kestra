package io.kestra.core.plugins.configuration;

import java.time.Duration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

/**
 * @param readTimeout how long a plugin download may receive no byte before it fails, 2 minutes when unset.
 */
@ConfigurationProperties("kestra.plugins")
public record PluginsConfiguration(
    @Nullable String localRepositoryPath,
    @Nullable Duration readTimeout) {
}
