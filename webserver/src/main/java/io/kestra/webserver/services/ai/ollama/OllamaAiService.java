package io.kestra.webserver.services.ai.ollama;

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
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OllamaAiService extends AiService<OllamaConfiguration> {
    public static final String TYPE = "ollama";

    public OllamaAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, NamespaceContextTool namespaceContextTool, String displayName, List<ChatModelListener> listeners, OllamaConfiguration ollamaConfiguration,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, ollamaConfiguration,
            expressionContextService, flowParsingService
        );
    }

    @Override
    protected String baseUrl() {
        return getAiConfiguration().baseUrl();
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
            .baseUrl(getAiConfiguration().baseUrl())
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .temperature(getAiConfiguration().temperature())
            .defaultRequestParameters(
                DefaultChatRequestParameters.builder()
                    .maxOutputTokens(getAiConfiguration().maxOutputTokens())
                    .build()
            )
            .topP(getAiConfiguration().topP())
            .topK(getAiConfiguration().topK())
            .think(false)
            .returnThinking(false)
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

    /**
     * Streaming counterpart of {@link #chatModel(List)}, backing the AI Copilot v2 agentic loop's
     * token stream. Mirrors the chat-model configuration so the agent and one-shot generation share
     * provider tuning. (Tool-calling in the loop still depends on the configured Ollama model
     * supporting tools.)
     */
    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        OllamaStreamingChatModel.OllamaStreamingChatModelBuilder builder = OllamaStreamingChatModel.builder()
            .baseUrl(getAiConfiguration().baseUrl())
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .temperature(getAiConfiguration().temperature())
            .defaultRequestParameters(
                DefaultChatRequestParameters.builder()
                    .maxOutputTokens(getAiConfiguration().maxOutputTokens())
                    .build()
            )
            .topP(getAiConfiguration().topP())
            .topK(getAiConfiguration().topK())
            .think(false)
            .returnThinking(false)
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
