package io.kestra.core.runners.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("kestra.tasks")
public record TasksConfiguration(
    @Nullable TmpDir tmpDir) {

    @ConfigurationProperties("tmpDir")
    public record TmpDir(@Nullable String path) {
    }
}
