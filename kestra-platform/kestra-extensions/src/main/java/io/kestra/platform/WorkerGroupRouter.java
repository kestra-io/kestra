package io.kestra.platform;

import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.runners.WorkerTask;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Routes worker tasks to appropriate worker groups based on namespace patterns.
 *
 * This is the CORE INNOVATION that enables multi-worker-group support in Kestra OSS.
 *
 * Key Features:
 * - Routes tasks to namespace-specific worker groups
 * - Provides client isolation at the compute level
 * - Enables GPU allocation per client
 * - Tracks routing metrics
 * - Supports dynamic worker group configuration
 *
 * Configuration:
 * - All settings come from environment variables via application.yml
 * - NO hardcoded values
 * - Database-driven worker group configuration
 */
@Slf4j
@Singleton
@Requires(property = "kestra.platform.worker-groups.enabled", value = "true")
public class WorkerGroupRouter implements ApplicationEventListener<StartupEvent> {

    private final WorkerGroupRepository repository;
    private final boolean enabled;

    // Cache of worker group configurations (refreshed periodically)
    private final Map<String, WorkerGroupConfig> workerGroupCache;

    // Routing metrics
    private final Map<String, AtomicLong> routingMetrics;
    private final AtomicLong totalRoutedTasks;
    private final AtomicLong defaultRoutedTasks;

    // Timestamp of last cache refresh
    private volatile long lastCacheRefresh;

    // Configuration: cache refresh interval (from env)
    private final long cacheRefreshIntervalMs;

    public WorkerGroupRouter(
        WorkerGroupRepository repository,
        @Property(name = "kestra.platform.worker-groups.enabled") boolean enabled,
        @Property(name = "kestra.platform.worker-groups.cache-refresh-interval-ms",
                  defaultValue = "60000") long cacheRefreshIntervalMs
    ) {
        this.repository = repository;
        this.enabled = enabled;
        this.cacheRefreshIntervalMs = cacheRefreshIntervalMs;
        this.workerGroupCache = new ConcurrentHashMap<>();
        this.routingMetrics = new ConcurrentHashMap<>();
        this.totalRoutedTasks = new AtomicLong(0);
        this.defaultRoutedTasks = new AtomicLong(0);
        this.lastCacheRefresh = 0;

        log.info("WorkerGroupRouter initialized (enabled={})", enabled);
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        if (!enabled) {
            log.info("Worker group routing is DISABLED");
            return;
        }

        // Check if database tables exist
        if (!repository.tablesExist()) {
            log.warn("Worker group tables do not exist. Please run database migrations.");
            return;
        }

        // Initial cache load
        refreshCache();

        log.info("Worker group routing is ENABLED");
        log.info("Loaded {} worker groups", workerGroupCache.size());
    }

    /**
     * Route a worker task to the appropriate worker group.
     *
     * @param workerTask The task to route
     * @return The worker group name to route to, or null for default queue
     */
    public String routeTask(WorkerTask workerTask) {
        if (!enabled) {
            return null;  // Use default queue
        }

        // Refresh cache if needed
        refreshCacheIfNeeded();

        // Extract namespace from task
        TaskRun taskRun = workerTask.getTaskRun();
        String namespace = taskRun.getNamespace();

        // Find matching worker group
        Optional<WorkerGroupConfig> workerGroup = findWorkerGroupForNamespace(namespace);

        if (workerGroup.isPresent()) {
            WorkerGroupConfig config = workerGroup.get();
            String workerGroupName = config.getName();

            // Track metrics
            totalRoutedTasks.incrementAndGet();
            routingMetrics.computeIfAbsent(workerGroupName, k -> new AtomicLong(0))
                          .incrementAndGet();

            log.debug("Routing task {} from namespace '{}' to worker group '{}'",
                     taskRun.getId(), namespace, workerGroupName);

            return workerGroupName;
        } else {
            // No specific worker group, use default
            defaultRoutedTasks.incrementAndGet();

            log.debug("Routing task {} from namespace '{}' to DEFAULT worker group",
                     taskRun.getId(), namespace);

            return null;
        }
    }

