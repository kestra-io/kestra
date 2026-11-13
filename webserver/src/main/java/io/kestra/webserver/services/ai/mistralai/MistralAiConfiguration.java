package io.kestra.webserver.services.ai.mistralai;

import java.time.Duration;
import java.util.Map;

import io.kestra.webserver.services.ai.AiConfiguration;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.swagger.v3.oas.annotations.media.Schema;

public record MistralAiConfiguration(
    String apiKey,
    @Nullable String baseUrl,
    String modelName,
    @Bindable(defaultValue = "0.7") Double temperature,
    @Nullable String clientPem,
    @Schema(description = "Not required but can be useful to add further trust", nullable = true)
    @Nullable String caPem,
    @Nullable Double topP,
    @Bindable(defaultValue = "false") boolean logRequests,
    @Bindable(defaultValue = "false") boolean logResponses,
    @Nullable
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT, keyFormat = StringConvention.RAW) Map<String, String> customHeaders,
    @Nullable Duration timeout) implements AiConfiguration {

    public MistralAiConfiguration {
        if (temperature == null)
            temperature = 0.7;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return MistralAiAiService.TYPE;
    }
}
