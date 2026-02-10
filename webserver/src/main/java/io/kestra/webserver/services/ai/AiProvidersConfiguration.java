package io.kestra.webserver.services.ai;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;

import java.util.List;

@ConfigurationProperties("kestra.ai")
public record AiProvidersConfiguration(
    @Nullable
    List<AiProviderConfiguration> providers
) {
}
