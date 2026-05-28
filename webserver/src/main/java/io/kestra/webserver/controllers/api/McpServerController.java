package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Optional;

import io.kestra.core.exceptions.ConflictException;
import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.EditionProvider;
import io.kestra.webserver.models.api.ApiMcpServer;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;

@Controller("/api/v1/{tenant}/mcp/servers")
@Slf4j
public class McpServerController {

    private final McpServerRepositoryInterface mcpServerRepository;
    private final TenantService tenantService;
    private final EditionProvider editionProvider;

    @Inject
    public McpServerController(McpServerRepositoryInterface mcpServerRepository, TenantService tenantService, EditionProvider editionProvider) {
        this.mcpServerRepository = mcpServerRepository;
        this.tenantService = tenantService;
        this.editionProvider = editionProvider;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Mcp"}, summary = "List MCP servers")
    public PagedResults<ApiMcpServer> listMcps(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort) {
        return PagedResults.of(
            mcpServerRepository.find(PageableUtils.from(page, size, sort), tenantService.resolveTenant())
                .map(ApiMcpServer::from)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Get an MCP server")
    public ApiMcpServer getMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id) {
        return mcpServerRepository.get(tenantService.resolveTenant(), id)
            .map(ApiMcpServer::from)
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post
    @Operation(tags = {"Mcp"}, summary = "Create an MCP server")
    public HttpResponse<ApiMcpServer> createMcp(
        @RequestBody(description = "The MCP server to create") @Body @Valid ApiMcpServer mcpServer) {
        String tenantId = tenantService.resolveTenant();

        if (McpServer.DEFAULT_ID.equals(mcpServer.id())) {
            throw new InvalidException(mcpServer, "MCP id '" + McpServer.DEFAULT_ID + "' is reserved");
        }

        requireEnvCompatibleAuthType(mcpServer.authType());

        if (mcpServerRepository.get(tenantId, mcpServer.id()).isPresent()) {
            throw new ConflictException("MCP server already exists with id: '" + mcpServer.id() + "'");
        }

        McpServer toSave = new McpServer(tenantId,
            mcpServer.id(), mcpServer.description(), mcpServer.instructions(),
            mcpServer.serverType(), mcpServer.authType(),
            mcpServer.disabled(), false, false, null, null);

        return HttpResponse.ok(ApiMcpServer.from(mcpServerRepository.save(null, toSave)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Update an MCP server")
    public HttpResponse<ApiMcpServer> updateMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id,
        @RequestBody(description = "The MCP server to update") @Body @Valid ApiMcpServer mcpServer) {
        String tenantId = tenantService.resolveTenant();

        Optional<McpServer> existing = mcpServerRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + id);
        }

        if (McpServer.DEFAULT_ID.equals(mcpServer.id()) != existing.get().isDefault()) {
            throw new InvalidException(mcpServer, "MCP id '" + McpServer.DEFAULT_ID + "' is reserved");
        }

        requireEnvCompatibleAuthType(mcpServer.authType());

        McpServer toSave = new McpServer(tenantId, id,
            mcpServer.description(), mcpServer.instructions(),
            mcpServer.serverType(), mcpServer.authType(),
            mcpServer.disabled(), false, false, null, null);

        return HttpResponse.ok(ApiMcpServer.from(mcpServerRepository.save(existing.get(), toSave)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{id}")
    @Operation(tags = {"Mcp"}, summary = "Delete an MCP server")
    public HttpResponse<Void> deleteMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id) {
        String tenantId = tenantService.resolveTenant();
        Optional<McpServer> existing = mcpServerRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + id);
        }
        if (existing.get().isDefault()) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "The default MCP server cannot be deleted");
        }
        return mcpServerRepository.delete(tenantId, id)
            .map(ignored -> HttpResponse.<Void>status(HttpStatus.NO_CONTENT))
            .orElse(HttpResponse.status(HttpStatus.NOT_FOUND));
    }

    private void requireEnvCompatibleAuthType(McpServer.AuthType authType) {
        if (editionProvider.get() == EditionProvider.Edition.OSS && authType == McpServer.AuthType.API_TOKEN) {
            throw new HttpStatusException(HttpStatus.FORBIDDEN, "Auth type '" + authType + "' requires Enterprise Edition");
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Patch(uri = "{id}/toggle")
    @Operation(tags = {"Mcp"}, summary = "Toggle an MCP server's enabled state")
    public HttpResponse<ApiMcpServer> toggleMcp(
        @Parameter(description = "The MCP server id") @PathVariable String id) {
        String tenantId = tenantService.resolveTenant();
        Optional<McpServer> existing = mcpServerRepository.get(tenantId, id);
        if (existing.isEmpty()) {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, "MCP server not found: " + id);
        }
        McpServer mcpServer = existing.get();
        McpServer toggled = new McpServer(tenantId, mcpServer.id(),
            mcpServer.description(), mcpServer.instructions(),
            mcpServer.serverType(), mcpServer.authType(),
            !mcpServer.disabled(), false, false, null, null);
        return HttpResponse.ok(ApiMcpServer.from(mcpServerRepository.save(mcpServer, toggled)));
    }
}
