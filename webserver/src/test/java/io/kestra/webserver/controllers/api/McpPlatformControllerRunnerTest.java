package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;

import io.kestra.core.events.Actor;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.webserver.services.ActorResolver;
import io.kestra.webserver.services.DefaultActorResolver;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.test.annotation.MockBean;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import tools.jackson.databind.json.JsonMapper;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@KestraTest(startRunner = true)
@Property(name = "kestra.mcp.platform-server.enabled", value = "true")
@Property(name = "kestra.ai.agent.docs-mcp-url", value = "http://localhost:1/mcp")
class McpPlatformControllerRunnerTest {
    private static final String PLATFORM_PATH = "/api/v1/mcp";
    private static final Actor ACTOR = new Actor("actor-id", "10.0.0.1", null);
    private static final McpJsonMapper MCP_MAPPER = new JacksonMcpJsonMapper(JsonMapper.builder().build());

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    TestRunnerUtils runnerUtils;

    @MockBean(DefaultActorResolver.class)
    ActorResolver actorResolver() {
        return () -> ACTOR;
    }

    @Test
    @LoadFlows({ "flows/valids/restart_last_failed.yaml" })
    void shouldAttributeRestartToCallerWhenRestartedThroughPlatformServer() throws Exception {
        // Given
        Execution failed = runnerUtils.runOne(
            MAIN_TENANT, "io.kestra.tests", "restart_last_failed", null, (BiFunction<FlowInterface, Execution, Map<String, Object>>) null
        );
        assertThat(failed.getState().getCurrent()).isEqualTo(State.Type.FAILED);
        String sessionId = initialize();

        // When
        String body = client.toBlocking().retrieve(
            mcpPost(
                new McpSchema.JSONRPCRequest(
                    McpSchema.JSONRPC_VERSION,
                    McpSchema.METHOD_TOOLS_CALL,
                    2,
                    new McpSchema.CallToolRequest("restart_execution", Map.of("executionId", failed.getId()), null)
                )
            ).header(HttpHeaders.MCP_SESSION_ID, sessionId),
            String.class
        );

        // Then
        assertThat(body).doesNotContain("\"isError\":true");
        Actor restartedBy = await().atMost(Duration.ofSeconds(10)).until(
            () -> ExecutionControllerRunnerTest.ExecutionCrudEventListener.events().stream()
                .filter(
                    event -> CrudEventType.CREATE.equals(event.getType())
                        && failed.getId().equals(event.getModel().getId())
                        && event.getModel().getLabels().contains(new Label(Label.RESTARTED, "true"))
                )
                .findFirst(),
            Optional::isPresent
        ).orElseThrow().getActor();
        assertThat(restartedBy).isEqualTo(ACTOR);
    }

    private String initialize() {
        McpSchema.JSONRPCRequest initialize = new McpSchema.JSONRPCRequest(
            McpSchema.JSONRPC_VERSION,
            McpSchema.METHOD_INITIALIZE,
            1,
            new McpSchema.InitializeRequest(
                ProtocolVersions.MCP_2025_03_26,
                new McpSchema.ClientCapabilities(null, null, null, null),
                new McpSchema.Implementation("test", "1.0.0")
            )
        );
        HttpResponse<?> response = client.toBlocking().exchange(mcpPost(initialize), Map.class);
        return response.getHeaders().get(HttpHeaders.MCP_SESSION_ID);
    }

    private static MutableHttpRequest<String> mcpPost(McpSchema.JSONRPCMessage message) {
        try {
            return POST(PLATFORM_PATH, MCP_MAPPER.writeValueAsString(message))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .contentType(MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }
}
