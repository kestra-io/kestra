package io.kestra.webserver.services.ai.ollama;

import java.time.Duration;
import java.util.Map;

import io.kestra.webserver.services.ai.AiConfiguration;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.swagger.v3.oas.annotations.media.Schema;

public record OllamaConfiguration(
    String baseUrl,
    String modelName,
    @Bindable(defaultValue = "0.7") Double temperature,
    @Bindable(defaultValue = "8000") int maxOutputTokens,
    @Nullable String clientPem,
    @Schema(description = "Not required but can be useful to add further trust", nullable = true)
    @Nullable String caPem,
    @Nullable Double topP,
    @Nullable Integer topK,
    @Bindable(defaultValue = "false") boolean logRequests,
    @Bindable(defaultValue = "false") boolean logResponses,
    @Nullable
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT, keyFormat = StringConvention.RAW) Map<String, String> customHeaders,
    @Nullable Duration timeout) implements AiConfiguration {

    public OllamaConfiguration {
        if (temperature == null)
            temperature = 0.7;
        if (maxOutputTokens == 0)
            maxOutputTokens = 8000;
    }

    /** {@inheritDoc} **/
    @Override
    public String type() {
        return OllamaAiService.TYPE;
    }
}
