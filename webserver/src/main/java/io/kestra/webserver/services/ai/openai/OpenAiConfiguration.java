package io.kestra.webserver.services.ai.openai;

import java.time.Duration;
import java.util.Map;

import io.kestra.webserver.services.ai.AiConfiguration;
import io.kestra.webserver.services.ai.ThinkingEffort;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.swagger.v3.oas.annotations.media.Schema;

public record OpenAiConfiguration(
    String apiKey,
    @Bindable(defaultValue = "gpt-5-nano") String modelName,
    @Nullable Double topP,
    @Bindable(defaultValue = "1") Double temperature,
    @Nullable String clientPem,
    @Schema(description = "Not required but can be useful to add further trust", nullable = true)
    @Nullable String caPem,
    @Bindable(defaultValue = "8000") int maxOutputTokens,
    @Bindable(defaultValue = "false") boolean logRequests,
    @Bindable(defaultValue = "false") boolean logResponses,
    @Bindable(defaultValue = "false") boolean thinkingEnabled,
    @Nullable ThinkingEffort thinkingEffort,
    @Nullable String baseUrl,
    @Nullable
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT, keyFormat = StringConvention.RAW) Map<String, String> customHeaders,
    @Nullable Duration timeout) implements AiConfiguration {

    public OpenAiConfiguration {
        if (modelName == null)
            modelName = "gpt-5-nano";
        if (temperature == null)
            temperature = 1.0;
        if (maxOutputTokens == 0)
            maxOutputTokens = 8000;
    }

    /** {@inheritDoc} **/
    @Override
    public String type() {
        return OpenAiAiService.TYPE;
    }
}
