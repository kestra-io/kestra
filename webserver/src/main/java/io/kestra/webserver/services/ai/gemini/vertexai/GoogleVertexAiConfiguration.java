package io.kestra.webserver.services.ai.gemini.vertexai;

import java.util.Map;

import io.kestra.webserver.services.ai.AiConfiguration;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

public record GoogleVertexAiConfiguration(
    String location,
    String project,
    String modelName,
    @Bindable(defaultValue = "0.7") Double temperature,
    @Nullable Double topP,
    @Nullable Integer topK,
    @Bindable(defaultValue = "false") boolean logRequests,
    @Bindable(defaultValue = "false") boolean logResponses,
    @Nullable
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT, keyFormat = StringConvention.RAW) Map<String, String> customHeaders) implements AiConfiguration {
    public GoogleVertexAiConfiguration {
        if (temperature == null)
            temperature = 0.7;
    }

    @Override
    public String type() {
        return "google-vertexai";
    }
}
