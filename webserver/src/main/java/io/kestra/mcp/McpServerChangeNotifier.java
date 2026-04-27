package io.kestra.mcp;

import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.trigger.McpToolTrigger;
import com.google.common.annotations.VisibleForTesting;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static io.kestra.plugin.core.trigger.McpToolTrigger.DEFAULT_SERVER_ID;

@Slf4j
@Context
@Requires(beans = McpServerHandlerTransport.class)
public class McpServerChangeNotifier {
    private final Provider<McpServerHandlerTransport> mcpServerHandlerTransport;
    private final BroadcastQueueInterface<FlowInterface> flowQueue;
    private final BroadcastQueueInterface<McpServer> mcpQueue;
    private final FlowRepositoryInterface flowRepository;

    private QueueSubscriber<FlowInterface> flowSubscriber;
    private QueueSubscriber<McpServer> mcpSubscriber;

    @Inject
    public McpServerChangeNotifier(
        Provider<McpServerHandlerTransport> mcpServerHandlerTransport,
        BroadcastQueueInterface<FlowInterface> flowQueue,
        BroadcastQueueInterface<McpServer> mcpQueue,
        FlowRepositoryInterface flowRepository
    ) {
        this.mcpServerHandlerTransport = mcpServerHandlerTransport;
        this.flowQueue = flowQueue;
        this.mcpQueue = mcpQueue;
        this.flowRepository = flowRepository;
    }

    @PostConstruct
    public void start() {
        this.flowSubscriber = flowQueue.subscriber().subscribe(either -> {
            if (either.isRight()) {
                log.warn("Failed to deserialize flow event for MCP change notification: {}", either.getRight().getMessage());
                return;
            }
            handleFlowChange(either.getLeft());
        });

        this.mcpSubscriber = mcpQueue.subscriber().subscribe(either -> {
            if (either.isRight()) {
                log.warn("Failed to deserialize MCP server event for MCP change notification: {}", either.getRight().getMessage());
                return;
            }
            handleMcpChange(either.getLeft());
        });
    }

    @PreDestroy
    public void stop() {
        if (flowSubscriber != null) {
            flowSubscriber.close();
        }
        if (mcpSubscriber != null) {
            mcpSubscriber.close();
        }
    }

    @VisibleForTesting
    public void handleFlowChange(FlowInterface flow) {
        Set<String> newServerIds = extractMcpServerIds(flow);
        Set<String> previousServerIds = extractPreviousRevisionServerIds(flow);

        // Refresh the union: new servers get updated tools, removed servers lose stale tools
        Set<String> serverIdsToRefresh = new HashSet<>(newServerIds);
        serverIdsToRefresh.addAll(previousServerIds);

        if (serverIdsToRefresh.isEmpty()) {
            return;
        }

        log.debug("Flow id: {} changed; refreshing tools on {} MCP server(s)", flow.getId(), serverIdsToRefresh.size());

        boolean flowDeletedOrDisabled = flow.isDisabled() || flow.isDeleted();
        for (String serverId : serverIdsToRefresh) {
            mcpServerHandlerTransport.get().refreshTools(flow.getTenantId(), serverId, flowDeletedOrDisabled)
                .doOnError(e -> log.error("Failed to refresh tools for server {}: {}", serverId, e.getMessage()))
                .doOnSuccess((_) -> log.debug("Tool list change notification successfully sent serverId: {}", serverId))
                .onErrorComplete()
                .subscribe();
        }
    }

    private Set<String> extractPreviousRevisionServerIds(FlowInterface flow) {
        Integer revision = flow.getRevision();
        if (revision == null || revision <= 1) {
            return Set.of();
        }
        return flowRepository.findRevisions(flow.getTenantId(), flow.getNamespace(), flow.getId(), true, List.of(revision - 1))
            .stream()
            .findFirst()
            .map(previous -> ListUtils.emptyOnNull(previous.getTriggers()).stream()
                .filter(t -> t instanceof McpToolTrigger && !t.isDisabled())
                .map(t -> Objects.requireNonNullElse(((McpToolTrigger) t).getMcpServer(), DEFAULT_SERVER_ID))
                .collect(Collectors.toSet()))
            .orElse(Set.of());
    }

    @VisibleForTesting
    public void handleMcpChange(McpServer mcpServer) {
        log.debug("MCP server {} changed; evicting cached instance and notifying sessions", mcpServer.id());

        if (!mcpServer.deleted() && !mcpServer.disabled()) {
            log.debug("No action needed for server tenantId: {}, serverId: {} as it is not deleted or disabled", mcpServer.tenantId(), mcpServer.id());
            return;
        }

        mcpServerHandlerTransport.get().evictAndNotify(mcpServer)
            .doOnError(e -> log.error("Failed to evict MCP server {}: {}", mcpServer.id(), e.getMessage()))
            .onErrorComplete()
            .subscribe();
    }

    private Set<String> extractMcpServerIds(FlowInterface flow) {
        if (!(flow instanceof GenericFlow genericFlow)) {
            return Set.of();
        }
        return genericFlow.getTriggers().stream()
            .filter(t -> McpToolTrigger.class.getName().equals(t.getType()))
            .map(t -> (String) t.getAdditionalProperties().getOrDefault("mcpServer", DEFAULT_SERVER_ID))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
}
