package io.kestra.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.queues.DispatchQueueInterface;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.HttpHeaders;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Singleton
@Requires(beans = DispatchQueueInterface.class)
@Slf4j
public class McpServerHandlerTransport {
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

    private final Map<HandlerKey, KestraFluxStreamableServerTransportProvider> handlers = new ConcurrentHashMap<>();
    private final Map<HandlerKey, ScopedServer> servers = new ConcurrentHashMap<>();
    private final McpErrorResponseMapper mcpErrorResponseMapper;
    private final Map<McpToolSource.Kind, McpToolSource> toolSources;
    private final McpServerCache mcpServerCache;
    private final McpSessionService mcpSessionService;
    private final McpSessionFactory sessionFactory;

    @Inject
    public McpServerHandlerTransport(
        McpErrorResponseMapper mcpErrorResponseMapper,
        List<McpToolSource> toolSources,
        McpServerCache mcpServerCache,
        McpSessionService mcpSessionService,
        McpSessionFactory sessionFactory) {
        this.mcpErrorResponseMapper = mcpErrorResponseMapper;
        this.toolSources = toolSources.stream().collect(Collectors.toMap(McpToolSource::kind, Function.identity()));
        this.mcpServerCache = mcpServerCache;
        this.mcpSessionService = mcpSessionService;
        this.sessionFactory = sessionFactory;
    }

    /**
     * Serves an MCP request to a server, responding 404 when the server does not exist or no tool source can
     * serve it, and 503 when it is disabled.
     *
     * @param tenantId the tenant the server belongs to, or {@code null} for a server that belongs to none
     * @param callerId the caller, or {@code null} when anonymous
     */
    public Mono<? extends HttpResponse<?>> handle(
        @Nullable String tenantId,
        String serverId,
        @Nullable String callerId,
        HttpRequest<String> request) {
        Optional<McpServerCache.ResolvedServer> server = mcpServerCache.resolve(tenantId, serverId);
        if (server.isEmpty()) {
            return Mono.just(HttpResponse.notFound());
        }
        if (server.get().server().disabled()) {
            return Mono.just(HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE));
        }
        if (!supports(server.get().toolSource())) {
            log.warn("MCP server '{}' needs a {} tool source, but none is registered", serverId, server.get().toolSource());
            return Mono.just(HttpResponse.notFound());
        }

