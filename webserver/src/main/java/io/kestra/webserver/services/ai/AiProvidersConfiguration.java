package io.kestra.webserver.services.ai;

import java.util.List;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

@ConfigurationProperties("kestra.ai")
public record AiProvidersConfiguration(
    @Nullable List<AiProviderConfiguration> providers) {
}
