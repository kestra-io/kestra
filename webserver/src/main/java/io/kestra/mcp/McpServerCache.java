package io.kestra.mcp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.kestra.core.mcp.models.McpServer;
import io.kestra.core.mcp.models.McpServerClusterEventPayload;
import io.kestra.core.mcp.repositories.McpServerRepositoryInterface;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.server.ClusterEvent;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Shared, cluster-aware cache of {@link McpServer} entries keyed by {@code (tenantId, serverId)}.
 *
 * <p>Subscribes once to {@link ClusterEvent.EventType#MCP_SERVER_CHANGED} and invalidates the
 * matching entry whenever a server is created, updated, or deleted on any node. Other components
 * that maintain derived caches keyed off the same {@code (tenantId, serverId)} can register an
 * invalidation listener via {@link #addInvalidationListener(BiConsumer)} instead of subscribing
 * to the cluster-event queue themselves.
 */
@Slf4j
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
public class McpServerCache {

    private final McpServerRepositoryInterface mcpServerRepository;
    private final BroadcastQueueInterface<ClusterEvent> clusterEventQueue;

    private final Cache<McpCacheKey, McpServer> cache;
    private final List<BiConsumer<String, String>> invalidationListeners = new CopyOnWriteArrayList<>();
    private QueueSubscriber<ClusterEvent> clusterEventSubscriber;

    @Inject
    public McpServerCache(
        McpServerRepositoryInterface mcpServerRepository,
        BroadcastQueueInterface<ClusterEvent> clusterEventQueue,
        McpConfig mcpConfig
    ) {
        this.mcpServerRepository = Objects.requireNonNull(mcpServerRepository);
        this.clusterEventQueue = Objects.requireNonNull(clusterEventQueue);
        McpConfig.ServerCacheConfig serverCacheConfig = mcpConfig.serverCacheConfig();
        this.cache = Caffeine.newBuilder()
            .maximumSize(serverCacheConfig.maximumSize())
            .expireAfterAccess(serverCacheConfig.expireAfterAccess())
            .build();
    }

    @PostConstruct
    public void start() {
        clusterEventSubscriber = clusterEventQueue.subscriber().subscribe(either -> {
            if (either.isRight()) {
                log.warn("Failed to deserialize cluster event in MCP server cache: {}", either.getRight().getMessage());
                return;
            }
            ClusterEvent event = either.getLeft();
            if (event.eventType() == ClusterEvent.EventType.MCP_SERVER_CHANGED) {
                McpServerClusterEventPayload payload = McpServerClusterEventPayload.fromJson(event.message());
                invalidate(payload.tenantId(), payload.serverId());
            }
        });
    }

    @PreDestroy
    public void stop() {
        if (clusterEventSubscriber != null) {
            clusterEventSubscriber.close();
        }
    }

    /**
     * Returns the {@link McpServer} for the given tenant + server id, hitting the repository on a
     * cache miss and caching the result if present.
     */
    public Optional<McpServer> get(String tenantId, String serverId) {
        McpCacheKey key = new McpCacheKey(tenantId, serverId);
        McpServer cached = cache.getIfPresent(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<McpServer> server = mcpServerRepository.get(tenantId, serverId);
        server.ifPresent(s -> cache.put(key, s));
        return server;
    }

    /**
     * Drops any cached entry for the given tenant + server id and notifies invalidation listeners.
     */
    public void invalidate(String tenantId, String serverId) {
        cache.invalidate(new McpCacheKey(tenantId, serverId));
        for (BiConsumer<String, String> listener : invalidationListeners) {
            try {
                listener.accept(tenantId, serverId);
            } catch (Exception e) {
                log.warn("MCP server cache invalidation listener threw", e);
            }
        }
    }

    /**
     * Registers a listener invoked whenever an entry is invalidated (whether by a cluster event
     * or an explicit {@link #invalidate(String, String)} call). Useful for components that keep
     * derived caches keyed by the same {@code (tenantId, serverId)}.
     */
    public void addInvalidationListener(BiConsumer<String, String> listener) {
        invalidationListeners.add(Objects.requireNonNull(listener));
    }

    private record McpCacheKey(String tenantId, String serverId) {}
}