        KestraMcpTransportContext context = sessionFactory.build(
            tenantId, serverId, request.getHeaders().get(HttpHeaders.MCP_SESSION_ID), callerId
        );
        return getServerHandler(context).handleRequest(request, context);
    }

    /**
     * The built tool list is part of what is cached here, so the key carries the caller's view alongside the
     * server: callers whose tool source puts them in the same view share an instance.
     */
    public KestraFluxStreamableServerTransportProvider getServerHandler(
        KestraMcpTransportContext kestraMcpTransportContext) {
        McpToolSource.Kind kind = mcpServerCache.resolve(kestraMcpTransportContext.getTenantId(), kestraMcpTransportContext.getServerId())
            .map(McpServerCache.ResolvedServer::toolSource)
            .orElse(McpToolSource.Kind.FLOWS);
        McpToolSource toolSource = Optional.ofNullable(toolSources.get(kind))
            .orElseThrow(() -> new IllegalStateException("No MCP tool source is registered for %s servers.".formatted(kind)));
        McpToolView view = toolSource.view(kestraMcpTransportContext);
        HandlerKey key = HandlerKey.from(kestraMcpTransportContext, view);

        return handlers.computeIfAbsent(key, handlerKey ->
        {
            log.debug("Building server for handler transportContext: {}", kestraMcpTransportContext);
            KestraFluxStreamableServerTransportProvider transportProvider = new KestraFluxStreamableServerTransportProvider(
                mcpErrorResponseMapper,
                mcpSessionService
            );
            servers.put(
                handlerKey,
                new ScopedServer(
                    buildServer(handlerKey, view, transportProvider), view, kind, handlerKey.tenantId(), handlerKey.serverId()
                )
            );
            return transportProvider;
        });
    }

    /**
     * Shuts down every server built by this registry when the application context closes.
     * <p>
     * Each {@link KestraFluxStreamableServerTransportProvider} starts a keep-alive scheduler in its
     * constructor; without this hook those schedulers outlive the context and keep pinging dead
     * sessions for the lifetime of the JVM, which is particularly visible in tests where many
     * contexts are created in a single JVM.
     */
    @PreDestroy
    public void close() {
        Flux.fromIterable(
            handlers.keySet().stream().map(key -> new ServerRef(key.tenantId(), key.serverId())).distinct().toList()
        )
            .concatMap(server -> evictAndNotify(server.tenantId(), server.serverId()).onErrorComplete())
            .then()
            .block(SHUTDOWN_TIMEOUT);
    }

    /**
     * Re-lists the tools of every instance built for this server, provided it is backed by the given kind of
     * tool source, and notifies the sessions connected to it of what changed.
     */
    public Mono<Void> refreshTools(String tenantId, String serverId, McpToolSource.Kind toolSource) {
        return Flux.fromIterable(keysFor(tenantId, serverId))
            .concatMap(
                key -> Optional.ofNullable(servers.get(key))
                    .filter(scoped -> scoped.toolSource() == toolSource)
                    .map(this::refreshTools)
                    .orElseGet(Mono::empty)
            )
            .then();
    }

    public Mono<Void> evictAndNotify(String tenantId, String serverId) {
        log.debug("Initiating graceful shutdown tenantId: {}, serverId: {} as the server is deleted or disabled", tenantId, serverId);

        List<HandlerKey> keys = keysFor(tenantId, serverId);
        if (keys.isEmpty()) {
            log.debug("No server found for tenantId: {}, serverId: {}", tenantId, serverId);
            return Mono.empty();
        }

        return Flux.fromIterable(keys)
            .concatMap(key ->
            {
                KestraFluxStreamableServerTransportProvider transport = handlers.remove(key);
                ScopedServer scopedServer = servers.remove(key);

                Mono<Void> transportClosed = Optional.ofNullable(transport)
                    .map(KestraFluxStreamableServerTransportProvider::closeGracefully)
                    .orElseGet(Mono::empty);

                return transportClosed.then(
                    Optional.ofNullable(scopedServer)
                        .map(scoped -> scoped.server().closeGracefully())
                        .orElseGet(Mono::empty)
                );
            })
            .then();
    }

    @VisibleForTesting
    public Flux<McpSchema.Tool> listToolsForServer(String tenantId, String serverId) {
        return Flux.fromIterable(keysFor(tenantId, serverId))
            .concatMap(key -> Optional.ofNullable(servers.get(key)).map(scoped -> scoped.server().listTools()).orElseGet(Flux::empty))
            .distinct(McpSchema.Tool::name);
    }

    private Mono<Void> refreshTools(ScopedServer scopedServer) {
        McpAsyncServer server = scopedServer.server();
        List<McpServerFeatures.AsyncToolSpecification> newSpecs = scopedServer.view().tools();
        Set<String> newToolNames = newSpecs.stream()
            .map(spec -> spec.tool().name())
            .collect(Collectors.toSet());

        Mono<Void> removeStale = server.listTools()
            .filter(tool -> !newToolNames.contains(tool.name()))
            .concatMap(tool -> server.removeTool(tool.name()))
            .then();

        Mono<Void> upsertCurrent = Flux.fromIterable(newSpecs)
            .concatMap(server::addTool)
            .then();

        log.debug("Sending notify tools list changed tenantId: {}, serverId: {}", scopedServer.tenantId(), scopedServer.serverId());
        return removeStale.then(upsertCurrent);
    }

    private List<HandlerKey> keysFor(String tenantId, String serverId) {
        return handlers.keySet().stream()
            .filter(key -> Objects.equals(key.tenantId(), tenantId) && Objects.equals(key.serverId(), serverId))
            .toList();
    }

    /**
     * @return whether a tool source is registered for this kind of server, i.e. whether it can be served
     */
    private boolean supports(McpToolSource.Kind kind) {
        return toolSources.containsKey(kind);
    }

    private McpAsyncServer buildServer(
        HandlerKey handlerKey,
        McpToolView view,
        KestraFluxStreamableServerTransportProvider serverTransport) {
        var mcpServerSpec = io.modelcontextprotocol.server.McpServer.async(serverTransport)
            .capabilities(
                McpSchema.ServerCapabilities.builder()
                    .tools(true)
                    .build()
            );

        Optional<McpServer> serverOpt = mcpServerCache.get(handlerKey.tenantId(), handlerKey.serverId());
        serverOpt.ifPresent(mcpServer ->
        {
            mcpServerSpec.serverInfo(mcpServer.id(), "1.0.0");
            if (mcpServer.instructions() != null) {
                mcpServerSpec.instructions(mcpServer.instructions());
            }
        });

        return mcpServerSpec.tools(view.tools()).build();
    }

    private record HandlerKey(
        String tenantId,
        String serverId,
        String viewKey) {
        public static HandlerKey from(KestraMcpTransportContext kestraMcpTransportContext, McpToolView view) {
            return new HandlerKey(
                kestraMcpTransportContext.getTenantId(),
                kestraMcpTransportContext.getServerId(),
                view.cacheKey()
            );
        }
    }

    private record ServerRef(
        String tenantId,
        String serverId) {
    }

    private record ScopedServer(
        McpAsyncServer server,
        McpToolView view,
        McpToolSource.Kind toolSource,
        String tenantId,
        String serverId) {
    }
}
