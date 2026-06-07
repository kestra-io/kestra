package io.kestra.webserver.filter;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.tenant.TenantService;
import io.kestra.mcp.McpServerCache;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

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
        TenantService tenantService
    ) {
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
        return Mono.fromCallable(() -> resolveServer(request))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(optMcpServer -> optMcpServer.isEmpty()
                ? chain.proceed(request)
                : authenticate(request, chain, optMcpServer.get()));
    }

    private Optional<McpServer> resolveServer(HttpRequest<?> request) {
        String[] parts = request.getPath().split("/");
        if (parts.length < 6) {
            return Optional.empty();
        }
        return mcpServerCache.get(tenantService.resolveTenant(), parts[5]);
    }

    private Publisher<MutableHttpResponse<?>> authenticate(
        HttpRequest<?> request,
        ServerFilterChain chain,
        McpServer mcpServer
    ) {
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
        return Flux.just(HttpResponse.<Object>unauthorized().header("WWW-Authenticate", "Basic"));
    }
}
