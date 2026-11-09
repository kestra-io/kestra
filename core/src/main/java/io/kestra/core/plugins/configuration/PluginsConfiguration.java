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

    private static final Duration MAX_READ_TIMEOUT = Duration.ofMillis(Integer.MAX_VALUE);

    public PluginsConfiguration {
        if (readTimeout != null && (!readTimeout.isPositive() || readTimeout.compareTo(MAX_READ_TIMEOUT) > 0)) {
            throw new IllegalArgumentException(
                "The 'kestra.plugins.read-timeout' value must be greater than 0 and at most %s, but was %s.".formatted(MAX_READ_TIMEOUT, readTimeout)
            );
        }
    }
}
