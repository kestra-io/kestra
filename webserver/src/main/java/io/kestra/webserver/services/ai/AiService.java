package io.kestra.webserver.services.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.service.AiServices;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import io.kestra.webserver.services.posthog.PosthogService;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AiService<T extends AiConfiguration> implements AiServiceInterface {
    private final PosthogService postHogService;
    @Getter
    private final T aiConfiguration;
    private final FlowAiCopilot flowAiCopilot;
    private final String instanceUid;
    private final String aiProvider;
    private final List<ChatModelListener> listeners;

    private final Map<String, ConversationMetadata> metadataByConversationId = new ConcurrentHashMap<>();

    public abstract ChatModel chatModel(List<ChatModelListener> listeners);

    private List<ChatModelListener> listeners(String spanName, String conversationId) {
        List<ChatModelListener> listeners = new ArrayList<>(this.listeners);
        listeners.add(new MetadataAppenderChatModelListener(this.instanceUid, this.aiProvider, spanName, () -> metadataByConversationId.get(conversationId)));
        return listeners;
    }

    private PluginFinder pluginFinder(String conversationId) {
        return AiServices.builder(PluginFinder.class)
            .chatModel(this.chatModel(
                this.listeners("PluginFinder", conversationId)
            ))
            .build();
    }

    private FlowYamlBuilder flowYamlBuilder(String conversationId) {
        return AiServices.builder(FlowYamlBuilder.class)
            .chatModel(this.chatModel(
                this.listeners("FlowYamlBuilder", conversationId)
            )).build();
    }

    public AiService(
        final PluginRegistry pluginRegistry,
        final JsonSchemaGenerator jsonSchemaGenerator,
        final VersionProvider versionProvider,
        final InstanceService instanceService,
        final PosthogService postHogService,
        final String aiProvider,
        final List<ChatModelListener> listeners,
        final T aiConfiguration
    ) {
        this.instanceUid = instanceService.fetch();
        this.postHogService = postHogService;
        this.aiProvider = aiProvider;
        this.listeners = listeners;
        this.aiConfiguration = aiConfiguration;

        this.flowAiCopilot = new FlowAiCopilot(jsonSchemaGenerator, pluginRegistry, versionProvider.getVersion());
    }

    public String generateFlow(String ip, FlowGenerationPrompt flowGenerationPrompt) {
        String parentSpanId = IdUtils.create();
        Map<String, String> inputState = new HashMap<>();
        inputState.put("flowYaml", flowGenerationPrompt.flowYaml());
        inputState.put("userPrompt", flowGenerationPrompt.userPrompt());

        this.postHogService.capture(flowGenerationPrompt.conversationId(), "$ai_trace", Map.of(
            "$ai_trace_id", flowGenerationPrompt.conversationId(),
            "$ai_span_name", "FlowGenerationSession",
            "$ai_input_state", inputState
        ));

        this.postHogService.capture(flowGenerationPrompt.conversationId(), "$ai_span", Map.of(
            "$ai_trace_id", flowGenerationPrompt.conversationId(),
            "$ai_span_id", parentSpanId,
            "$ai_span_name", "FlowGenerationAttempt",
            "$ai_input_state", inputState
        ));

        metadataByConversationId.put(flowGenerationPrompt.conversationId(), new ConversationMetadata(flowGenerationPrompt.conversationId(), ip, parentSpanId));
        String generatedFlow = flowAiCopilot.generateFlow(
            this.pluginFinder(flowGenerationPrompt.conversationId()),
            this.flowYamlBuilder(flowGenerationPrompt.conversationId()),
            flowGenerationPrompt
        );
        metadataByConversationId.remove(flowGenerationPrompt.conversationId());

        this.postHogService.capture(flowGenerationPrompt.conversationId(), "$ai_span", Map.of(
            "$ai_trace_id", flowGenerationPrompt.conversationId(),
            "$ai_span_id", IdUtils.create(),
            "$ai_span_name", "FlowGenerationResult",
            "$ai_input_state", inputState,
            "$ai_output_state", Map.of("generatedFlow", generatedFlow)
        ));
        return generatedFlow;
    }

    public record ConversationMetadata(String conversationId, String ip, String parentSpanId) {
    }
}
