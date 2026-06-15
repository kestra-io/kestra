package io.kestra.mcp;

import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.mcp.models.McpServer;
import com.google.common.annotations.VisibleForTesting;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Singleton
@Requires(beans = DispatchQueueInterface.class)
@Slf4j
public class McpServerHandlerTransport {
    private final Map<HandlerKey, KestraFluxStreamableServerTransportProvider> handlers = new ConcurrentHashMap<>();
    private final Map<HandlerKey, McpAsyncServer> servers = new ConcurrentHashMap<>();
    private final McpErrorResponseMapper mcpErrorResponseMapper;
    private final McpToolService mcpToolService;
    private final McpServerCache mcpServerCache;
    private final McpSessionService mcpSessionService;

    @Inject
    public McpServerHandlerTransport(
        McpErrorResponseMapper mcpErrorResponseMapper,
        McpToolService mcpToolService,
        McpServerCache mcpServerCache,
        McpSessionService mcpSessionService
    ) {
        this.mcpErrorResponseMapper = mcpErrorResponseMapper;
        this.mcpToolService = mcpToolService;
        this.mcpServerCache = mcpServerCache;
        this.mcpSessionService = mcpSessionService;
    }

    public KestraFluxStreamableServerTransportProvider getServerHandler(
        KestraMcpTransportContext kestraMcpTransportContext
    ) {
        return handlers.computeIfAbsent(HandlerKey.from(kestraMcpTransportContext), handlerKey -> {
            log.debug("Building server for handler transportContext: {}", kestraMcpTransportContext);
            KestraFluxStreamableServerTransportProvider transportProvider = new KestraFluxStreamableServerTransportProvider(
                mcpErrorResponseMapper,
                mcpSessionService
            );
            servers.put(handlerKey, buildServer(handlerKey, transportProvider));
            return transportProvider;
        });
    }

    public Mono<Void> refreshTools(String tenantId, String serverId) {
        HandlerKey key = new HandlerKey(tenantId, serverId);
        McpAsyncServer server = servers.get(key);
        if (server == null) {
            return Mono.empty();
        }

        McpServer.ServerType serverType = mcpServerCache.get(tenantId, serverId)
            .map(McpServer::serverType)
            .orElse(McpServer.ServerType.PRIVATE);

        List<McpServerFeatures.AsyncToolSpecification> newSpecs =
            mcpToolService.listToolSpecsForServer(tenantId, serverId, serverType);
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

        log.debug("Sending notify tools list changed tenantId: {}, serverId: {}", tenantId, serverId);
        return removeStale.then(upsertCurrent);
    }

    public Mono<Void> evictAndNotify(String tenantId, String serverId) {
        HandlerKey key = new HandlerKey(tenantId, serverId);
        KestraFluxStreamableServerTransportProvider transport = handlers.remove(key);
        McpAsyncServer asyncServer = servers.remove(key);

        log.debug("Initiating graceful shutdown tenantId: {}, serverId: {} as the server is deleted or disabled", tenantId, serverId);
        Mono<Void> transportClosed = Optional.ofNullable(transport).map(KestraFluxStreamableServerTransportProvider::closeGracefully).orElseGet(() -> {
            log.debug("No transport providers found for tenantId: {}, serverId: {}", tenantId, serverId);
            return Mono.empty();
        });

        return transportClosed.then(Optional.ofNullable(asyncServer).map(McpAsyncServer::closeGracefully).orElseGet(() -> {
            log.debug("No server found for tenantId: {}, serverId: {}", tenantId, serverId);
            return Mono.empty();
        }));
    }

    @VisibleForTesting
    public Flux<McpSchema.Tool> listToolsForServer(String tenantId, String serverId) {
        McpAsyncServer server = servers.get(new HandlerKey(tenantId, serverId));
        return server != null ? server.listTools() : Flux.empty();
    }

    private McpAsyncServer buildServer(
        HandlerKey handlerKey,
        KestraFluxStreamableServerTransportProvider serverTransport
    ) {
        var mcpServerSpec = io.modelcontextprotocol.server.McpServer.async(serverTransport)
            .capabilities(
                McpSchema.ServerCapabilities.builder()
                    .tools(true)
                    .build()
            );

        Optional<McpServer> serverOpt = mcpServerCache.get(handlerKey.tenantId(), handlerKey.serverId());
        serverOpt.ifPresent(mcpServer -> {
            mcpServerSpec.serverInfo(mcpServer.id(), "1.0.0");
            if (mcpServer.instructions() != null) {
                mcpServerSpec.instructions(mcpServer.instructions());
            }
        });

        McpServer.ServerType serverType = serverOpt.map(McpServer::serverType).orElse(McpServer.ServerType.PRIVATE);
        return mcpServerSpec.tools(this.mcpToolService.listToolSpecsForServer(
            handlerKey.tenantId(),
            handlerKey.serverId(),
            serverType
        )).build();
    }


    private record HandlerKey(
        String tenantId,
        String serverId
    ) {
        public static HandlerKey from(KestraMcpTransportContext kestraMcpTransportContext) {
            return new HandlerKey(
                kestraMcpTransportContext.getTenantId(),
                kestraMcpTransportContext.getServerId()
            );
        }
    }
}
