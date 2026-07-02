package io.kestra.webserver.controllers.api;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.services.McpServerService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.models.api.ApiMcpServer;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.*;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class McpServerControllerTest {

    private static final String MCP_PATH = "/api/v1/main/mcp/servers";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    McpServerService mcpService;

    @Test
    void shouldCreateMcpWhenValidMcpProvided() {
        // Given
        ApiMcpServer mcp = buildMcp(IdUtils.create());

        // When
        ApiMcpServer created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(mcp.id());
        assertThat(created.disabled()).isFalse();
        assertThat(created.created()).isNotNull();
    }

    @Test
    void shouldReturnConflictWhenCreatingMcpWithDuplicateId() {
        // Given
        ApiMcpServer mcp = buildMcp(IdUtils.create());
        client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);

        // When / Then — same id → conflict
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
    }

    @Test
    void shouldReturnValidationErrorWhenCreatingMcpWithMissingRequiredFields() {
        // Given — null id violates @NotBlank/@NotNull
        ApiMcpServer mcp = new ApiMcpServer(null, null, null, null, null, null, null, true, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void shouldReturnMcpWhenGettingExistingMcp() {
        // Given
        ApiMcpServer mcp = buildMcp(IdUtils.create());
        ApiMcpServer created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);

        // When
        ApiMcpServer retrieved = client.toBlocking().retrieve(GET(MCP_PATH + "/" + created.id()), ApiMcpServer.class);

        // Then
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo(mcp.id());
    }

    @Test
    void shouldReturnNotFoundWhenGettingNonExistingMcp() {
        // Given
        String nonExistentId = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(GET(MCP_PATH + "/" + nonExistentId), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnPagedResultsWhenListingMcps() {
        // Given
        ApiMcpServer mcpOne = buildMcp(IdUtils.create());
        ApiMcpServer mcpTwo = buildMcp(IdUtils.create());
        ApiMcpServer createdOne = client.toBlocking().retrieve(POST(MCP_PATH, mcpOne), ApiMcpServer.class);
        ApiMcpServer createdTwo = client.toBlocking().retrieve(POST(MCP_PATH, mcpTwo), ApiMcpServer.class);

        // When
        PagedResults<ApiMcpServer> results = client.toBlocking().retrieve(
            GET(MCP_PATH + "?page=1&size=100"),
            Argument.of(PagedResults.class, ApiMcpServer.class)
        );

        // Then
        assertThat(results).isNotNull();
        assertThat(results.getTotal()).isGreaterThanOrEqualTo(2);
        List<String> ids = results.getResults().stream().map(ApiMcpServer::id).toList();
        assertThat(ids).contains(createdOne.id(), createdTwo.id());
    }

    @Test
    void shouldUpdateMcpWhenUpdatingExistingMcp() {
        // Given
        ApiMcpServer mcp = buildMcp(IdUtils.create());
        ApiMcpServer created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);
        ApiMcpServer update = new ApiMcpServer(created.id(), created.description(),
            null, null, null, null, null, true, false, null, null);

        // When
        ApiMcpServer result = client.toBlocking().retrieve(PUT(MCP_PATH + "/" + created.id(), update), ApiMcpServer.class);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(created.id());
        assertThat(result.disabled()).isTrue();
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingNonExistingMcp() {
        // Given
        String nonExistentId = IdUtils.create();
        ApiMcpServer mcp = buildMcp(IdUtils.create());

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(PUT(MCP_PATH + "/" + nonExistentId, mcp), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldReturnNoContentWhenDeletingExistingMcp() {
        // Given
        ApiMcpServer mcp = buildMcp(IdUtils.create());
        ApiMcpServer created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);

        // When
        HttpResponse<Void> response = client.toBlocking().exchange(DELETE(MCP_PATH + "/" + created.id()));

        // Then
        assertThat(response.code()).isEqualTo(HttpStatus.NO_CONTENT.getCode());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingNonExistingMcp() {
        // Given
        String nonExistentId = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(DELETE(MCP_PATH + "/" + nonExistentId))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldReturnValidationErrorWhenCreatingMcpWithReservedId() {
        // Given — "default" is a reserved id
        ApiMcpServer mcp = new ApiMcpServer(McpServer.DEFAULT_ID,
            "A description", null, null, null, null, null, true, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void shouldReturnValidationErrorWhenUpdatingMcpWithReservedId() {
        // Given
        ApiMcpServer mcp = buildMcp(IdUtils.create());
        ApiMcpServer created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);
        ApiMcpServer renamed = new ApiMcpServer(McpServer.DEFAULT_ID,
            created.description(), null, null, null, null, null, true, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(PUT(MCP_PATH + "/" + created.id(), renamed), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    void shouldReturnForbiddenWhenDeletingDefaultMcp() {
        // Given — provision via the service; the API blocks creating "default" directly
        mcpService.createDefaultMcpServerIfNotExist(TenantService.MAIN_TENANT);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(DELETE(MCP_PATH + "/" + McpServer.DEFAULT_ID))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    @Test
    void shouldFlipEnabledStateWhenTogglingExistingMcp() {
        // Given
        ApiMcpServer mcp = buildMcp(IdUtils.create());
        ApiMcpServer created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);
        assertThat(created.disabled()).isFalse();

        // When
        ApiMcpServer toggled = client.toBlocking().retrieve(
            PATCH(MCP_PATH + "/" + created.id() + "/toggle", ""), ApiMcpServer.class);

        // Then
        assertThat(toggled.disabled()).isTrue();
    }

    @Test
    void shouldReturnNotFoundWhenTogglingNonExistingMcp() {
        // Given
        String nonExistentId = IdUtils.create();

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(PATCH(MCP_PATH + "/" + nonExistentId + "/toggle", ""), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void shouldReturnForbiddenWhenCreatingMcpWithEnterpriseAuthType() {
        // Given — API_TOKEN requires EE; OSS edition is active in tests
        ApiMcpServer mcp = new ApiMcpServer("test-mcp-" + IdUtils.create().toLowerCase(),
            "A description", null, null, McpServer.AuthType.API_TOKEN, null, null, true, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    @Test
    void shouldCreateMcpWhenUsingBasicAuthType() {
        // Given — BASIC is the only auth type permitted in OSS
        ApiMcpServer mcp = new ApiMcpServer("test-mcp-" + IdUtils.create().toLowerCase(),
            "A description", null, null, McpServer.AuthType.BASIC, null, null, true, false, null, null);

        // When
        ApiMcpServer created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.authType()).isEqualTo(McpServer.AuthType.BASIC);
    }

    @Test
    void shouldReturnForbiddenWhenUpdatingMcpWithEnterpriseAuthType() {
        // Given — create a valid MCP server first, then try to switch to an EE-only auth type
        ApiMcpServer mcp = buildMcp(IdUtils.create());
        ApiMcpServer created = client.toBlocking().retrieve(POST(MCP_PATH, mcp), ApiMcpServer.class);
        ApiMcpServer update = new ApiMcpServer(created.id(), created.description(),
            null, null, McpServer.AuthType.API_TOKEN, null, null,true, false, null, null);

        // When / Then
        HttpClientResponseException e = Assertions.assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(PUT(MCP_PATH + "/" + created.id(), update), ApiMcpServer.class)
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.FORBIDDEN.getCode());
    }

    /** Builds a valid {@link ApiMcpServer} request payload with a unique id. */
    private static ApiMcpServer buildMcp(String uniqueSuffix) {
        return new ApiMcpServer("test-mcp-" + uniqueSuffix.toLowerCase(),
            "A test description", null, null, null, null, null, false, false, null, null);
    }
}
