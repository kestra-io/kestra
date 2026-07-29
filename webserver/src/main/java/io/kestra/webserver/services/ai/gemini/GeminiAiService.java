package io.kestra.webserver.services.ai.gemini;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.NamespaceContextTool;
import io.kestra.webserver.services.posthog.PosthogService;
import io.kestra.webserver.utils.HttpClientUtils;

import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.googleai.GeminiThinkingConfig;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import io.micronaut.core.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GeminiAiService extends AiService<GeminiConfiguration> {
    public static final String TYPE = "gemini";

    public GeminiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, @Nullable NamespaceContextTool namespaceContextTool,
        String displayName, List<ChatModelListener> listeners, GeminiConfiguration geminiConfiguration, ExpressionContextService expressionContextService,
        FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, geminiConfiguration,
            expressionContextService, flowParsingService
        );
    }

    private static final String DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com";

    @Override
    protected String baseUrl() {
        return getAiConfiguration().baseUrl() != null ? getAiConfiguration().baseUrl() : DEFAULT_BASE_URL;
    }

    /**
     * Builds the thinking configuration for the model. {@code includeThoughts(false)} always applies — we
     * never need the human-readable thought summaries, only the signatures. When thinking is enabled we tune
     * the depth: a {@code thinkingLevel} for Gemini 3.x (effort) or a {@code thinkingBudget} for Gemini 2.5
     * (a null budget lets the model pick its default). When disabled we leave the budget unset: Gemini 2.5
     * thinks by default, so we cannot reliably force it off, which is precisely why the signature round-trip
     * below stays on regardless.
     */
    private GeminiThinkingConfig thinkingConfig() {
        GeminiThinkingConfig.Builder builder = GeminiThinkingConfig.builder().includeThoughts(false);
        if (getAiConfiguration().thinkingEnabled()) {
            if (getAiConfiguration().thinkingEffort() != null) {
                builder.thinkingLevel(getAiConfiguration().thinkingEffort().value());
            } else if (getAiConfiguration().thinkingBudgetTokens() != null) {
                builder.thinkingBudget(getAiConfiguration().thinkingBudgetTokens());
            }
        }
        return builder.build();
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
            .thinkingConfig(thinkingConfig())
            .returnThinking(true)
            .sendThinking(true)
            .timeout(getAiConfiguration().timeout());

        if (getAiConfiguration().clientPem() != null) {
            try (
                ByteArrayInputStream is = new ByteArrayInputStream(getAiConfiguration().clientPem().getBytes(StandardCharsets.UTF_8));
                ByteArrayInputStream caPem = getAiConfiguration().caPem() == null ? null : new ByteArrayInputStream(getAiConfiguration().caPem().getBytes(StandardCharsets.UTF_8))
            ) {
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

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        return GoogleAiGeminiStreamingChatModel.builder()
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
            .thinkingConfig(thinkingConfig())
            .returnThinking(true)
            .sendThinking(true)
            .timeout(getAiConfiguration().timeout())
            .build();
    }
}
