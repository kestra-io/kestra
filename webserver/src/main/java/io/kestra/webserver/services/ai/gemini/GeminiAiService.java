package io.kestra.webserver.services.ai.gemini;

import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.posthog.PosthogService;
import io.kestra.webserver.utils.HttpClientUtils;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Singleton
@Requires(property = "kestra.ai.type", value = GeminiAiService.TYPE)
@Requires(property = "kestra.ai.gemini.api-key")
@Slf4j
public class GeminiAiService extends AiService<GeminiConfiguration> {
    public static final String TYPE = "gemini";

    public GeminiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService, PosthogService posthogService, List<ChatModelListener> listeners, GeminiConfiguration geminiConfiguration) {
        super(pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, TYPE, listeners, geminiConfiguration);
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        GoogleAiGeminiChatModel.GoogleAiGeminiChatModelBuilder builder = GoogleAiGeminiChatModel.builder()
            .baseUrl(getAiConfiguration().baseUrl())
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .temperature(getAiConfiguration().temperature())
            .topP(getAiConfiguration().topP())
            .topK(getAiConfiguration().topK())
            .maxOutputTokens(getAiConfiguration().maxOutputTokens())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .thinkingConfig(GeminiThinkingConfig.builder().includeThoughts(false).build())
            .returnThinking(false);

        if (getAiConfiguration().clientPem() != null) {
            try (ByteArrayInputStream is = new ByteArrayInputStream(getAiConfiguration().clientPem().getBytes(StandardCharsets.UTF_8));
                 ByteArrayInputStream caPem = getAiConfiguration().caPem() == null ? null : new ByteArrayInputStream(getAiConfiguration().caPem().getBytes(StandardCharsets.UTF_8))) {
                JdkHttpClientBuilder jdkHttpClientBuilder = ((JdkHttpClientBuilder) HttpClientBuilderLoader.loadHttpClientBuilder()).httpClientBuilder(
                    HttpClientUtils.withPemCertificate(is, caPem)
                );

                builder = builder.httpClientBuilder(jdkHttpClientBuilder);
            } catch (Exception e) {
                throw new IllegalArgumentException("Exception while trying to setup AI Service certificates", e);
            }
        }

        return builder.build();
    }
}
