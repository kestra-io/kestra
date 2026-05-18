package io.kestra.webserver.controllers.api;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.micronaut.http.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.reactor.http.client.ReactorSseClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
@io.micronaut.context.annotation.Property(name = "kestra.server-type", value = "WEBSERVER")
class McpToolControllerTest {

    private static final String MCP_TOOL_PATH = "/api/v1/main/mcp";
    private static final String TEST_NAMESPACE = "io.kestra.test";

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

    private static final McpSchema.JSONRPCNotification INITIALIZED_NOTIFICATION = new McpSchema.JSONRPCNotification(
        McpSchema.JSONRPC_VERSION,
        McpSchema.METHOD_NOTIFICATION_INITIALIZED,
        Map.of()
    );

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    @Client("/")
    ReactorSseClient sseClient;

    @Inject
    McpServerRepositoryInterface mcpServerRepository;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    BroadcastQueueInterface<FlowInterface> flowQueue;

    @Test
    void shouldReturnNotFoundWhenConnectingToUnknownServer() {
        // Given
        String nonExistentName = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(nonExistentName, ""))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldReturnServiceUnavailableWhenConnectingToDisabledServer() {
        // Given
        String serverId = saveServer(true, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverId, ""))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void shouldReturnBadRequestWhenPostingWithoutAcceptHeaders() {
        // Given
        String serverId = saveServer(false, null);

        // When / Then — MCP transport requires Accept: text/event-stream, application/json
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                POST(serverUrl(serverId), INITIALIZE_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void shouldReturnBadRequestWhenPostingWithEmptyBody() {
        // Given
        String serverId = saveServer(false, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverId, ""))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void shouldReturnBadRequestWhenPostingWithMalformedJson() {
        // Given
        String serverId = saveServer(false, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverId, "{not-valid-json}"))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void shouldReturnBadRequestWhenNonInitializePostWithoutSessionId() {
        // Given
        String serverId = saveServer(false, null);

        // When / Then — tools/list is not initialize so a session ID is required
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverId, TOOLS_LIST_REQUEST))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void shouldReturnBadRequestWhenGettingWithoutSessionId() {
        // Given
        String serverId = saveServer(false, null);

        // When / Then — GET (SSE stream) requires an existing Mcp-Session-Id
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                GET(serverUrl(serverId))
                    .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    void shouldReturnBadRequestWhenDeletingWithoutSessionId() {
        // Given
        String serverId = saveServer(false, null);

        // When / Then — DELETE requires Mcp-Session-Id to identify the session to close
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                DELETE(serverUrl(serverId))
                    .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateSessionWithServerInfoWhenInitializingServerWithInstructions() {
        // Given
        String instructions = "You are a helpful Kestra assistant.";
        String serverId = saveServer(false, instructions);

        // When
        HttpResponse<Map> response = client.toBlocking().exchange(
            mcpPost(serverId, INITIALIZE_REQUEST), Map.class
        );

        // Then — session is created and its ID is returned in the response header
        assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getHeaders().get(HttpHeaders.MCP_SESSION_ID)).isNotBlank();

        // Then — server metadata from the Mcp record is reflected in the protocol response
        Map<String, Object> result = (Map<String, Object>) response.body().get("result");
        assertThat(result.get("instructions")).isEqualTo(instructions);
    }

    @Test
    void shouldAcceptInitializedNotificationWhenSessionIsInitialized() {
        // Given
        String serverId = saveServer(false, null);
        String sessionId = initialize(serverId);

        // When
        HttpResponse<?> response = client.toBlocking().exchange(
            mcpPost(serverId, INITIALIZED_NOTIFICATION, sessionId)
        );

        // Then
        assertThat(response.code()).isEqualTo(HttpStatus.ACCEPTED.getCode());
    }

    @Test
    void shouldReturnEmptyToolsListViaSseWhenToolsListRequested() {
        // Given
        String serverId = saveServer(false, null);
        String sessionId = initialize(serverId);

        // When — tools/list returns an SSE response containing the JSON-RPC result
        String body = client.toBlocking().retrieve(
            mcpPost(serverId, TOOLS_LIST_REQUEST, sessionId),
            String.class
        );

        // Then — the SSE body contains an empty tools array (no flows with McpToolTrigger in tests)
        assertThat(body).contains("\"tools\"");
    }

    @Test
    void shouldReturnOkWhenDeletingInitializedSession() {
        // Given
        String serverId = saveServer(false, null);
        String sessionId = initialize(serverId);

        // When
        HttpResponse<?> response = client.toBlocking().exchange(
            DELETE(serverUrl(serverId))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.MCP_SESSION_ID, sessionId)
        );

        // Then
        assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
    }

    // -------------------------------------------------------------------------
    // MCP tools — listing and tool-level protocol validation
    // -------------------------------------------------------------------------

    @Test
    void shouldIncludeToolInListWhenFlowHasMcpTrigger() {
        // Given — flow must exist before session init so the server picks it up when built
        String serverId = saveServer(false, null);
        String toolName = saveFlowWithTool(serverId);
        String sessionId = initialize(serverId);

        // When
        String body = client.toBlocking().retrieve(
            mcpPost(serverId, TOOLS_LIST_REQUEST, sessionId),
            String.class
        );

        // Then — the SSE body lists the tool registered on this server
        assertThat(body).contains(toolName);
    }

    @Test
    void shouldReturnMcpErrorViaSseWhenToolCallSentForUnknownTool() {
        // Given — server with no registered tools
        String serverId = saveServer(false, null);
        String sessionId = initialize(serverId);

        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest("non-existent-tool", Map.of(), null)
        );

        // When — tool name has no matching handler; MCP returns a JSON-RPC error immediately
        String body = client.toBlocking().retrieve(
            mcpPost(serverId, callRequest, sessionId),
            String.class
        );

        // Then — response contains a JSON-RPC error (not a successful result)
        assertThat(body).contains("\"error\"");
        assertThat(body).doesNotContain("\"result\"");
    }

