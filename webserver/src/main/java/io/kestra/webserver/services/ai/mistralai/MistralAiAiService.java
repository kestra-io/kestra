package io.kestra.webserver.services.ai.mistralai;

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

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MistralAiAiService extends AiService<MistralAiConfiguration> {
    public static final String TYPE = "mistralai";

    public MistralAiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, NamespaceContextTool namespaceContextTool, String displayName, List<ChatModelListener> listeners, MistralAiConfiguration mistralAiConfiguration,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, mistralAiConfiguration,
            expressionContextService, flowParsingService
        );
    }

    @Override
    protected String baseUrl() {
        return getAiConfiguration().baseUrl();
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        MistralAiChatModel.MistralAiChatModelBuilder builder = MistralAiChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .baseUrl(getAiConfiguration().baseUrl())
            .temperature(getAiConfiguration().temperature())
            .topP(getAiConfiguration().topP())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .customHeaders(getAiConfiguration().customHeaders())
            .timeout(getAiConfiguration().timeout());

        pemHttpClientBuilder(getAiConfiguration().clientPem(), getAiConfiguration().caPem())
            .ifPresent(builder::httpClientBuilder);

        return builder
            .build();
    }

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        MistralAiStreamingChatModel.MistralAiStreamingChatModelBuilder builder = MistralAiStreamingChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .baseUrl(getAiConfiguration().baseUrl())
            .temperature(getAiConfiguration().temperature())
            .topP(getAiConfiguration().topP())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .customHeaders(getAiConfiguration().customHeaders())
            .timeout(getAiConfiguration().timeout());

        pemHttpClientBuilder(getAiConfiguration().clientPem(), getAiConfiguration().caPem())
            .ifPresent(builder::httpClientBuilder);

        return builder
            .build();
    }
}
