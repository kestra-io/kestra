package io.kestra.webserver.services.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.docs.JsonSchemaGenerator;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Version;
import io.kestra.core.utils.VersionProvider;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.libs.copilot.models.in.PluginMetadata;
import io.kestra.libs.copilot.services.ai.*;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.webserver.services.posthog.PosthogService;

import io.micronaut.core.annotation.Nullable;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.service.AiServices;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AiService<T extends AiConfiguration> implements AiServiceInterface {
    private final PosthogService postHogService;
    @Getter
    private final T aiConfiguration;
    private final PluginRegistry pluginRegistry;
    private final JsonSchemaGenerator jsonSchemaGenerator;
    private final VersionProvider versionProvider;
    private final FlowAiCopilot<Flow> flowAiCopilot;
    private final DashboardAiCopilot<Dashboard> dashboardAiCopilot;
    private final ExpressionContextService expressionContextService;
    private final PluginDefaultService pluginDefaultService;
    private final NamespaceContextTool namespaceContextTool;
    @Nullable
    private volatile KestraDocsContextTool kestraDocsContextTool;
    private final String instanceUid;
    private final String aiProvider;
    private final String displayName;
    private final List<ChatModelListener> listeners;

    private final Map<String, ConversationMetadata> metadataByConversationId = new ConcurrentHashMap<>();

    public abstract ChatModel chatModel(List<ChatModelListener> listeners);

    protected String baseUrl() {
        return null;
    }

    protected List<ChatModelListener> listeners(String spanName, String conversationId) {
        List<ChatModelListener> listeners = new ArrayList<>(this.listeners);
        listeners.add(new MetadataAppenderChatModelListener(this.instanceUid, this.aiProvider, spanName, this.baseUrl(), () -> metadataByConversationId.get(conversationId)));
        return listeners;
    }

    protected PluginFinder pluginFinder(String conversationId) {
        return AiServices.builder(PluginFinder.class)
            .chatModel(
                this.chatModel(
                    this.listeners("PluginFinder", conversationId)
                )
            )
            .build();
    }

    protected FlowYamlBuilder flowYamlBuilder(String conversationId) {
        var builder = AiServices.builder(FlowYamlBuilder.class)
            .chatModel(this.chatModel(this.listeners("FlowYamlBuilder", conversationId)));
        List<Object> tools = new ArrayList<>();
        if (namespaceContextTool != null) tools.add(namespaceContextTool);
        if (kestraDocsContextTool != null) tools.add(kestraDocsContextTool);
        return tools.isEmpty() ? builder.build() : builder.tools(tools).build();
    }

    protected DashboardYamlBuilder dashboardYamlBuilder(String conversationId) {
        var builder = AiServices.builder(DashboardYamlBuilder.class)
            .chatModel(this.chatModel(this.listeners("DashboardYamlBuilder", conversationId)));
        if (kestraDocsContextTool != null) builder.tools(kestraDocsContextTool);
        return builder.build();
    }

    public AiService(
        final PluginRegistry pluginRegistry,
        final JsonSchemaGenerator jsonSchemaGenerator,
        final VersionProvider versionProvider,
        final InstanceService instanceService,
        final PosthogService postHogService,
        @Nullable final NamespaceContextTool namespaceContextTool,
        final String aiProvider,
        final String displayName,
        final List<ChatModelListener> listeners,
        final T aiConfiguration,
        final ExpressionContextService expressionContextService,
        final PluginDefaultService pluginDefaultService) {
        this.pluginRegistry = pluginRegistry;
        this.jsonSchemaGenerator = jsonSchemaGenerator;
        this.instanceUid = instanceService.fetch();
        this.postHogService = postHogService;
        this.aiProvider = aiProvider;
        this.displayName = displayName;
        this.listeners = listeners;
        this.aiConfiguration = aiConfiguration;
        this.expressionContextService = expressionContextService;
        this.pluginDefaultService = pluginDefaultService;
        this.namespaceContextTool = namespaceContextTool;

        this.flowAiCopilot = new FlowAiCopilot<>(Flow.class);
        this.dashboardAiCopilot = new DashboardAiCopilot<>(Dashboard.class);
        this.versionProvider = versionProvider;
    }

    public void setKestraDocsContextTool(KestraDocsContextTool kestraDocsContextTool) {
        this.kestraDocsContextTool = kestraDocsContextTool;
    }

    @Override
    public GenerationResult generateFlow(UserInfo userInfo, FlowGenerationPrompt flowGenerationPrompt, String tenantId) {
        AiService.GenerationContext ctx = this.beforeGeneration(
            userInfo, flowGenerationPrompt.getConversationId(), "FlowGeneration", Map.of(
                "flowYaml", Optional.ofNullable(flowGenerationPrompt.getYaml()).orElse(""),
                "userPrompt", flowGenerationPrompt.getUserPrompt()
            )
        );

        String pebbleExpressions = buildPebbleExpressions(tenantId, flowGenerationPrompt.getYaml(), flowGenerationPrompt.getNamespace());

        String generatedFlow = flowAiCopilot.generateFlow(
            this.pluginFinder(flowGenerationPrompt.getConversationId()),
            this.flowYamlBuilder(flowGenerationPrompt.getConversationId()),
            (plugins) -> JacksonMapper.ofJson().writeValueAsString(jsonSchemaGenerator.schemas(Flow.class, false, plugins, true)),
            allPluginsMetadata(),
            flowGenerationPrompt,
            tenantId,
            pebbleExpressions
        );

        return GenerationResult.of(this.afterGeneration(ctx, "FlowGenerationResult", Map.of("generatedFlow", generatedFlow), generatedFlow, "generatedFlow"));
    }

    @Override
    public GenerationResult generateDashboard(UserInfo userInfo, DashboardGenerationPrompt dashboardGenerationPrompt) {
        AiService.GenerationContext ctx = this.beforeGeneration(
            userInfo, dashboardGenerationPrompt.getConversationId(), "DashboardGeneration", Map.of(
                "dashboardYaml", Optional.ofNullable(dashboardGenerationPrompt.getYaml()).orElse(""),
                "userPrompt", dashboardGenerationPrompt.getUserPrompt()
            )
        );

        String generatedDashboard = dashboardAiCopilot.generateDashboard(
            this.pluginFinder(dashboardGenerationPrompt.getConversationId()),
            this.dashboardYamlBuilder(dashboardGenerationPrompt.getConversationId()),
            (plugins) -> JacksonMapper.ofJson().writeValueAsString(jsonSchemaGenerator.schemas(Dashboard.class, false, plugins, true)),
            allPluginsMetadata(),
            dashboardGenerationPrompt
        );

        return GenerationResult.of(this.afterGeneration(ctx, "DashboardGenerationResult", Map.of("generatedDashboard", generatedDashboard), generatedDashboard, "generatedDashboard"));
    }

    public String displayName() {
        return displayName;
    }

    public ExpressionContextService expressionContextService() {
        return expressionContextService;
    }

    private String buildPebbleExpressions(@Nullable String tenantId, String flowYaml, @Nullable String namespace) {
        if (flowYaml != null && !flowYaml.isBlank()) {
            try {
                Flow flow = pluginDefaultService.parseFlowWithAllDefaults(tenantId, flowYaml, false);
                return PebbleExpressionsFormatter.format(expressionContextService.buildExpressionContext(flow, null).toDisplayNameMap());
            } catch (Exception e) {
                log.debug("Could not parse flow YAML for pebble expression context, falling back to namespace context: {}", e.getMessage());
            }
        }
        if (namespace != null && !namespace.isBlank()) {
            try {
                Flow flow = Flow.builder()
                    .id("__ai_context__")
                    .namespace(namespace)
                    .tenantId(tenantId)
                    .tasks(List.of())
                    .build();
                return PebbleExpressionsFormatter.format(expressionContextService.buildExpressionContext(flow, null).toDisplayNameMap());
            } catch (Exception e) {
                log.debug("Could not build namespace pebble expression context, falling back to global: {}", e.getMessage());
            }
        }
        return PebbleExpressionsFormatter.format(expressionContextService.buildGlobalExpressionContext().toDisplayNameMap());
    }

    public PluginFinder pluginFinderForConversation(String conversationId) {
        return this.pluginFinder(conversationId);
    }

    public <B> B buildAiService(Class<B> serviceClass, String spanName, String conversationId) {
        var builder = AiServices.builder(serviceClass)
            .chatModel(this.chatModel(this.listeners(spanName, conversationId)));
        if (kestraDocsContextTool != null) builder.tools(kestraDocsContextTool);
        return builder.build();
    }

    public record ConversationMetadata(String conversationId, String ip, String parentSpanId, String uid) {
    }

    public record GenerationContext(String conversationId, String ip, String parentSpanId, String uid) {
    }

    @Override
    public GenerationContext beforeGeneration(UserInfo userInfo, String conversationId, String spanName, Map<String, String> inputState) {
        if (conversationId == null) {

            return null;
        }
        String parentSpanId = IdUtils.create();
        String uid = Objects.requireNonNullElse(userInfo.uid(), "api-call");
        this.postHogService.capture(
            uid, "$ai_trace", Map.of(
                "$ai_trace_id", conversationId,
                "$ai_span_name", spanName + "Session",
                "$ai_input_state", inputState
            )
        );
        this.postHogService.capture(
            uid, "$ai_span", Map.of(
                "$ai_trace_id", conversationId,
                "$ai_span_id", parentSpanId,
                "$ai_span_name", spanName + "Attempt",
                "$ai_input_state", inputState
            )
        );
        metadataByConversationId.put(conversationId, new ConversationMetadata(conversationId, userInfo.ip(), parentSpanId, uid));

        return new GenerationContext(conversationId, userInfo.ip(), parentSpanId, uid);
    }

    @Override
    public String afterGeneration(GenerationContext context, String spanName, Map<String, Object> outputState, String result, String outputKey) {
        if (context == null || context.conversationId() == null) {

            return result;
        }
        metadataByConversationId.remove(context.conversationId());
        Map<String, Object> aiOutput = (outputState == null || outputState.isEmpty()) ? Map.of(outputKey, result) : outputState;
        this.postHogService.capture(
            context.uid(), "$ai_span", Map.of(
                "$ai_trace_id", context.conversationId(),
                "$ai_span_id", IdUtils.create(),
                "$ai_span_name", spanName,
                "$ai_input_state", Map.of(),
                "$ai_output_state", aiOutput
            )
        );

        return result;
    }

    public List<PluginMetadata<ReverseOrderVersion>> allPluginsMetadata() {
        return pluginRegistry.plugins().stream().flatMap(
            parentPlugin ->
            {
                ReverseOrderVersion version = new ReverseOrderVersion(Optional.ofNullable(parentPlugin.version()).orElse(versionProvider.getVersion()));
                return parentPlugin.allClassGrouped().entrySet().stream().flatMap(e -> e.getValue().stream().map(pluginClass ->
                {
                    Schema schemaAnnotation = ((Class<?>) pluginClass).getDeclaredAnnotation(Schema.class);
                    return new PluginMetadata<>(
                        pluginClass.getName(),
                        Optional.ofNullable(schemaAnnotation).map(Schema::title).orElse(null),
                        e.getKey(),
                        Optional.ofNullable(schemaAnnotation).map(Schema::deprecated).orElse(false),
                        version
                    );
                })
                );
            }
        ).toList();
    }

    public static class ReverseOrderVersion implements Comparable<ReverseOrderVersion> {
        private final Version version;

        public ReverseOrderVersion(String version) {
            this.version = Version.of(version);
        }

        @Override
        public int compareTo(ReverseOrderVersion that) {
            return -version.compareTo(that.version);
        }
    }
}