    @Test
    void shouldReceiveToolListChangeNotificationWhenFlowWithMcpToolIsDeleted() throws Exception {
        // Given
        String serverId = saveServer(false, null);
        String flowId = IdUtils.create();
        String toolName = "tool-" + IdUtils.create().toLowerCase();
        saveFlowWithTool(serverId, flowId, toolName);
        String sessionId = initialize(serverId);

        CountDownLatch notificationReceived = new CountDownLatch(1);
        Disposable subscription = sseClient.eventStream(
            GET(serverUrl(serverId))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
                .header(HttpHeaders.MCP_SESSION_ID, sessionId),
            String.class
        ).subscribe(
            event -> {
                if (event.getData() != null &&
                    event.getData().contains(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED)) {
                    notificationReceived.countDown();
                }
            },
            error -> {},
            () -> {}
        );
        Thread.sleep(200);

        // When
        client.toBlocking().exchange(
            DELETE("/api/v1/main/flows/" + TEST_NAMESPACE + "/" + flowId)
        );

        // Then
        assertThat(notificationReceived.await(5, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();
    }

    @Test
    void shouldReceiveToolListChangeNotificationWhenAssociatedFlowIsUpdated() throws Exception {
        // Given
        String serverId = saveServer(false, null);
        String toolName = saveFlowWithTool(serverId);
        String sessionId = initialize(serverId);

        CountDownLatch notificationReceived = new CountDownLatch(1);
        Disposable subscription = sseClient.eventStream(
            GET(serverUrl(serverId))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
                .header(HttpHeaders.MCP_SESSION_ID, sessionId),
            String.class
        ).subscribe(
            event -> {
                if (event.getData() != null &&
                    event.getData().contains(McpSchema.METHOD_NOTIFICATION_TOOLS_LIST_CHANGED)) {
                    notificationReceived.countDown();
                }
            },
            error -> {},
            () -> {}
        );
        Thread.sleep(200);

        // When
        flowQueue.emit(buildGenericFlowForQueue(serverId, toolName));

        // Then
        assertThat(notificationReceived.await(5, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();
    }

    @Test
    void shouldCloseConnectionWhenMcpServerIsDeleted() throws Exception {
        // Given
        String serverId = saveServer(false, null);
        String sessionId = initialize(serverId);

        CountDownLatch connectionClosed = new CountDownLatch(1);
        Disposable subscription = sseClient.eventStream(
            GET(serverUrl(serverId))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
                .header(HttpHeaders.MCP_SESSION_ID, sessionId),
            String.class
        ).subscribe(
            event -> {},
            error -> connectionClosed.countDown(),
            connectionClosed::countDown
        );
        Thread.sleep(500);

        // When
        client.toBlocking().exchange(DELETE("/api/v1/main/mcp/servers/" + serverId));

        // Then
        assertThat(connectionClosed.await(5, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();
    }

    @Test
    void shouldCloseConnectionWhenMcpServerIsDisabled() throws Exception {
        // Given
        String serverId = saveServer(false, null);
        String sessionId = initialize(serverId);

        CountDownLatch connectionClosed = new CountDownLatch(1);
        AtomicBoolean closedGracefully = new AtomicBoolean(false);
        Disposable subscription = sseClient.eventStream(
            GET(serverUrl(serverId))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE)
                .header(HttpHeaders.MCP_SESSION_ID, sessionId),
            String.class
        ).subscribe(
            event -> {},
            error -> connectionClosed.countDown(),
            () -> {
                closedGracefully.set(true);
                connectionClosed.countDown();
            }
        );
        Thread.sleep(500);

        // When
        client.toBlocking().exchange(
            PATCH("/api/v1/main/mcp/servers/" + serverId + "/toggle", "")
                .contentType(MediaType.APPLICATION_JSON)
        );

        // Then
        assertThat(connectionClosed.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(closedGracefully.get()).isTrue();
        subscription.dispose();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Saves an MCP server record directly to the repository and returns its id. */
    private String saveServer(boolean disabled, String instructions) {
        String serverId = "server-" + IdUtils.create();
        McpServer mcpServer = new McpServer(TenantService.MAIN_TENANT, serverId,
            "A test MCP server", instructions, null, null, disabled, false, false, null, null);
        return mcpServerRepository.save(null, mcpServer).id();
    }

    /** Full URL for the MCP Streamable HTTP transport endpoint. */
    private String serverUrl(String serverId) {
        return MCP_TOOL_PATH + "/" + serverId ;
    }

    /**
     * Builds a POST request with the Accept headers required by the MCP Streamable HTTP transport.
     * Both {@code text/event-stream} and {@code application/json} must be present.
     * Use the {@link String} overload only for guard tests that send an intentionally invalid body.
     */
    private MutableHttpRequest<String> mcpPost(String serverId, String body) {
        return POST(serverUrl(serverId), body)
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .contentType(MediaType.APPLICATION_JSON);
    }

    /** Serializes an MCP message and builds a POST request with the required Accept headers. */
    private MutableHttpRequest<String> mcpPost(String serverId, McpSchema.JSONRPCMessage message) {
        try {
            return mcpPost(serverId, MCP_MAPPER.writeValueAsString(message));
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }

    /** Builds a POST request with an {@code Mcp-Session-Id} header for post-initialize calls. */
    private MutableHttpRequest<String> mcpPost(String serverId, McpSchema.JSONRPCMessage message, String sessionId) {
        return mcpPost(serverId, message)
            .header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }

    /** Sends an MCP initialize request and returns the session ID from the response header. */
    private String initialize(String serverId) {
        HttpResponse<?> response = client.toBlocking().exchange(
            mcpPost(serverId, INITIALIZE_REQUEST), Map.class
        );
        String sessionId = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }

    private FlowInterface buildGenericFlowForQueue(String serverId, String toolName) {
        McpToolTrigger trigger = McpToolTrigger.builder()
            .id("mcp-trigger")
            .type(McpToolTrigger.class.getName())
            .toolName(toolName)
            .title("Test Tool")
            .toolDescription("A test MCP tool")
            .mcpServer(serverId)
            .build();
        return GenericFlow.of(
            Flow.builder()
                .id(IdUtils.create())
                .namespace(TEST_NAMESPACE)
                .tenantId(TenantService.MAIN_TENANT)
                .tasks(List.of(
                    Return.builder()
                        .id("task")
                        .type(Return.class.getName())
                        .format(Property.ofValue("done"))
                        .build()
                ))
                .triggers(List.of(trigger))
                .build()
        );
    }

    private String saveFlowWithTool(String serverId) {
        String toolName = "tool-" + IdUtils.create().toLowerCase();
        saveFlowWithTool(serverId, IdUtils.create(), toolName);
        return toolName;
    }

    private void saveFlowWithTool(String serverId, String flowId, String toolName) {
        McpToolTrigger trigger = McpToolTrigger.builder()
            .id("mcp-trigger")
            .type(McpToolTrigger.class.getName())
            .toolName(toolName)
            .title("Test Tool")
            .toolDescription("A test MCP tool")
            .mcpServer(serverId)
            .build();

        flowRepository.create(GenericFlow.of(
            Flow.builder()
                .id(flowId)
                .namespace(TEST_NAMESPACE)
                .tenantId(TenantService.MAIN_TENANT)
                .tasks(List.of(
                    Return.builder()
                        .id("task")
                        .type(Return.class.getName())
                        .format(Property.ofValue("done"))
                        .build()
                ))
                .triggers(List.of(trigger))
                .build()
        ));
    }
}
