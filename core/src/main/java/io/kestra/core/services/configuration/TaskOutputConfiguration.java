package io.kestra.core.services.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.task.outputs")
public record TaskOutputConfiguration(
    @Bindable(defaultValue = "-1") Integer limit) {
}
