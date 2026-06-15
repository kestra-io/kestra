package io.kestra.webserver.controllers.api;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.models.property.Property;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.CountDownLatchTask;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.plugin.core.execution.Fail;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Integration tests for MCP tool invocation that require a running executor.
 * <p>
 * The {@code execution-timeout} is set to {@code PT5S} so that:
 * <ul>
 *   <li>Success tests complete well within the window (~1-2 s with queue overhead).</li>
 *   <li>The timeout test finishes exactly at 5 s instead of the default 5-minute wait.</li>
 * </ul>
 */
@KestraTest(startRunner = true)
@io.micronaut.context.annotation.Property(name = "kestra.mcp.tool-execution-timeout", value = "PT5S")
class McpToolControllerRunnerTest {

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

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    McpServerRepositoryInterface mcpServerRepository;

    @Inject
    FlowRepositoryInterface flowRepository;

    @Inject
    ExecutionRepositoryInterface executionRepository;

    // -------------------------------------------------------------------------
    // Tool invocation — requires a running executor to process executions
    // -------------------------------------------------------------------------

    @Test
    void shouldReturnSuccessResultViaSseWhenToolCallWithValidInput() throws InterruptedException {
        // Given
        String serverId = saveServer(false, null);
        CountDownLatch completionLatch = new CountDownLatch(1);
        CountDownLatch continueLatch = new CountDownLatch(1);
        String toolName = saveFlowWithCountDownLatch(serverId, completionLatch, continueLatch);
        String sessionId = initialize(serverId);

        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest(toolName, Map.of(), null)
        );

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(continueLatch::countDown, 1, TimeUnit.SECONDS);
        try {
            // When
            String body = client.toBlocking().retrieve(
                mcpPost(serverId, callRequest, sessionId),
                String.class
            );

            // Then
            assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(body).contains("\"result\"");
            assertThat(body).contains("\"isError\":false");
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void shouldReturnErrorResultViaSseWhenToolCallWithMissingRequiredInput() {
        // Given
        String serverName = saveServer(false, null);
        String toolName = saveFlowWithToolAndConditionalFail(serverName);
        String sessionId = initialize(serverName);

        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest(toolName, Map.of(), null)  // "message" arg omitted
        );

        // When
        String body = client.toBlocking().retrieve(
            mcpPost(serverName, callRequest, sessionId),
            String.class
        );

        // Then
        assertThat(body).contains("\"result\"");
        assertThat(body).contains("\"isError\":true");
    }

    @Test
    void shouldReturnTimeoutErrorViaSseWhenToolCallExecutionTimesOut() {
        // Given
        String serverId = saveServer(false, null);
        CountDownLatch neverReleasedLatch = new CountDownLatch(1);  // intentionally never counted down
        String toolName = saveFlowWithBlockingCountDownLatch(serverId, neverReleasedLatch);
        String sessionId = initialize(serverId);

        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest(toolName, Map.of(), null)
        );

        // When
        String body = client.toBlocking().retrieve(
            mcpPost(serverId, callRequest, sessionId),
            String.class
        );

