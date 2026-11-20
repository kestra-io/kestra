package io.kestra.webserver.services.ai.openrouter;

import java.util.List;
import java.util.Optional;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.NamespaceContextTool;
import io.kestra.webserver.services.posthog.PosthogService;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenRouterAiService extends AiService<OpenRouterConfiguration> {
    public static final String TYPE = "open-router";
    public static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";

    public OpenRouterAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, NamespaceContextTool namespaceContextTool, String displayName, List<ChatModelListener> listeners, OpenRouterConfiguration openRouterConfiguration,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, openRouterConfiguration,
            expressionContextService, flowParsingService
        );
    }

    @Override
    protected String baseUrl() {
        return getAiConfiguration().baseUrl() != null ? getAiConfiguration().baseUrl() : DEFAULT_BASE_URL;
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .topP(getAiConfiguration().topP())
            .temperature(getAiConfiguration().temperature())
            .maxCompletionTokens(getAiConfiguration().maxOutputTokens())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .baseUrl(Optional.ofNullable(getAiConfiguration().baseUrl()).orElse(DEFAULT_BASE_URL))
            .returnThinking(false)
            .timeout(getAiConfiguration().timeout())
            .customHeaders(getAiConfiguration().customHeaders());

        pemHttpClientBuilder(getAiConfiguration().clientPem(), getAiConfiguration().caPem())
            .ifPresent(builder::httpClientBuilder);

        return builder.build();
    }

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .topP(getAiConfiguration().topP())
            .temperature(getAiConfiguration().temperature())
            .maxCompletionTokens(getAiConfiguration().maxOutputTokens())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .baseUrl(Optional.ofNullable(getAiConfiguration().baseUrl()).orElse(DEFAULT_BASE_URL))
            .returnThinking(false)
            .timeout(getAiConfiguration().timeout())
            .customHeaders(getAiConfiguration().customHeaders());

        pemHttpClientBuilder(getAiConfiguration().clientPem(), getAiConfiguration().caPem())
            .ifPresent(builder::httpClientBuilder);

        return builder.build();
    }
}
