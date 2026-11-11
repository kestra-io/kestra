package io.kestra.webserver.services.ai.gemini.vertexai;

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
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GoogleVertexAiAiService extends AiService<GoogleVertexAiConfiguration> {
    public static final String TYPE = "googlevertexai";

    public GoogleVertexAiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, NamespaceContextTool namespaceContextTool, String displayName, List<ChatModelListener> listeners,
        GoogleVertexAiConfiguration googleVertexAiConfiguration,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, googleVertexAiConfiguration,
            expressionContextService, flowParsingService
        );
    }

    @Override
    protected String baseUrl() {
        return "https://" + getAiConfiguration().location() + "-aiplatform.googleapis.com";
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        return VertexAiGeminiChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .location(getAiConfiguration().location())
            .project(getAiConfiguration().project())
            .temperature(getAiConfiguration().temperature().floatValue())
            .topK(getAiConfiguration().topK())
            .topP(getAiConfiguration().topP() != null ? getAiConfiguration().topP().floatValue() : null)
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .customHeaders(getAiConfiguration().customHeaders())
            .build();
    }

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        return VertexAiGeminiStreamingChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .location(getAiConfiguration().location())
            .project(getAiConfiguration().project())
            .temperature(getAiConfiguration().temperature().floatValue())
            .topK(getAiConfiguration().topK())
            .topP(getAiConfiguration().topP() != null ? getAiConfiguration().topP().floatValue() : null)
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .customHeaders(getAiConfiguration().customHeaders())
            .build();
    }
}
