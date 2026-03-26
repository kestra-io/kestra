package io.kestra.webserver.services.ai;

import java.util.Map;

import io.micronaut.core.annotation.Nullable;

public record AiProviderConfiguration(
    String id,
    String displayName,
    String type,
    boolean isDefault,
    @Nullable Map<String, Object> configuration) {
}
