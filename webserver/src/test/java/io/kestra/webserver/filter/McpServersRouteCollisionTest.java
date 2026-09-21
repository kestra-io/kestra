package io.kestra.webserver.filter;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.tenant.TenantService;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import jakarta.inject.Inject;
import tools.jackson.databind.json.JsonMapper;

import static io.micronaut.http.HttpRequest.GET;
import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guards against the MCP-management route-collision authentication bypass.
 * <p>
 * The management API used to live at {@code /api/v1/{tenant}/mcp/servers}, nested inside the MCP
 * tool namespace {@code /api/v1/{tenant}/mcp/{id}} that {@link McpServerAuthenticationFilter}
 * governs. A PUBLIC tool server whose id was {@code servers} therefore made the filter treat the
 * management route as an unauthenticated public MCP endpoint, letting it skip BasicAuth.
 * <p>
 * The management API now lives at {@code /api/v1/{tenant}/mcp-servers}, outside the MCP tool
 * namespace, so the two can no longer collide. These tests assert both halves of that contract:
 * the management API requires BasicAuth, and a genuine PUBLIC tool server named {@code servers}
 * is reachable on the tool endpoint (no longer shadowed). {@link TestAuthFilter} is disabled so
 * unauthenticated requests carry no credentials.
 */
@KestraTest
class McpServersRouteCollisionTest {

    private static final String MANAGEMENT_PATH = "/api/v1/main/mcp-servers";

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
    private ReactorHttpClient client;

    @Inject
    private McpServerRepositoryInterface mcpServerRepository;

    @BeforeEach
    void disableTestAuthFilter() {
        TestAuthFilter.ENABLED = false;
    }

    @AfterEach
    void restoreTestAuthFilter() {
        TestAuthFilter.ENABLED = true;
    }

    @Test
    void shouldReturnUnauthorizedWhenListingServersWithoutAuthGivenPublicServersServerExists() {
        // Given — a PUBLIC tool server named "servers" (the historical collision trigger)
        savePublicServer("servers");

        // When / Then — the management API must still require BasicAuth
        assertThatThrownBy(() -> client.toBlocking().exchange(GET(MANAGEMENT_PATH), String.class))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e ->
                assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode()));
    }

    @Test
    void shouldReturnUnauthorizedWhenCreatingServerWithoutAuthGivenPublicServersServerExists() {
        // Given
        savePublicServer("servers");
        McpServer body = new McpServer(
            TenantService.MAIN_TENANT, "servers2", "x", null,
            McpServer.ServerType.PUBLIC, McpServer.AuthType.BASIC, null, null,
            false, false, false, null, null
        );

        // When / Then
        assertThatThrownBy(() -> client.toBlocking().exchange(POST(MANAGEMENT_PATH, body), String.class))
            .isInstanceOfSatisfying(HttpClientResponseException.class, e ->
                assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode()));
    }

    @Test
    void shouldAllowUnauthenticatedToolAccessForPublicServerNamedServers() {
        // Given — a PUBLIC tool server whose id is "servers" is now a legitimate, reachable server
        savePublicServer("servers");

        // When — its MCP tool endpoint no longer collides with the management route
        HttpResponse<?> response = client.toBlocking().exchange(mcpToolPost("servers", INITIALIZE_REQUEST), String.class);

        // Then — the public tool endpoint responds without credentials (not shadowed, not 401)
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    }

    private void savePublicServer(String id) {
        mcpServerRepository.save(null, new McpServer(
            TenantService.MAIN_TENANT, id, "collision", null,
            McpServer.ServerType.PUBLIC, McpServer.AuthType.BASIC, null, null,
            false, false, false, null, null
        ));
    }

    private MutableHttpRequest<String> mcpToolPost(String serverId, McpSchema.JSONRPCMessage message) {
        try {
            return POST("/api/v1/main/mcp/" + serverId, MCP_MAPPER.writeValueAsString(message))
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .contentType(MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }
}
