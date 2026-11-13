package io.kestra.webserver.controllers.api;

import io.kestra.mcp.McpServerHandlerTransport;
import io.kestra.mcp.PlatformMcpServerProvider;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;
import reactor.core.publisher.Mono;

/**
 * Serves the platform MCP server, which belongs to no tenant: its tools take the tenant to act on as an argument.
 */
@Controller("/api/v1/mcp")
public class McpPlatformController {
    private final McpServerHandlerTransport handlerRegistry;

    @Inject
    public McpPlatformController(McpServerHandlerTransport handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @Get
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.TEXT_EVENT_STREAM })
    public Mono<? extends HttpResponse<?>> handleGetRequest(HttpRequest<String> request) {
        return handleRequest(request);
    }

    @Delete
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON })
    public Mono<? extends HttpResponse<?>> handleDeleteRequest(HttpRequest<String> request) {
        return handleRequest(request);
    }

    @Post
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({ MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON })
    public Mono<? extends HttpResponse<?>> handleRequest(HttpRequest<String> request) {
        return handlerRegistry.handle(null, PlatformMcpServerProvider.SERVER_ID, request);
    }
}
