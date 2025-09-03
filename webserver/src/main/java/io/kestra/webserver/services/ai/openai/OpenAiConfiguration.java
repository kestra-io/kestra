package io.kestra.webserver.services.ai.openai;

import io.kestra.webserver.services.ai.AiConfiguration;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;

@ConfigurationProperties(value = "kestra.ai.openai")
public record OpenAiConfiguration(
    String apiKey,
    @Bindable(defaultValue = "gpt-5-nano")
    String modelName,
    @Nullable
    Double topP,
    @Bindable(defaultValue = "50000")
    int maxOutputTokens,
    @Bindable(defaultValue = "false")
    boolean logRequests,
    @Bindable(defaultValue = "false")
    boolean logResponses,
    @Nullable
    String baseUrl
) implements AiConfiguration {}
