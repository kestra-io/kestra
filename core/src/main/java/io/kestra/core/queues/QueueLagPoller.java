package io.kestra.core.queues;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerGroupExecutorInterface;
import io.kestra.core.runners.WorkerJob;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;

import static io.kestra.core.queues.QueueFactoryInterface.WORKERJOB_NAMED;

@Slf4j
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)")
@Requires(property = "kestra.metric.queue.lag.enabled", value = "true")
public class QueueLagPoller {
    private final MetricRegistry metricRegistry;
    private final WorkerGroupExecutorInterface workerGroupExecutor;
    private final BeanProvider<QueueInterface<WorkerJob>> workerJobQueueProvider;

    private final Cache<CacheKey, Integer> queueLagCache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofSeconds(30))
        .build();

    public QueueLagPoller(
        MetricRegistry metricRegistry,
        WorkerGroupExecutorInterface workerGroupExecutor,
        BeanProvider<QueueInterface<WorkerJob>> workerJobQueueProvider
    ) {
        this.metricRegistry = metricRegistry;
        this.workerJobQueueProvider = workerJobQueueProvider;
        this.workerGroupExecutor = workerGroupExecutor;
    }


    @Scheduled(fixedDelay = "300s", initialDelay = "30s")
    public void refreshWorkerGroups() {
        Set<String> availableWorkerGroups = workerGroupExecutor.listAllWorkerGroupKeys();
        QueueInterface<WorkerJob> workerJobQueue = workerJobQueueProvider.get();
        availableWorkerGroups.stream().filter(workerGroup ->
            metricRegistry.findGauges(MetricRegistry.QUEUE_MESSAGE_LAG_COUNT).stream().noneMatch(
                gauge -> workerGroup.equals(gauge.getId().getTag(MetricRegistry.TAG_WORKER_GROUP))
            )
        ).forEach(workerGroup ->
            this.register(
                getQueueLagForConsumerGroup(WORKERJOB_NAMED, workerGroup, Worker.class, workerJobQueue),
                MetricRegistry.TAG_WORKER_GROUP, workerGroup,
                MetricRegistry.TAG_QUEUE_NAME, WORKERJOB_NAMED
            )
        );
    }

    @PostConstruct
    void initQueueMetrics() {
        QueueInterface<WorkerJob> workerJobQueue = workerJobQueueProvider.get();
        this.register(
            getQueueLagForConsumerGroup(WORKERJOB_NAMED, null, Worker.class, workerJobQueue),
            MetricRegistry.TAG_WORKER_GROUP, "__default__",
            MetricRegistry.TAG_QUEUE_NAME, WORKERJOB_NAMED
        );

        workerGroupExecutor.listAllWorkerGroupKeys().forEach(workerGroupKey ->
            this.register(
                getQueueLagForConsumerGroup(WORKERJOB_NAMED, workerGroupKey, Worker.class, workerJobQueue),
                MetricRegistry.TAG_WORKER_GROUP, workerGroupKey,
                MetricRegistry.TAG_QUEUE_NAME, WORKERJOB_NAMED
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

    private Supplier<Number> getQueueLagForConsumerGroup(String queueName, String consumerGroup, Class<?> queueType, QueueInterface<?> queue) {
        return () -> queueLagCache.get(new CacheKey(queueName, consumerGroup), (key) -> queue.queueLagForConsumerGroup(consumerGroup, queueType));
    }

    private record CacheKey(String queueName, String consumerGroup) { }
}
