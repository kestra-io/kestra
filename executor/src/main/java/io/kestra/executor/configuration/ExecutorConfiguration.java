package io.kestra.executor.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.executor")
public record ExecutorConfiguration(
    @Bindable(defaultValue = "0") Integer threadCount) {
}
