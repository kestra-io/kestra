package io.kestra.core.queues;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.runners.Worker;
import io.kestra.core.runners.WorkerJob;
import io.micronaut.context.annotation.Requires;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Singleton
@Requires(property = "kestra.server-type", pattern = "(WEBSERVER|STANDALONE)", defaultValue = "STANDALONE")
public class WorkerQueueSizePoller {
    private final MetricRegistry metricRegistry;
    private final QueueInterface<WorkerJob> queue;
    private final Map<String, AtomicInteger> workerGroupExecutionQueueLength = new HashMap<>();

    public WorkerQueueSizePoller(
        MetricRegistry metricRegistry,
        QueueInterface<WorkerJob> queueFactory
    ) {
        this.metricRegistry = metricRegistry;
        this.queue = queueFactory;
    }

    @PostConstruct
    void init() {
        this.register(null);
    }

    @Scheduled(fixedDelay = "${kestra.queue-size-poller.fixed-delay:30s}")
    void getQueueLengths() {
        Map<String, Integer> workerJobsByWorkerGroup = this.queue.queueLagByWorkerGroup(Worker.class);
        workerJobsByWorkerGroup.forEach(
            (workerGroup, length) -> register(workerGroup).set(length)
        );
    }

    private AtomicInteger register(String workerGroup) {
        return this.workerGroupExecutionQueueLength.computeIfAbsent(workerGroup,  (workerGroupName) -> this.metricRegistry.gauge(
            MetricRegistry.WORKER_MESSAGES_UNCONSUMMATED_COUNT,
            MetricRegistry.WORKER_MESSAGES_UNCONSUMMATED_COUNT_DESCRIPTION,
            new AtomicInteger(),
            MetricRegistry.TAG_WORKER_GROUP, workerGroup == null ? "default" : workerGroup
        ));
    }
}
