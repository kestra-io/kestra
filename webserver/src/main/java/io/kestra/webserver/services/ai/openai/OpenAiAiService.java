package io.kestra.webserver.services.ai.openai;

import java.util.List;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.services.ai.AiService;
import io.kestra.webserver.services.ai.NamespaceContextTool;
import io.kestra.webserver.services.ai.ThinkingEffort;
import io.kestra.webserver.services.posthog.PosthogService;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenAiAiService extends AiService<OpenAiConfiguration> {
    public static final String TYPE = "openai";

    public OpenAiAiService(PluginRegistry pluginRegistry, JsonSchemaGenerator jsonSchemaGenerator, VersionProvider versionProvider, InstanceService instanceService,
        PosthogService posthogService, NamespaceContextTool namespaceContextTool, String displayName, List<ChatModelListener> listeners, OpenAiConfiguration openAiConfiguration,
        ExpressionContextService expressionContextService, FlowParsingService flowParsingService) {
        super(
            pluginRegistry, jsonSchemaGenerator, versionProvider, instanceService, posthogService, namespaceContextTool, TYPE, displayName, listeners, openAiConfiguration,
            expressionContextService, flowParsingService
        );
    }

    @Override
    protected String baseUrl() {
        return getAiConfiguration().baseUrl();
    }

    /** OpenAI reasoning-effort token when thinking is on, else null; {@code none} is sent even with thinking off. */
    private String reasoningEffort(final boolean thinking) {
        ThinkingEffort effort = getAiConfiguration().thinkingEffort();
        if (effort == null || (!thinking && ThinkingEffort.NONE != effort)) {
            return null;
        }
        return effort.value();
    }

    public ChatModel chatModel(List<ChatModelListener> listeners) {
        boolean thinking = getAiConfiguration().thinkingEnabled();
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .topP(getAiConfiguration().topP())
            .temperature(getAiConfiguration().temperature())
            .maxCompletionTokens(getAiConfiguration().maxOutputTokens())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .baseUrl(getAiConfiguration().baseUrl())
            .timeout(getAiConfiguration().timeout())
            .reasoningEffort(reasoningEffort(thinking))
            .returnThinking(thinking)
            .customHeaders(getAiConfiguration().customHeaders());

        pemHttpClientBuilder(getAiConfiguration().clientPem(), getAiConfiguration().caPem())
            .ifPresent(builder::httpClientBuilder);

        return builder.build();
    }

    @Override
    public StreamingChatModel streamingChatModel(List<ChatModelListener> listeners) {
        boolean thinking = getAiConfiguration().thinkingEnabled();
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
            .listeners(listeners)
            .modelName(getAiConfiguration().modelName())
            .apiKey(getAiConfiguration().apiKey())
            .topP(getAiConfiguration().topP())
            .temperature(getAiConfiguration().temperature())
            .maxCompletionTokens(getAiConfiguration().maxOutputTokens())
            .logRequests(getAiConfiguration().logRequests())
            .logResponses(getAiConfiguration().logResponses())
            .baseUrl(getAiConfiguration().baseUrl())
            .timeout(getAiConfiguration().timeout())
            .reasoningEffort(reasoningEffort(thinking))
            .returnThinking(thinking)
            .customHeaders(getAiConfiguration().customHeaders());

        pemHttpClientBuilder(getAiConfiguration().clientPem(), getAiConfiguration().caPem())
            .ifPresent(builder::httpClientBuilder);

        return builder.build();
    }
}
