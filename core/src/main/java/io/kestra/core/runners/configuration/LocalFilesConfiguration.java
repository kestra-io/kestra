package io.kestra.core.runners.configuration;

import java.util.List;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.local-files")
public record LocalFilesConfiguration(
    @Nullable List<String> allowedPaths,
    @Bindable(defaultValue = "true") Boolean enableFileFunctions,
    @Bindable(defaultValue = "true") Boolean enablePreview) {
}