        // Then
        assertThat(body).contains("\"result\"");
        assertThat(body).contains("\"isError\":true");
        assertThat(body).contains("Failed to execute flow");
    }

    @Test
    void shouldSetMcpServerAndSessionLabelsWhenExecutionCreated() throws InterruptedException {
        // Given
        String serverId = saveServer(false, null);
        String flowId = IdUtils.create();
        McpToolTrigger trigger = McpToolTrigger.builder()
            .id("mcp-trigger")
            .type(McpToolTrigger.class.getName())
            .toolName("tool-" + flowId.toLowerCase())
            .title("Label Test Tool")
            .toolDescription("Verifies MCP labels are stamped on the execution")
            .mcpServer(serverId)
            .build();

        CountDownLatch completionLatch = new CountDownLatch(1);
        CountDownLatch continueLatch = new CountDownLatch(1);
        CountDownLatchTask task = CountDownLatchTask.getTaskForCountDownLatch(completionLatch, continueLatch, Duration.ofSeconds(10));
        flowRepository.create(GenericFlow.of(
            Flow.builder()
                .id(flowId)
                .namespace(TEST_NAMESPACE)
                .tenantId(TenantService.MAIN_TENANT)
                .tasks(List.of(task))
                .triggers(List.of(trigger))
                .build()
        ));

        String sessionId = initialize(serverId);
        McpSchema.JSONRPCRequest callRequest = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_TOOLS_CALL,
            3,
            new McpSchema.CallToolRequest(trigger.getToolName(), Map.of(), null)
        );

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.schedule(continueLatch::countDown, 1, TimeUnit.SECONDS);
        try {
            // When
            client.toBlocking().retrieve(mcpPost(serverId, callRequest, sessionId), String.class);
            assertThat(completionLatch.await(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            scheduler.shutdown();
        }

        // Then
        ArrayListTotal<Execution> executions = executionRepository.findByFlowId(TenantService.MAIN_TENANT, TEST_NAMESPACE, flowId, Pageable.unpaged());
        assertThat(executions).hasSize(1);
        assertThat(executions.getFirst().getLabels())
            .extracting(Label::key, Label::value)
            .contains(
                tuple(Label.MCP_SERVER_ID, serverId),
                tuple(Label.MCP_SESSION_ID, sessionId)
            );
    }

    private String saveServer(boolean disabled, String instructions) {
        String serverName = "server-" + IdUtils.create();
        McpServer mcpServer = new McpServer(TenantService.MAIN_TENANT, serverName, null,
            instructions, null, null, null, null, disabled, false, false, null, null);
        return mcpServerRepository.save(null, mcpServer).id();
    }

    private String serverUrl(String serverName) {
        return MCP_TOOL_PATH + "/" + serverName ;
    }

    private MutableHttpRequest<String> mcpPost(String serverName, String body) {
        return POST(serverUrl(serverName), body)
            .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
            .contentType(MediaType.APPLICATION_JSON);
    }

    private MutableHttpRequest<String> mcpPost(String serverName, McpSchema.JSONRPCMessage message) {
        try {
            return mcpPost(serverName, MCP_MAPPER.writeValueAsString(message));
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }

    private MutableHttpRequest<String> mcpPost(String serverName, McpSchema.JSONRPCMessage message, String sessionId) {
        return mcpPost(serverName, message).header(HttpHeaders.MCP_SESSION_ID, sessionId);
    }

    private String initialize(String serverId) {
        HttpResponse<?> response = client.toBlocking().exchange(
            mcpPost(serverId, INITIALIZE_REQUEST), Map.class
        );
        String sessionId = response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
        assertThat(sessionId).isNotBlank();
        return sessionId;
    }

    private String saveFlowWithCountDownLatch(String serverId, CountDownLatch completionLatch, CountDownLatch continueLatch) {
        String toolName = "tool-" + IdUtils.create().toLowerCase();

        McpToolTrigger trigger = McpToolTrigger.builder()
            .id("mcp-trigger")
            .type(McpToolTrigger.class.getName())
            .toolName(toolName)
            .title("Test Tool")
            .toolDescription("A test MCP tool")
            .mcpServer(serverId)
            .build();

        CountDownLatchTask task = CountDownLatchTask.getTaskForCountDownLatch(
            completionLatch, continueLatch, Duration.ofSeconds(10)
        );

        flowRepository.create(GenericFlow.of(
            Flow.builder()
                .id(IdUtils.create())
                .namespace(TEST_NAMESPACE)
                .tenantId(TenantService.MAIN_TENANT)
                .tasks(List.of(task))
                .triggers(List.of(trigger))
                .build()
        ));
        return toolName;
    }

    private String saveFlowWithBlockingCountDownLatch(String serverName, CountDownLatch neverReleasedLatch) {
        String toolName = "tool-" + IdUtils.create().toLowerCase();

        McpToolTrigger trigger = McpToolTrigger.builder()
            .id("mcp-trigger")
            .type(McpToolTrigger.class.getName())
            .toolName(toolName)
            .title("Test Tool")
            .toolDescription("A test MCP tool that blocks until the MCP execution timeout fires")
            .mcpServer(serverName)
            .build();

        CountDownLatchTask task = CountDownLatchTask.getTaskForCountDownLatch(
            new CountDownLatch(1),  // completion latch — never reached; await blocks first
            neverReleasedLatch,     // await latch — never counts down within the test window
            Duration.ofSeconds(60)  // longer than PT5S MCP timeout so the MCP fires first
        );

        flowRepository.create(GenericFlow.of(
            Flow.builder()
                .id(IdUtils.create())
                .namespace(TEST_NAMESPACE)
                .tenantId(TenantService.MAIN_TENANT)
                .tasks(List.of(task))
                .triggers(List.of(trigger))
                .build()
        ));
        return toolName;
    }

    private String saveFlowWithToolAndConditionalFail(String serverName) {
        String toolName = "tool-" + IdUtils.create().toLowerCase();

        McpToolTrigger trigger = McpToolTrigger.builder()
            .id("mcp-trigger")
            .type(McpToolTrigger.class.getName())
            .toolName(toolName)
            .title("Test Tool")
            .toolDescription("A test MCP tool that requires a 'message' input")
            .mcpServer(serverName)
            .build();

        StringInput messageInput = StringInput.builder()
            .id("message")
            .type(Type.STRING)
            .required(true)
            .build();

        // Fails only when inputs.message is absent/empty
        Fail conditionalFail = Fail.builder()
            .id("fail-if-no-input")
            .type(Fail.class.getName())
            .condition(Property.ofExpression("{{ inputs.message is empty }}"))
            .build();

        flowRepository.create(GenericFlow.of(
            Flow.builder()
                .id(IdUtils.create())
                .namespace(TEST_NAMESPACE)
                .tenantId(TenantService.MAIN_TENANT)
                .inputs(List.of(messageInput))
                .tasks(List.of(conditionalFail))
                .triggers(List.of(trigger))
                .build()
        ));
        return toolName;
    }
}
