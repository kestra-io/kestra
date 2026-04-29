package io.kestra.core.runners.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("kestra.tasks.tmp-dir")
public record WorkingDirConfiguration(
    @Nullable String path) {
}
