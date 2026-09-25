package io.kestra.webserver.filter;

import java.util.Optional;

import org.reactivestreams.Publisher;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.tenant.TenantService;
import io.kestra.mcp.McpServerCache;
import io.kestra.webserver.controllers.api.McpToolController;
import io.kestra.webserver.services.BasicAuthService;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Filter("/api/v1/*/mcp/*")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true")
public class McpServerAuthenticationFilter implements HttpServerFilter {

    /** Request attribute set when this filter has already made the auth decision. */
    public static final String MCP_AUTH_HANDLED = "io.kestra.mcp.auth.handled";

    private final McpServerCache mcpServerCache;
    private final BasicAuthService basicAuthService;
    private final TenantService tenantService;

    @Inject
    public McpServerAuthenticationFilter(
        McpServerCache mcpServerCache,
        BasicAuthService basicAuthService,
        TenantService tenantService) {
        this.mcpServerCache = mcpServerCache;
        this.basicAuthService = basicAuthService;
        this.tenantService = tenantService;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() - 1;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<String> serverId = mcpServerId(request);
        if (serverId.isEmpty()) {
            return chain.proceed(request);
        }
        return Mono.fromCallable(() -> mcpServerCache.get(tenantService.resolveTenant(), serverId.get()))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(
                optMcpServer -> optMcpServer.isEmpty()
                    ? chain.proceed(request)
                    : authenticate(request, chain, optMcpServer.get())
            );
    }

    /**
     * Resolves the {@code id} path variable of a matched {@link McpToolController} route instead of splitting
     * {@link HttpRequest#getPath()}, which is the raw, still-percent-encoded request target and can
     * disagree with the controller on a request whose id is percent-encoded.
     */
    @SuppressWarnings("rawtypes")
    private Optional<String> mcpServerId(HttpRequest<?> request) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (
            routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch<?, ?> method
                && McpToolController.class.isAssignableFrom(method.getDeclaringType())
                && method.getVariableValues().get("id") instanceof String id
        ) {
            return Optional.of(id);
        }
        return Optional.empty();
    }

    private Publisher<MutableHttpResponse<?>> authenticate(
        HttpRequest<?> request,
        ServerFilterChain chain,
        McpServer mcpServer) {
        if (mcpServer.serverType() == McpServer.ServerType.PUBLIC) {
            // Public servers require no auth — enabled/disabled is the controller's concern
            request.getAttributes().put(MCP_AUTH_HANDLED, true);
            return chain.proceed(request);
        }

        // PRIVATE server: require Basic auth regardless of enabled state
        if (basicAuthService.isAuthenticated(request)) {
            request.getAttributes().put(MCP_AUTH_HANDLED, true);
            return chain.proceed(request);
        }
        return Flux.just(HttpResponse.<Object> unauthorized().header("WWW-Authenticate", "Basic"));
    }
}
