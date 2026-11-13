package io.kestra.webserver.services.ai.azure.openai;

import java.time.Duration;
import java.util.Map;

import io.kestra.webserver.services.ai.AiConfiguration;
import io.kestra.webserver.services.ai.ThinkingEffort;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

public record AzureOpenAiConfiguration(
    @Nullable String apiKey,
    @Nullable String tenantId,
    @Nullable String serviceVersion,
    String endpoint,
    @Nullable String clientId,
    @Nullable String clientSecret,
    String modelName,
    @Bindable(defaultValue = "8000") int maxOutputTokens,
    @Bindable(defaultValue = "1") Double temperature,
    @Nullable Double topP,
    @Bindable(defaultValue = "false") boolean logRequestsAndResponses,
    @Bindable(defaultValue = "false") boolean thinkingEnabled,
    @Nullable ThinkingEffort thinkingEffort,
    @Nullable
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT, keyFormat = StringConvention.RAW) Map<String, String> customHeaders,
    Duration timeout) implements AiConfiguration {
    public AzureOpenAiConfiguration {
        if (maxOutputTokens == 0)
            maxOutputTokens = 8000;
        if (temperature == null)
            temperature = 1.0;
    }

    @Override
    public String type() {
        return "azure-openai";
    }
}
