package io.kestra.webserver.services.ai.amazon.bedrock;

import java.time.Duration;
import java.util.Map;

import io.kestra.webserver.services.ai.AiConfiguration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;

@ConfigurationProperties(value = "kestra.ai.bedrock")
public record AmazonBedrockConfiguration(
    String accessKeyId,
    String secretAccessKey,
    String modelName,
    @Bindable(defaultValue = "0.7") Double temperature,
    @Bindable(defaultValue = "8000") int maxOutputTokens,
    @Nullable Double topP,
    @Nullable Integer topK,
    @Bindable(defaultValue = "false") boolean logRequests,
    @Bindable(defaultValue = "false") boolean logResponses,
    @Bindable(defaultValue = "false") boolean thinkingEnabled,
    @Nullable Integer thinkingBudgetTokens,
    @Nullable
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT, keyFormat = StringConvention.RAW) Map<String, String> customHeaders,
    Duration timeout) implements AiConfiguration {
    public AmazonBedrockConfiguration {
        if (temperature == null)
            temperature = 0.7;
        if (maxOutputTokens == 0)
            maxOutputTokens = 8000;
    }

    @Override
    public String type() {
        return "amazon-bedrock";
    }
}
