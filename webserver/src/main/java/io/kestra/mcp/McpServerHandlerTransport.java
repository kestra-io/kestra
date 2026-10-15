package io.kestra.mcp;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.models.AccessScope;
import io.kestra.core.queues.DispatchQueueInterface;

import io.micronaut.context.annotation.Requires;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
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
    private final McpToolService mcpToolService;
    private final McpServerCache mcpServerCache;
    private final McpSessionService mcpSessionService;
    private final McpToolAccessControl accessControl;

    @Inject
    public McpServerHandlerTransport(
        McpErrorResponseMapper mcpErrorResponseMapper,
        McpToolService mcpToolService,
        McpServerCache mcpServerCache,
        McpSessionService mcpSessionService,
        McpToolAccessControl accessControl) {
        this.mcpErrorResponseMapper = mcpErrorResponseMapper;
        this.mcpToolService = mcpToolService;
        this.mcpServerCache = mcpServerCache;
        this.mcpSessionService = mcpSessionService;
        this.accessControl = accessControl;
    }

    /**
     * The built tool list is part of what is cached here, so the key carries the caller's visibility
     * alongside the server. Callers granted the same namespaces share an instance, which keeps this to one
     * server per distinct permission set rather than one per user.
     */
    public KestraFluxStreamableServerTransportProvider getServerHandler(
        KestraMcpTransportContext kestraMcpTransportContext) {
        AccessScope scope = accessControl.executableScope(
            kestraMcpTransportContext.getUserId(),
            kestraMcpTransportContext.getTenantId(),
            kestraMcpTransportContext.getServerId()
        );
        HandlerKey key = HandlerKey.from(kestraMcpTransportContext, scope);

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
                    buildServer(handlerKey, scope, transportProvider), scope, handlerKey.tenantId(), handlerKey.serverId()
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

    public Mono<Void> refreshTools(String tenantId, String serverId) {
        return Flux.fromIterable(keysFor(tenantId, serverId))
            .concatMap(key -> Optional.ofNullable(servers.get(key)).map(this::refreshTools).orElseGet(Mono::empty))
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
        List<McpServerFeatures.AsyncToolSpecification> newSpecs = mcpToolService.listToolSpecsForServer(
            scopedServer.tenantId(), scopedServer.serverId(), scopedServer.scope()
        );
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

    private McpAsyncServer buildServer(
        HandlerKey handlerKey,
        AccessScope scope,
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

        return mcpServerSpec.tools(
            this.mcpToolService.listToolSpecsForServer(
                handlerKey.tenantId(),
                handlerKey.serverId(),
                scope
            )
        ).build();
    }

    private record HandlerKey(
        String tenantId,
        String serverId,
        String scopeKey) {
        public static HandlerKey from(KestraMcpTransportContext kestraMcpTransportContext, AccessScope scope) {
            return new HandlerKey(
                kestraMcpTransportContext.getTenantId(),
                kestraMcpTransportContext.getServerId(),
                McpToolScope.cacheKey(scope)
            );
        }
    }

    private record ServerRef(
        String tenantId,
        String serverId) {
    }

    private record ScopedServer(
        McpAsyncServer server,
        AccessScope scope,
        String tenantId,
        String serverId) {
    }
}
