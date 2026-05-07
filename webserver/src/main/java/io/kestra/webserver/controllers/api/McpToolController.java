package io.kestra.webserver.controllers.api;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.kestra.core.tenant.TenantService;
import io.micronaut.http.annotation.*;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.kestra.mcp.McpServerHandlerTransport;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import io.kestra.mcp.McpSessionFactory;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Controller("/api/v1/{tenant}/mcp")
public class McpToolController {
    private final McpServerHandlerTransport handlerRegistry;
    private final TenantService tenantService;
    private final McpSessionFactory sessionFactory;
    private final McpServerRepositoryInterface mcpServerRepository;

    @Inject
    public McpToolController(
        McpServerHandlerTransport handlerRegistry,
        TenantService tenantService,
        McpSessionFactory sessionFactory,
        McpServerRepositoryInterface mcpServerRepository
    ) {
        this.handlerRegistry = handlerRegistry;
        this.tenantService = tenantService;
        this.sessionFactory = sessionFactory;
        this.mcpServerRepository = mcpServerRepository;
    }

    @Get("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_EVENT_STREAM})
    public Mono<? extends HttpResponse<?>> handleGetRequest(
        @NotNull String tenant,
        @NotNull String id,
        HttpRequest<String> request) {
        return handleRequest(tenant, id, request);
    }

    @Delete("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON})
    public Mono<? extends HttpResponse<?>> handleDeleteRequest(
        @NotNull String tenant,
        @PathVariable String id,
        HttpRequest<String> request) {
        return handleRequest(tenant, id, request);
    }

    @Post("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON})
    public Mono<? extends HttpResponse<?>> handleRequest(
        @NotNull String tenant,
        @PathVariable String id,
        HttpRequest<String> request) {

        String tenantId = tenantService.resolveTenant();

        Optional<McpServer> mcpServer = mcpServerRepository.get(tenantId, id);
        if (mcpServer.isEmpty()) {
            return Mono.just(HttpResponse.notFound());
        }
        if (mcpServer.get().disabled()) {
            return Mono.just(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE));
        }

        var transportContext = sessionFactory.build(
            tenantId, id, request.getHeaders().get(HttpHeaders.MCP_SESSION_ID)
        );

        return handlerRegistry.getServerHandler(transportContext).handleRequest(request, transportContext);
    }
}
