package io.kestra.webserver.services.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.service.AiServices;
import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.models.ai.DashboardGenerationPrompt;
import io.kestra.webserver.models.ai.FlowGenerationPrompt;
import io.kestra.webserver.services.posthog.PosthogService;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AiService<T extends AiConfiguration> implements AiServiceInterface {
    private final PosthogService postHogService;
    @Getter
    private final T aiConfiguration;
    private final FlowAiCopilot flowAiCopilot;
    private final DashboardAiCopilot dashboardAiCopilot;
    private final String instanceUid;
    private final String aiProvider;
    private final String displayName;
    private final List<ChatModelListener> listeners;

    private final Map<String, ConversationMetadata> metadataByConversationId = new ConcurrentHashMap<>();

    public abstract ChatModel chatModel(List<ChatModelListener> listeners);

    protected List<ChatModelListener> listeners(String spanName, String conversationId) {
        List<ChatModelListener> listeners = new ArrayList<>(this.listeners);
        listeners.add(new MetadataAppenderChatModelListener(this.instanceUid, this.aiProvider, spanName, () -> metadataByConversationId.get(conversationId)));
        return listeners;
    }

    protected PluginFinder pluginFinder(String conversationId) {
        return AiServices.builder(PluginFinder.class)
            .chatModel(this.chatModel(
                this.listeners("PluginFinder", conversationId)
            ))
            .build();
    }

    protected FlowYamlBuilder flowYamlBuilder(String conversationId) {
        return AiServices.builder(FlowYamlBuilder.class)
            .chatModel(this.chatModel(
                this.listeners("FlowYamlBuilder", conversationId)
            )).build();
    }

    protected DashboardYamlBuilder dashboardYamlBuilder(String conversationId) {
        return AiServices.builder(DashboardYamlBuilder.class)
            .chatModel(this.chatModel(
                this.listeners("DashboardYamlBuilder", conversationId)
            )).build();
    }

    public AiService(
        final PluginRegistry pluginRegistry,
        final JsonSchemaGenerator jsonSchemaGenerator,
        final VersionProvider versionProvider,
        final InstanceService instanceService,
        final PosthogService postHogService,
        final String aiProvider,
        final String displayName,
        final List<ChatModelListener> listeners,
        final T aiConfiguration
    ) {
        this.instanceUid = instanceService.fetch();
        this.postHogService = postHogService;
        this.aiProvider = aiProvider;
        this.displayName = displayName;
        this.listeners = listeners;
        this.aiConfiguration = aiConfiguration;

        this.flowAiCopilot = new FlowAiCopilot(jsonSchemaGenerator, pluginRegistry, versionProvider.getVersion());
        this.dashboardAiCopilot = new DashboardAiCopilot(jsonSchemaGenerator, pluginRegistry, versionProvider.getVersion());
    }

    @Override
    public String generateFlow(String ip, FlowGenerationPrompt flowGenerationPrompt) {
        AiService.GenerationContext ctx = this.beforeGeneration(ip, flowGenerationPrompt.conversationId(), "FlowGeneration", Map.of(
            "flowYaml", flowGenerationPrompt.yaml(),
            "userPrompt", flowGenerationPrompt.userPrompt()
        ));

        String generatedFlow = flowAiCopilot.generateFlow(
            this.pluginFinder(flowGenerationPrompt.conversationId()),
            this.flowYamlBuilder(flowGenerationPrompt.conversationId()),
            flowGenerationPrompt
        );

        return this.afterGeneration(ctx, "FlowGenerationResult", Map.of("generatedFlow", generatedFlow), generatedFlow, "generatedFlow");
    }

    @Override
    public String generateDashboard(String ip, DashboardGenerationPrompt dashboardGenerationPrompt) {
        AiService.GenerationContext ctx = this.beforeGeneration(ip, dashboardGenerationPrompt.conversationId(), "DashboardGeneration", Map.of(
            "dashboardYaml", dashboardGenerationPrompt.yaml(),
            "userPrompt", dashboardGenerationPrompt.userPrompt()
        ));

        String generatedDashboard = dashboardAiCopilot.generateDashboard(
            this.pluginFinder(dashboardGenerationPrompt.conversationId()),
            this.dashboardYamlBuilder(dashboardGenerationPrompt.conversationId()),
            dashboardGenerationPrompt
        );

        return this.afterGeneration(ctx, "DashboardGenerationResult", Map.of("generatedDashboard", generatedDashboard), generatedDashboard, "generatedDashboard");
    }

    public String displayName() {
        return displayName;
    }

    public PluginFinder pluginFinderForConversation(String conversationId) {
        return this.pluginFinder(conversationId);
    }

    public <B> B buildAiService(Class<B> serviceClass, String spanName, String conversationId) {
        return AiServices.builder(serviceClass)
            .chatModel(this.chatModel(this.listeners(spanName, conversationId)))
            .build();
    }

    public record ConversationMetadata(String conversationId, String ip, String parentSpanId) {
    }

    public record GenerationContext(String conversationId, String ip, String parentSpanId) {
    }

    @Override
    public GenerationContext beforeGeneration(String ip, String conversationId, String spanName, Map<String, String> inputState) {
        if (conversationId == null) {

            return null;
        }
        String parentSpanId = IdUtils.create();
        this.postHogService.capture(conversationId, "$ai_trace", Map.of(
            "$ai_trace_id", conversationId,
            "$ai_span_name", spanName + "Session",
            "$ai_input_state", inputState
        ));
        this.postHogService.capture(conversationId, "$ai_span", Map.of(
            "$ai_trace_id", conversationId,
            "$ai_span_id", parentSpanId,
            "$ai_span_name", spanName + "Attempt",
            "$ai_input_state", inputState
        ));
        metadataByConversationId.put(conversationId, new ConversationMetadata(conversationId, ip, parentSpanId));

        return new GenerationContext(conversationId, ip, parentSpanId);
    }

    @Override
    public String afterGeneration(GenerationContext context, String spanName, Map<String, Object> outputState, String result, String outputKey) {
        if (context == null || context.conversationId() == null) {

            return result;
        }
        metadataByConversationId.remove(context.conversationId());
        Map<String, Object> aiOutput = (outputState == null || outputState.isEmpty()) ? Map.of(outputKey, result) : outputState;
        this.postHogService.capture(context.conversationId(), "$ai_span", Map.of(
            "$ai_trace_id", context.conversationId(),
            "$ai_span_id", IdUtils.create(),
            "$ai_span_name", spanName,
            "$ai_input_state", Map.of(),
            "$ai_output_state", aiOutput
        ));

        return result;
    }

}
