package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.mcp.KestraMcpTransportContext;
import io.kestra.mcp.McpServerChangeNotifier;
import io.kestra.mcp.McpServerProvider;
import io.kestra.mcp.McpToolSource;
import io.kestra.mcp.McpToolView;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.McpToolTrigger;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@io.micronaut.context.annotation.Property(name = "kestra.server-type", value = "WEBSERVER")
@io.micronaut.context.annotation.Property(name = "mcp-tool-source-test.enabled", value = "true")
class McpToolSourceTest {
    private static final String MCP_TOOL_PATH = "/api/v1/main/mcp";
    private static final McpJsonMapper MCP_MAPPER = new JacksonMcpJsonMapper(JsonMapper.builder().build());

    private static final McpSchema.JSONRPCRequest INITIALIZE_REQUEST = new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_INITIALIZE,
        1,
        new McpSchema.InitializeRequest(
            ProtocolVersions.MCP_2025_03_26,
            new McpSchema.ClientCapabilities(null, null, null, null),
            new McpSchema.Implementation("test", "1.0.0")
        )
    );

    private static final McpSchema.JSONRPCRequest TOOLS_LIST_REQUEST = new McpSchema.JSONRPCRequest(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_TOOLS_LIST,
        2,
        null
    );

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    McpServerRepositoryInterface mcpServerRepository;

    @Inject
    McpServerChangeNotifier notifier;

    @Inject
    TestPlatformToolSource toolSource;

    @Test
    void shouldListPlatformSourceToolsWhenServerIsProvided() {
        String serverId = providedServerId();
        String sessionId = initialize(serverId);

        String body = client.toBlocking().retrieve(mcpPost(serverId, TOOLS_LIST_REQUEST, sessionId), String.class);

        assertThat(body).contains(TestPlatformToolSource.TOOL_NAME);
    }

    @Test
    void shouldInvokePlatformSourceToolWhenServerIsProvided() {
        String serverId = providedServerId();
        String sessionId = initialize(serverId);
        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest(TestPlatformToolSource.TOOL_NAME, Map.of(), null)
        );

        String body = client.toBlocking().retrieve(mcpPost(serverId, callRequest, sessionId), String.class);

        assertThat(body).contains(TestPlatformToolSource.TOOL_RESULT);
    }

    @Test
    void shouldNotRefreshPlatformServerWhenFlowTargetingSameServerIdChanges() {
        String serverId = providedServerId();
        initialize(serverId);
        int listingsAfterBuild = toolSource.listings(serverId);

        notifier.handleFlowChange(flowWithToolOn(serverId));

        assertThat(toolSource.listings(serverId)).isEqualTo(listingsAfterBuild);
    }

    @Test
    void shouldServeProvidedServerWhenStoredServerHasSameId() {
        String serverId = providedServerId();
        mcpServerRepository.save(
            null,
            new McpServer(TenantService.MAIN_TENANT, serverId, null, null, null, null, null, null, false, false, false, null, null)
        );
        String sessionId = initialize(serverId);

        String body = client.toBlocking().retrieve(mcpPost(serverId, TOOLS_LIST_REQUEST, sessionId), String.class);

        assertThat(body).contains(TestPlatformToolSource.TOOL_NAME);
    }

    private static String providedServerId() {
        return TestPlatformServerProvider.SERVER_ID_PREFIX + IdUtils.create().toLowerCase();
    }

    private GenericFlow flowWithToolOn(String serverId) {
        return GenericFlow.of(
            Flow.builder()
                .id(IdUtils.create())
                .namespace("io.kestra.test")
                .tenantId(TenantService.MAIN_TENANT)
                .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("done")).build()))
                .triggers(
                    List.of(
                        McpToolTrigger.builder()
                            .id("mcp-trigger")
                            .type(McpToolTrigger.class.getName())
                            .toolName("flow-tool")
                            .toolDescription("A flow tool")
                            .mcpServer(serverId)
                            .build()
                    )
                )
                .build()
        );
    }

    private String initialize(String serverId) {
        HttpResponse<?> response = client.toBlocking().exchange(mcpPost(serverId, INITIALIZE_REQUEST), Map.class);
        String sessionId = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }

    private MutableHttpRequest<String> mcpPost(String serverId, McpSchema.JSONRPCMessage message) {
        try {
            return POST(MCP_TOOL_PATH + "/" + serverId, MCP_MAPPER.writeValueAsString(message))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .contentType(MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }

    private MutableHttpRequest<String> mcpPost(String serverId, McpSchema.JSONRPCMessage message, String sessionId) {
        return mcpPost(serverId, message).header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }

    @Singleton
    @Requires(property = "mcp-tool-source-test.enabled", value = "true")
    public static class TestPlatformServerProvider implements McpServerProvider {
        static final String SERVER_ID_PREFIX = "test-platform-";

        @Override
        public McpToolSource.Kind toolSource() {
            return McpToolSource.Kind.PLATFORM;
        }

        @Override
        public Optional<McpServer> get(String tenantId, String serverId) {
            if (!TenantService.MAIN_TENANT.equals(tenantId) || !serverId.startsWith(SERVER_ID_PREFIX)) {
                return Optional.empty();
            }
            return Optional.of(new McpServer(tenantId, serverId, null, null, null, null, null, null, false, false, false, null, null));
        }
    }

    @Singleton
    @Requires(property = "mcp-tool-source-test.enabled", value = "true")
    public static class TestPlatformToolSource implements McpToolSource {
        static final String TOOL_NAME = "platform-echo";
        static final String TOOL_RESULT = "echoed by the platform source";

        private final Map<String, AtomicInteger> listingsByServer = new ConcurrentHashMap<>();

        @Override
        public Kind kind() {
            return Kind.PLATFORM;
        }

        @Override
        public McpToolView view(KestraMcpTransportContext context) {
            String serverId = context.getServerId();
            return new McpToolView() {
                @Override
                public String cacheKey() {
                    return "*";
                }

                @Override
                public List<McpServerFeatures.AsyncToolSpecification> tools() {
                    listingsByServer.computeIfAbsent(serverId, _ -> new AtomicInteger()).incrementAndGet();
                    return List.of(
                        new McpServerFeatures.AsyncToolSpecification(
                            McpSchema.Tool.builder()
                                .name(TOOL_NAME)
                                .description("Echoes a fixed text")
                                .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null))
                                .build(),
                            (_, _) -> Mono.just(McpSchema.CallToolResult.builder().addTextContent(TOOL_RESULT).build())
                        )
                    );
                }
            };
        }

        int listings(String serverId) {
            return listingsByServer.getOrDefault(serverId, new AtomicInteger()).get();
        }
    }
}
