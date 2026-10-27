package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.debug.Return;
import io.kestra.webserver.filter.TestAuthFilter;
import io.kestra.webserver.services.ai.agent.tool.DocsMcpToolProvider;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import tools.jackson.databind.json.JsonMapper;

import static io.micronaut.http.HttpRequest.DELETE;
import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
@io.micronaut.context.annotation.Property(name = "kestra.server-type", value = "WEBSERVER")
@io.micronaut.context.annotation.Property(name = "kestra.mcp.platform-server.enabled", value = "true")
class McpPlatformControllerTest {
    private static final String PLATFORM_PATH = "/api/v1/mcp";
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
    FlowRepositoryInterface flowRepository;

    @MockBean(DocsMcpToolProvider.class)
    DocsMcpToolProvider docsMcpToolProvider() {
        DocsMcpToolProvider provider = mock(DocsMcpToolProvider.class);
        when(provider.tools()).thenReturn(Map.of());
        return provider;
    }

    @Test
    void shouldListPlatformToolsWithTenantArgumentWhenServerIsEnabled() {
        String sessionId = initialize();

        String body = client.toBlocking().retrieve(mcpPost(TOOLS_LIST_REQUEST, sessionId), String.class);

        assertThat(body).contains("\"read-flow\"", "\"restart-execution\"", "\"tenant\"", "\"readOnlyHint\":true");
    }

    @Test
    void shouldCallToolInTenantWhenTenantArgumentIsGiven() {
        String flowId = saveFlow();
        String sessionId = initialize();

        String body = callTool(sessionId, "read-flow", Map.of("tenant", TenantService.MAIN_TENANT, "namespace", "io.kestra.test", "flowId", flowId));

        assertThat(body).contains(flowId).doesNotContain("\"isError\":true");
    }

    @Test
    void shouldCallToolInCallerOnlyTenantWhenTenantArgumentIsOmitted() {
        String flowId = saveFlow();
        String sessionId = initialize();

        String body = callTool(sessionId, "read-flow", Map.of("namespace", "io.kestra.test", "flowId", flowId));

        assertThat(body).contains(flowId).doesNotContain("\"isError\":true");
    }

    @Test
    void shouldReturnErrorResultWhenTenantCannotBeReached() {
        String sessionId = initialize();

        String body = callTool(sessionId, "read-flow", Map.of("tenant", "another-tenant", "namespace", "io.kestra.test", "flowId", "any"));

        assertThat(body).contains("\"isError\":true", "You cannot act on tenant 'another-tenant' through this server.");
    }

    @Test
    void shouldReturnUnauthorizedWhenNoCredentialsAreSent() {
        TestAuthFilter.ENABLED = false;
        try {
            HttpClientResponseException e = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(mcpPost(INITIALIZE_REQUEST), Map.class)
            );

            assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
        } finally {
            TestAuthFilter.ENABLED = true;
        }
    }

    @Test
    void shouldCloseSessionWhenPlatformSessionIsDeleted() {
        String sessionId = initialize();

        HttpResponse<?> deleted = client.toBlocking().exchange(
            DELETE(PLATFORM_PATH)
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.MCP_SESSION_ID, sessionId)
        );

        assertThat(deleted.code()).isEqualTo(HttpStatus.OK.getCode());
        // The node drops its in-memory session only once the broadcast session-deleted event comes back.
        Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
        {
            HttpClientResponseException e = assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().retrieve(mcpPost(TOOLS_LIST_REQUEST, sessionId), String.class)
            );
            assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        });
    }

    private String callTool(String sessionId, String toolName, Map<String, Object> arguments) {
        McpSchema.JSONRPCRequest request = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest(toolName, arguments, null)
        );
        return client.toBlocking().retrieve(mcpPost(request, sessionId), String.class);
    }

    private String saveFlow() {
        String flowId = "flow-" + IdUtils.create().toLowerCase();
        flowRepository.create(
            GenericFlow.of(
                Flow.builder()
                    .id(flowId)
                    .namespace("io.kestra.test")
                    .tenantId(TenantService.MAIN_TENANT)
                    .tasks(List.of(Return.builder().id("task").type(Return.class.getName()).format(Property.ofValue("done")).build()))
                    .build()
            )
        );
        return flowId;
    }

    private String initialize() {
        HttpResponse<?> response = client.toBlocking().exchange(mcpPost(INITIALIZE_REQUEST), Map.class);
        String sessionId = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }

    private MutableHttpRequest<String> mcpPost(McpSchema.JSONRPCMessage message) {
        try {
            return POST(PLATFORM_PATH, MCP_MAPPER.writeValueAsString(message))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .contentType(MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }

    private MutableHttpRequest<String> mcpPost(McpSchema.JSONRPCMessage message, String sessionId) {
        return mcpPost(message).header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }
}
