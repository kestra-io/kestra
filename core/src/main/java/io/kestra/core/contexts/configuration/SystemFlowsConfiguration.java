package io.kestra.core.contexts.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties("kestra.system-flows")
public record SystemFlowsConfiguration(
    @Bindable(defaultValue = "system") String namespace) {
}