    /**
     * Find the worker group that should handle the given namespace.
     *
     * Uses database query with regex matching for efficiency.
     *
     * @param namespace The namespace to match
     * @return Worker group config if found
     */
    private Optional<WorkerGroupConfig> findWorkerGroupForNamespace(String namespace) {
        // Query database for matching worker group
        // Database uses PostgreSQL regex operator (~) for efficient matching
        return repository.findWorkerGroupForNamespace(namespace);
    }

    /**
     * Get the Kafka topic name for a worker group.
     *
     * @param workerGroupName The worker group name
     * @return Kafka topic name (e.g., "workergroup-client1-cpu")
     */
    public String getKafkaTopicForWorkerGroup(String workerGroupName) {
        return "workergroup-" + workerGroupName;
    }

    /**
     * Refresh the worker group cache from database.
     *
     * Called on startup and periodically via scheduled task.
     */
    private void refreshCache() {
        try {
            Map<String, WorkerGroupConfig> newCache = new ConcurrentHashMap<>();

            // Load all active worker groups
            repository.findAllActive().forEach(config -> {
                newCache.put(config.getName(), config);
            });

            // Replace cache
            workerGroupCache.clear();
            workerGroupCache.putAll(newCache);

            lastCacheRefresh = System.currentTimeMillis();

            log.debug("Refreshed worker group cache: {} worker groups loaded",
                     workerGroupCache.size());

        } catch (Exception e) {
            log.error("Error refreshing worker group cache", e);
        }
    }

    /**
     * Refresh cache if it's older than the configured interval.
     */
    private void refreshCacheIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCacheRefresh > cacheRefreshIntervalMs) {
            refreshCache();
        }
    }

    /**
     * Scheduled task to refresh cache periodically.
     *
     * Runs every minute by default (configurable via cache-refresh-interval-ms).
     */
    @Scheduled(fixedDelay = "${kestra.platform.worker-groups.cache-refresh-interval-ms:60000}")
    public void scheduledCacheRefresh() {
        if (enabled) {
            refreshCache();
        }
    }

    /**
     * Get routing metrics.
     *
     * Useful for monitoring and debugging.
     *
     * @return Map of worker group name to task count
     */
    public Map<String, Long> getRoutingMetrics() {
        Map<String, Long> metrics = new ConcurrentHashMap<>();

        routingMetrics.forEach((workerGroup, count) ->
            metrics.put(workerGroup, count.get())
        );

        metrics.put("_total", totalRoutedTasks.get());
        metrics.put("_default", defaultRoutedTasks.get());

        return metrics;
    }

    /**
     * Log routing statistics.
     *
     * Called periodically to track system behavior.
     */
    @Scheduled(fixedDelay = "5m", initialDelay = "1m")
    public void logRoutingStatistics() {
        if (!enabled) {
            return;
        }

        long total = totalRoutedTasks.get();
        long defaultCount = defaultRoutedTasks.get();

        if (total == 0) {
            log.debug("No tasks routed yet");
            return;
        }

        StringBuilder stats = new StringBuilder();
        stats.append(String.format("Routing Statistics - Total: %d, Default: %d (%.1f%%)\n",
            total, defaultCount, (defaultCount * 100.0 / total)));

        routingMetrics.forEach((workerGroup, count) -> {
            long c = count.get();
            stats.append(String.format("  %s: %d (%.1f%%)\n",
                workerGroup, c, (c * 100.0 / total)));
        });

        log.info(stats.toString());
    }

    /**
     * Check if worker group routing is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get all active worker groups from cache.
     */
    public Map<String, WorkerGroupConfig> getActiveWorkerGroups() {
        return Map.copyOf(workerGroupCache);
    }
}
