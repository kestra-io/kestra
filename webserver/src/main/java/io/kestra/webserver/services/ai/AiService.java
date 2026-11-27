package io.kestra.webserver.services.ai;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
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
import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.services.ExpressionContextService;
import io.kestra.core.services.FlowParsingService;
import io.kestra.core.services.InstanceService;
import io.kestra.core.utils.ToonUtils;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.Version;
import io.kestra.core.utils.VersionProvider;
import io.kestra.libs.copilot.models.in.DashboardGenerationPrompt;
import io.kestra.libs.copilot.models.in.FlowGenerationPrompt;
import io.kestra.libs.copilot.models.in.PluginMetadata;
import io.kestra.libs.copilot.services.ai.*;
import io.kestra.webserver.services.posthog.PosthogService;
import io.kestra.webserver.utils.HttpClientUtils;
import io.kestra.webserver.utils.ProxyAuthorizingHttpClient;

import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.service.AiServices;
import io.micronaut.core.annotation.Nullable;
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
    private final FlowParsingService flowParsingService;
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

    /**
     * Builds the HTTP client of a provider, when it needs anything the langchain4j default does not give:
     * a client PEM certificate (and optional CA) for mutual-TLS, and/or the JVM proxy credentials, which the
     * JDK HTTP client never sends on its own. Returns empty when neither applies, so a caller applies it
     * conditionally: {@code httpClientBuilder(cfg.clientPem(), cfg.caPem()).ifPresent(builder::httpClientBuilder)}.
     * Shared here so every provider's chat and streaming builders get the same setup.
     *
     * @param clientPem the client certificate PEM, or {@code null} when mutual-TLS is not configured.
     * @param caPem the CA certificate PEM, or {@code null}.
     * @return the configured HTTP client builder, or empty when the langchain4j default is enough.
     */
    protected Optional<JdkHttpClientBuilder> httpClientBuilder(@Nullable String clientPem, @Nullable String caPem) {
        boolean proxyCredentials = ProxyAuthorizingHttpClient.isConfigured();
        if (clientPem == null && !proxyCredentials) {
            return Optional.empty();
        }

        HttpClient.Builder httpClientBuilder = clientPem == null ? HttpClient.newBuilder() : pemHttpClient(clientPem, caPem);

        // langchain4j only forces HTTP/1.1 on plain HTTP for its own default client: an "Upgrade: h2c" request
        // is mishandled by many servers, so keep that behaviour now that the client is ours.
        String baseUrl = baseUrl();
        if (baseUrl != null && baseUrl.regionMatches(true, 0, "http:", 0, 5)) {
            httpClientBuilder.version(HttpClient.Version.HTTP_1_1);
        }

        if (proxyCredentials) {
            httpClientBuilder = ProxyAuthorizingHttpClient.builder(httpClientBuilder);
        }

        return Optional.of(
            ((JdkHttpClientBuilder) HttpClientBuilderLoader.loadHttpClientBuilder()).httpClientBuilder(httpClientBuilder)
        );
    }

    private static HttpClient.Builder pemHttpClient(String clientPem, @Nullable String caPem) {
        try (
            ByteArrayInputStream is = new ByteArrayInputStream(clientPem.getBytes(StandardCharsets.UTF_8));
            ByteArrayInputStream caPemIs = caPem == null ? null : new ByteArrayInputStream(caPem.getBytes(StandardCharsets.UTF_8))
        ) {
            return HttpClientUtils.withPemCertificate(is, caPemIs);
        } catch (Exception e) {
            throw new IllegalArgumentException("Exception while trying to setup AI Service certificates", e);
        }
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
        if (namespaceContextTool != null)
            tools.add(namespaceContextTool);
        if (kestraDocsContextTool != null)
            tools.add(kestraDocsContextTool);
        return tools.isEmpty() ? builder.build() : builder.tools(tools).build();
    }

    protected DashboardYamlBuilder dashboardYamlBuilder(String conversationId) {
        var builder = AiServices.builder(DashboardYamlBuilder.class)
            .chatModel(this.chatModel(this.listeners("DashboardYamlBuilder", conversationId)));
        if (kestraDocsContextTool != null)
            builder.tools(kestraDocsContextTool);
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
        final FlowParsingService flowParsingService) {
        this.pluginRegistry = pluginRegistry;
        this.jsonSchemaGenerator = jsonSchemaGenerator;
        this.instanceUid = instanceService.fetch();
        this.postHogService = postHogService;
        this.aiProvider = aiProvider;
        this.displayName = displayName;
        this.listeners = listeners;
        this.aiConfiguration = aiConfiguration;
        this.expressionContextService = expressionContextService;
        this.flowParsingService = flowParsingService;
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
        AiService.GenerationContext ctx = this.beforeGeneration(userInfo, flowGenerationPrompt.getConversationId(), "FlowGeneration");

        String pebbleExpressions = buildPebbleExpressions(tenantId, flowGenerationPrompt.getYaml(), flowGenerationPrompt.getNamespace());

        String generatedFlow = flowAiCopilot.generateFlow(
            this.pluginFinder(flowGenerationPrompt.getConversationId()),
            this.flowYamlBuilder(flowGenerationPrompt.getConversationId()),
            (plugins) -> {
                JsonNode node = JacksonMapper.ofJson().valueToTree(jsonSchemaGenerator.schemas(Flow.class, false, plugins, true));
                return ToonUtils.jsonToToon(node);
            },
            allPluginsMetadata(),
            flowGenerationPrompt,
            tenantId,
            pebbleExpressions
        );

        this.afterGeneration(ctx, "FlowGenerationResult");

        return GenerationResult.of(generatedFlow);
    }

    @Override
    public GenerationResult generateDashboard(UserInfo userInfo, DashboardGenerationPrompt dashboardGenerationPrompt) {
        AiService.GenerationContext ctx = this.beforeGeneration(userInfo, dashboardGenerationPrompt.getConversationId(), "DashboardGeneration");

        String generatedDashboard = dashboardAiCopilot.generateDashboard(
            this.pluginFinder(dashboardGenerationPrompt.getConversationId()),
            this.dashboardYamlBuilder(dashboardGenerationPrompt.getConversationId()),
            (plugins) -> {
                JsonNode node = JacksonMapper.ofJson().valueToTree(jsonSchemaGenerator.schemas(Dashboard.class, false, plugins, true));
                return ToonUtils.jsonToToon(node);
            },
            allPluginsMetadata(),
            dashboardGenerationPrompt
        );

        this.afterGeneration(ctx, "DashboardGenerationResult");

        return GenerationResult.of(generatedDashboard);
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public Optional<AiUsageLimitConfiguration> usageLimit() {
        return Optional.ofNullable(aiConfiguration.usageLimit()).filter(AiUsageLimitConfiguration::enabled);
    }

    public ExpressionContextService expressionContextService() {
        return expressionContextService;
    }

    private String buildPebbleExpressions(@Nullable String tenantId, String flowYaml, @Nullable String namespace) {
        if (flowYaml != null && !flowYaml.isBlank()) {
            try {
                Flow flow = flowParsingService.parse(tenantId, flowYaml, false);
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
        if (kestraDocsContextTool != null)
            builder.tools(kestraDocsContextTool);
        return builder.build();
    }

    public record ConversationMetadata(String conversationId, String ip, String parentSpanId, String uid) {
    }

    public record GenerationContext(String conversationId, String ip, String parentSpanId, String uid) {
    }

    @Override
    public GenerationContext beforeGeneration(UserInfo userInfo, String conversationId, String spanName) {
        if (conversationId == null) {

            return null;
        }
        String parentSpanId = IdUtils.create();
        String uid = Objects.requireNonNullElse(userInfo.uid(), "api-call");
        this.postHogService.capture(
            uid, "$ai_trace", Map.of(
                "$ai_trace_id", conversationId,
                "$ai_span_name", spanName + "Session"
            )
        );
        this.postHogService.capture(
            uid, "$ai_span", Map.of(
                "$ai_trace_id", conversationId,
                "$ai_span_id", parentSpanId,
                "$ai_span_name", spanName + "Attempt"
            )
        );
        metadataByConversationId.put(conversationId, new ConversationMetadata(conversationId, userInfo.ip(), parentSpanId, uid));

        return new GenerationContext(conversationId, userInfo.ip(), parentSpanId, uid);
    }

    @Override
    public void afterGeneration(GenerationContext context, String spanName) {
        if (context == null || context.conversationId() == null) {
            return;
        }
        metadataByConversationId.remove(context.conversationId());
        this.postHogService.capture(
            context.uid(), "$ai_span", Map.of(
                "$ai_trace_id", context.conversationId(),
                "$ai_span_id", IdUtils.create(),
                "$ai_span_name", spanName
            )
        );
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
