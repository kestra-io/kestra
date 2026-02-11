package io.kestra.webserver.services.ai;

import io.micronaut.core.annotation.Nullable;

import java.util.Map;

public record AiProviderConfiguration(
    String id,
    String displayName,
    String type,
    boolean isDefault,
    @Nullable
    Map<String, Object> configuration
) {
}
