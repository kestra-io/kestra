package io.kestra.webserver.filter;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.services.BasicAuthService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

import static io.micronaut.http.HttpRequest.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class McpServerAuthenticationFilterTest {

    private static final String MCP_PATH = "/api/v1/main/mcp";

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

    @Inject
    private BasicAuthService.BasicAuthConfiguration basicAuthConfiguration;

    @BeforeEach
    void disableTestAuthFilter() {
        TestAuthFilter.ENABLED = false;
    }

    @AfterEach
    void restoreTestAuthFilter() {
        TestAuthFilter.ENABLED = true;
    }

    @Test
    void shouldReturnUnauthorizedWhenNoCredentialsProvidedForPrivateServer() {
        // Given
        String serverId = saveServer(false, McpServer.ServerType.PRIVATE);

        // When / Then
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverId, INITIALIZE_REQUEST))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
        assertThat(e.getResponse().getHeaders().get("WWW-Authenticate")).isEqualTo("Basic");
    }

    @Test
    void shouldAllowThroughWhenValidCredentialsProvidedForPrivateServer() {
        // Given
        String serverId = saveServer(false, McpServer.ServerType.PRIVATE);

        // When — valid credentials clear the auth gate; MCP responds with the initialize result
        HttpResponse<?> response = client.toBlocking().exchange(
            mcpPost(serverId, INITIALIZE_REQUEST)
                .basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()),
            Map.class
        );

        // Then
        assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldReturnUnauthorizedWhenWrongCredentialsProvidedForPrivateServer() {
        // Given
        String serverId = saveServer(false, McpServer.ServerType.PRIVATE);

        // When / Then
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                mcpPost(serverId, INITIALIZE_REQUEST).basicAuth("wrong@user.com", "wrongPassword1")
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldAllowThroughWhenNoCredentialsProvidedForPublicServer() {
        // Given
        String serverId = saveServer(false, McpServer.ServerType.PUBLIC);

        // When
        HttpResponse<?> response = client.toBlocking().exchange(
            mcpPost(serverId, INITIALIZE_REQUEST), Map.class
        );

        // Then
        assertThat(response.code()).isEqualTo(HttpStatus.OK.getCode());
    }

    @Test
    void shouldReturnUnauthorizedWhenNoCredentialsProvidedForDisabledPrivateServer() {
        // Given
        String serverId = saveServer(true, McpServer.ServerType.PRIVATE);

        // When / Then
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverId, INITIALIZE_REQUEST))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldReturnServiceUnavailableWhenValidCredentialsProvidedForDisabledPrivateServer() {
        // Given — auth passes but the controller rejects because the server is disabled
        String serverId = saveServer(true, McpServer.ServerType.PRIVATE);

        // When / Then
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                mcpPost(serverId, INITIALIZE_REQUEST)
                    .basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword())
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void shouldReturnServiceUnavailableForDisabledPublicServer() {
        // Given
        String serverId = saveServer(true, McpServer.ServerType.PUBLIC);

        // When / Then
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(mcpPost(serverId, INITIALIZE_REQUEST))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void shouldReturnNotFoundWhenServerDoesNotExist() {
        // Given
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(
                mcpPost(IdUtils.create(), INITIALIZE_REQUEST)
                    .basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword())
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldRequireAuthAfterCacheUpdateWhenServerTypeChangedFromPublicToPrivate() throws InterruptedException {
        // Given
        String serverId = saveServer(false, McpServer.ServerType.PUBLIC);
        client.toBlocking().exchange(mcpPost(serverId, INITIALIZE_REQUEST), Map.class);

        // When
        McpServer existing = mcpServerRepository.get(TenantService.MAIN_TENANT, serverId).orElseThrow();
        McpServer updated = new McpServer(existing.tenantId(), existing.id(), existing.description(),
            existing.instructions(), McpServer.ServerType.PRIVATE, existing.authType(),
            existing.oauthProvider(), existing.oauthScopesSupported(), existing.disabled(), existing.isDefault(), existing.deleted(), existing.created(), existing.updated());
        mcpServerRepository.save(existing, updated);

        // Then
        pollUntilStatus(mcpPost(serverId, INITIALIZE_REQUEST), HttpStatus.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldPassThroughAfterCacheInvalidationWhenPrivateServerIsDeleted() throws InterruptedException {
        // Given
        String serverId = saveServer(false, McpServer.ServerType.PRIVATE);
        client.toBlocking().exchange(
            mcpPost(serverId, INITIALIZE_REQUEST)
                .basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()),
            Map.class
        );

        // When
        mcpServerRepository.delete(TenantService.MAIN_TENANT, serverId);

        // Then
        pollUntilStatus(
            mcpPost(serverId, INITIALIZE_REQUEST)
                .basicAuth(basicAuthConfiguration.getUsername(), basicAuthConfiguration.getPassword()),
            HttpStatus.NOT_FOUND.getCode()
        );
    }

    @Test
    void shouldReturnServiceUnavailableAfterCacheUpdateWhenPublicServerIsDisabled() throws InterruptedException {
        // Given
        String serverId = saveServer(false, McpServer.ServerType.PUBLIC);
        client.toBlocking().exchange(mcpPost(serverId, INITIALIZE_REQUEST), Map.class);

        // When
        McpServer existing = mcpServerRepository.get(TenantService.MAIN_TENANT, serverId).orElseThrow();
        McpServer disabled = new McpServer(existing.tenantId(), existing.id(), existing.description(),
            existing.instructions(), existing.serverType(), existing.authType(),
            existing.oauthProvider(), existing.oauthScopesSupported(), true, existing.isDefault(), existing.deleted(), existing.created(), existing.updated());
        mcpServerRepository.save(existing, disabled);

        // Then
        pollUntilStatus(mcpPost(serverId, INITIALIZE_REQUEST), HttpStatus.SERVICE_UNAVAILABLE.getCode());
    }

    private void pollUntilStatus(MutableHttpRequest<String> request, int expectedStatus) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        int actual = -1;
        while (System.currentTimeMillis() < deadline) {
            try {
                actual = client.toBlocking().exchange(request, Map.class).code();
            } catch (HttpClientResponseException e) {
                actual = e.getStatus().getCode();
            }
            if (actual == expectedStatus) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(actual).as("cache-invalidation did not propagate within 5s").isEqualTo(expectedStatus);
    }

    private String saveServer(boolean disabled, McpServer.ServerType serverType) {
        String name = "server-" + IdUtils.create();
        McpServer mcpServer = new McpServer(
            TenantService.MAIN_TENANT, name, "A test MCP server",
            null, serverType, McpServer.AuthType.BASIC, null, null, disabled, false, false, null, null
        );
        return mcpServerRepository.save(null, mcpServer).id();
    }

    private MutableHttpRequest<String> mcpPost(String serverId, McpSchema.JSONRPCMessage message) {
        try {
            String body = MCP_MAPPER.writeValueAsString(message);
            return POST(MCP_PATH + "/" + serverId , body)
                .accept(MediaType.TEXT_EVENT_STREAM_TYPE, MediaType.APPLICATION_JSON_TYPE)
                .contentType(MediaType.APPLICATION_JSON);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MCP message", e);
        }
    }
}
