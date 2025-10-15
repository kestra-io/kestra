package io.kestra.webserver.services.ai.gemini;

import io.kestra.webserver.services.ai.AiConfiguration;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.swagger.v3.oas.annotations.media.Schema;

@ConfigurationProperties(value = "kestra.ai.gemini")
public record GeminiConfiguration (
    @Nullable
    String baseUrl,
    String apiKey,
    @Bindable(defaultValue = "gemini-2.5-flash")
    String modelName,
    @Bindable(defaultValue = "0.7")
    Double temperature,
    @Nullable
    Double topP,
    @Nullable
    Integer topK,
    @Nullable
    String clientPem,
    @Schema(description = "Not required but can be useful to add further trust", nullable = true)
    @Nullable
    String caPem,
    @Bindable(defaultValue = "8000")
    int maxOutputTokens,
    @Bindable(defaultValue = "false")
    boolean logRequests,
    @Bindable(defaultValue = "false")
    boolean logResponses
) implements AiConfiguration {}
