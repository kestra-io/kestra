package io.kestra.core.queues;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.WorkerGroupMetaStore;
import io.kestra.core.runners.WorkerJobEvent;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "kestra.metric.queue.lag.enabled", value = "true")
public class QueueLagPoller {
    private final MetricRegistry metricRegistry;
    private final WorkerGroupMetaStore workerGroupExecutor;
    private final BeanProvider<KeyedDispatchQueueInterface<WorkerJobEvent>> workerJobQueueProvider;

    private final Cache<CacheKey, Integer> queueLagCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .build();

    public QueueLagPoller(
        MetricRegistry metricRegistry,
        WorkerGroupMetaStore workerGroupExecutor,
        BeanProvider<KeyedDispatchQueueInterface<WorkerJobEvent>> workerJobQueueProvider) {
        this.metricRegistry = metricRegistry;
        this.workerJobQueueProvider = workerJobQueueProvider;
        this.workerGroupExecutor = workerGroupExecutor;
    }

    @Scheduled(fixedDelay = "300s", initialDelay = "30s")
    public void refreshWorkerGroups() {
        Set<String> availableWorkerGroups = workerGroupExecutor.listAllWorkerGroupKeys();
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobQueue = workerJobQueueProvider.get();
        availableWorkerGroups.stream().filter(
            workerGroup -> metricRegistry.findGauges(MetricRegistry.QUEUE_MESSAGE_LAG_COUNT).stream().noneMatch(
                gauge -> workerGroup.equals(gauge.getId().getTag(MetricRegistry.TAG_WORKER_GROUP))
            )
        ).forEach(
            workerGroup -> this.register(
                getQueueLagForConsumerGroup(workerGroup, workerJobQueue),
                MetricRegistry.TAG_WORKER_GROUP, workerGroup,
                MetricRegistry.TAG_QUEUE_NAME, workerJobQueue.queueName()
            )
        );
    }

    @PostConstruct
    void initQueueMetrics() {
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobQueue = workerJobQueueProvider.get();
        this.register(
            getQueueLagForConsumerGroup(null, workerJobQueue),
            MetricRegistry.TAG_WORKER_GROUP, "__default__",
            MetricRegistry.TAG_QUEUE_NAME, workerJobQueue.queueName()
        );

        workerGroupExecutor.listAllWorkerGroupKeys().forEach(
            workerGroupKey -> this.register(
                getQueueLagForConsumerGroup(workerGroupKey, workerJobQueue),
                MetricRegistry.TAG_WORKER_GROUP, workerGroupKey,
                MetricRegistry.TAG_QUEUE_NAME, workerJobQueue.queueName()
            )
        );
    }

    private void register(Supplier<Number> supplier, String... tags) {
        this.metricRegistry.gauge(
            MetricRegistry.QUEUE_MESSAGE_LAG_COUNT,
            MetricRegistry.QUEUE_MESSAGE_LAG_COUNT_DESCRIPTION,
            supplier,
            tags
        );
    }

    private Supplier<Number> getQueueLagForConsumerGroup(String consumerGroup, KeyedDispatchQueueInterface<WorkerJobEvent> queue) {
        return () -> queueLagCache.get(new CacheKey(queue.queueName(), consumerGroup), (key) -> queue.queueLag(consumerGroup));
    }

    private record CacheKey(String queueName, String consumerGroup) {
    }
}
