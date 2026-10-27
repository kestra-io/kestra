package io.kestra.webserver.controllers.api;

import io.kestra.core.tenant.TenantService;
import io.kestra.mcp.McpServerHandlerTransport;
import io.kestra.mcp.McpToolAccessControl;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import reactor.core.publisher.Mono;

@Controller("/api/v1/{tenant}/mcp")
public class McpToolController {
    private final McpServerHandlerTransport handlerRegistry;
    private final TenantService tenantService;
    private final McpToolAccessControl accessControl;

    @Inject
    public McpToolController(
        McpServerHandlerTransport handlerRegistry,
        TenantService tenantService,
        McpToolAccessControl accessControl) {
        this.handlerRegistry = handlerRegistry;
        this.tenantService = tenantService;
        this.accessControl = accessControl;
    }

    @Get("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.TEXT_EVENT_STREAM })
    public Mono<? extends HttpResponse<?>> handleGetRequest(
        @NotNull String tenant,
        @NotNull String id,
        HttpRequest<String> request) {
        return handleRequest(tenant, id, request);
    }

    @Delete("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON })
    public Mono<? extends HttpResponse<?>> handleDeleteRequest(
        @NotNull String tenant,
        @PathVariable String id,
        HttpRequest<String> request) {
        return handleRequest(tenant, id, request);
    }

    @Post("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON })
    public Mono<? extends HttpResponse<?>> handleRequest(
        @NotNull String tenant,
        @PathVariable String id,
        HttpRequest<String> request) {
        return handlerRegistry.handle(tenantService.resolveTenant(), id, accessControl.callerId(request).orElse(null), request);
    }
}
