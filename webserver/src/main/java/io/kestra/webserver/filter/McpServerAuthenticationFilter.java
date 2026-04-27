package io.kestra.webserver.filter;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.services.BasicAuthService;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Filter("/api/v1/*/mcp/*")
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "micronaut.security.enabled", notEquals = "true")
public class McpServerAuthenticationFilter implements HttpServerFilter {

    /** Request attribute set when this filter has already made the auth decision. */
    public static final String MCP_AUTH_HANDLED = "io.kestra.mcp.auth.handled";

    private final McpServerRepositoryInterface mcpServerRepository;
    private final BroadcastQueueInterface<McpServer> mcpQueue;
    private final BasicAuthService basicAuthService;
    private final TenantService tenantService;

    private final Cache<McpCacheKey, McpServer> mcpConfigCache = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build();
    private QueueSubscriber<McpServer> mcpSubscriber;

    @Inject
    public McpServerAuthenticationFilter(
        McpServerRepositoryInterface mcpServerRepository,
        BroadcastQueueInterface<McpServer> mcpQueue,
        BasicAuthService basicAuthService,
        TenantService tenantService
    ) {
        this.mcpServerRepository = mcpServerRepository;
        this.mcpQueue = mcpQueue;
        this.basicAuthService = basicAuthService;
        this.tenantService = tenantService;
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order() - 1;
    }

    @PostConstruct
    public void start() {
        mcpSubscriber = mcpQueue.subscriber().subscribe(either -> {
            if (either.isRight()) {
                log.warn("Failed to deserialize MCP server event in auth filter: {}", either.getRight().getMessage());
                return;
            }
            McpServer mcpServer = either.getLeft();
            McpCacheKey key = new McpCacheKey(mcpServer.tenantId(), mcpServer.id());
            if (mcpServer.deleted()) {
                mcpConfigCache.invalidate(key);
            } else {
                mcpConfigCache.put(key, mcpServer);
            }
        });
    }

    @PreDestroy
    public void stop() {
        if (mcpSubscriber != null) {
            mcpSubscriber.close();
        }
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
        String tenantId = tenantService.resolveTenant();
        String serverId = parts[5];
        McpCacheKey key = new McpCacheKey(tenantId, serverId);
        McpServer mcpServer = mcpConfigCache.getIfPresent(key);
        if (mcpServer == null) {
            mcpServer = mcpServerRepository.get(tenantId, serverId).orElse(null);
            if (mcpServer != null) {
                mcpConfigCache.put(key, mcpServer);
            }
        }
        return Optional.ofNullable(mcpServer);
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

    private record McpCacheKey(String tenantId, String serverId) {}
}
