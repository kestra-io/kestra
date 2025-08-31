package io.kestra.webserver.services.ai.gemini;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties(value = "kestra.ai.gemini")
public record GeminiConfiguration (
    String apiKey,
    @Bindable(defaultValue = "gemini-2.5-flash")
    String modelName
) {}
