package io.kestra.core.queues;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.WorkerQueueMetaStore;
import io.kestra.core.runners.WorkerJobEvent;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Provider;
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
    private final WorkerQueueMetaStore workerQueueMetaStore;
    private final Provider<KeyedDispatchQueueInterface<WorkerJobEvent>> workerJobQueueProvider;

    private final Cache<CacheKey, Integer> queueLagCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .build();

    public QueueLagPoller(
        MetricRegistry metricRegistry,
        WorkerQueueMetaStore workerQueueMetaStore,
        Provider<KeyedDispatchQueueInterface<WorkerJobEvent>> workerJobQueueProvider) {
        this.metricRegistry = metricRegistry;
        this.workerJobQueueProvider = workerJobQueueProvider;
        this.workerQueueMetaStore = workerQueueMetaStore;
    }

    @Scheduled(fixedDelay = "300s", initialDelay = "30s")
    public void refreshWorkerQueues() {
        Set<String> availableWorkerQueues = workerQueueMetaStore.listAllWorkerQueueIds();
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobQueue = workerJobQueueProvider.get();
        availableWorkerQueues.stream().filter(
            workerQueueId -> metricRegistry.findGauges(MetricRegistry.METRIC_QUEUE_MESSAGE_LAG).stream().noneMatch(
                gauge -> workerQueueId.equals(gauge.getId().getTag(MetricRegistry.TAG_WORKER_QUEUE))
            )
        ).forEach(
            workerQueueId -> this.register(
                getQueueLagForConsumerGroup(workerQueueId, workerJobQueue),
                metricRegistry.workerQueueTags(workerQueueId, MetricRegistry.TAG_QUEUE_NAME, workerJobQueue.queueName())
            )
        );
    }

    @PostConstruct
    void initQueueMetrics() {
        KeyedDispatchQueueInterface<WorkerJobEvent> workerJobQueue = workerJobQueueProvider.get();
        this.register(
            getQueueLagForConsumerGroup(null, workerJobQueue),
            metricRegistry.workerQueueTags(null, MetricRegistry.TAG_QUEUE_NAME, workerJobQueue.queueName())
        );

        this.workerQueueMetaStore.listAllWorkerQueueIds().forEach(
            workerQueueId -> this.register(
                getQueueLagForConsumerGroup(workerQueueId, workerJobQueue),
                metricRegistry.workerQueueTags(workerQueueId, MetricRegistry.TAG_QUEUE_NAME, workerJobQueue.queueName())
            )
        );
    }

    private void register(Supplier<Number> supplier, String... tags) {
        this.metricRegistry.gauge(
            MetricRegistry.METRIC_QUEUE_MESSAGE_LAG,
            MetricRegistry.METRIC_QUEUE_MESSAGE_LAG_DESCRIPTION,
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
