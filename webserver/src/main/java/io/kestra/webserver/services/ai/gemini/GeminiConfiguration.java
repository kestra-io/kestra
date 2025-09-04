package io.kestra.webserver.services.ai.gemini;

import io.kestra.webserver.services.ai.AiConfiguration;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties(value = "kestra.ai.gemini")
public record GeminiConfiguration (
    String apiKey,
    @Bindable(defaultValue = "gemini-2.5-flash")
    String modelName,
    @Bindable(defaultValue = "0.7")
    double temperature,
    @Nullable
    Double topP,
    @Nullable
    Integer topK,
    @Bindable(defaultValue = "50000")
    int maxOutputTokens,
    @Bindable(defaultValue = "false")
    boolean logRequests,
    @Bindable(defaultValue = "false")
    boolean logResponses
) implements AiConfiguration {}
