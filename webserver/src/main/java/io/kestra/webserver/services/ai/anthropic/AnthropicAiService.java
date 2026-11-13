package io.kestra.webserver.services.ai.anthropic;

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
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnthropicAiService extends AiService<AnthropicConfiguration> {
    public static final String TYPE = "anthropic";

    /** Anthropic requires the thinking budget to be at least 1024 tokens; used when thinking is enabled with no explicit budget. */
    private static final int DEFAULT_THINKING_BUDGET_TOKENS = 1024;

    public AnthropicAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, NamespaceContextTool namespaceContextTool, String displayName, List<ChatModelListener> listeners, AnthropicConfiguration anthropicConfiguration,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, anthropicConfiguration,
            expressionContextService, flowParsingService
        );
    }

    @Override
    protected String baseUrl() {
        return "https://api.anthropic.com";
    }

    /** The configured thinking budget, or the Anthropic minimum when thinking is enabled without an explicit budget. */
    private int thinkingBudgetTokens() {
        Integer configured = getAiConfiguration().thinkingBudgetTokens();
        return configured != null ? configured : DEFAULT_THINKING_BUDGET_TOKENS;
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        boolean thinking = getAiConfiguration().thinkingEnabled();
        AnthropicChatModel.AnthropicChatModelBuilder builder = AnthropicChatModel.builder()
            .listeners(listeners)
            .maxTokens(getAiConfiguration().maxOutputTokens())
            .modelName(getAiConfiguration().modelName())
            // Anthropic requires temperature=1 and no topP/topK when extended thinking is on.
            .temperature(thinking ? 1.0 : getAiConfiguration().temperature())
            .topP(thinking ? null : getAiConfiguration().topP())
            .topK(thinking ? null : getAiConfiguration().topK())
            .thinkingType(thinking ? "enabled" : "disabled")
            .thinkingBudgetTokens(thinking ? thinkingBudgetTokens() : null)
            .returnThinking(thinking)
            .sendThinking(thinking)
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .customHeaders(getAiConfiguration().customHeaders())
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

        return builder
            .build();
    }

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        boolean thinking = getAiConfiguration().thinkingEnabled();
        AnthropicStreamingChatModel.AnthropicStreamingChatModelBuilder builder = AnthropicStreamingChatModel.builder()
            .listeners(listeners)
            .maxTokens(getAiConfiguration().maxOutputTokens())
            .modelName(getAiConfiguration().modelName())
            // Anthropic requires temperature=1 and no topP/topK when extended thinking is on.
            .temperature(thinking ? 1.0 : getAiConfiguration().temperature())
            .topP(thinking ? null : getAiConfiguration().topP())
            .topK(thinking ? null : getAiConfiguration().topK())
            .thinkingType(thinking ? "enabled" : "disabled")
            .thinkingBudgetTokens(thinking ? thinkingBudgetTokens() : null)
            .returnThinking(thinking)
            .sendThinking(thinking)
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .customHeaders(getAiConfiguration().customHeaders())
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

        return builder
            .build();
    }
}
