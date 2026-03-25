package io.kestra.indexer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.*;
import io.kestra.core.queues.event.DispatchEvent;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.IndexingRepository;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.server.ServiceType;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible for batch-indexing asynchronously queue messages.
 * <p>
 */
@SuppressWarnings("this-escape")
@Slf4j
@Singleton
public class DefaultIndexer implements Indexer {
    private final LogRepositoryInterface logRepository;
    private final DispatchQueueInterface<LogEntry> logQueue;

    private final MetricRepositoryInterface metricRepository;
    private final DispatchQueueInterface<MetricEntry> metricQueue;
    private final MetricRegistry metricRegistry;
    private final List<QueueSubscriber<?>> subscribers = new ArrayList<>();

    private final String id = IdUtils.create();
    private final AtomicReference<ServiceState> state = new AtomicReference<>();
    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final IgnoreExecutionService ignoreExecutionService;
    private final QueueService queueService;

    @Inject
    public DefaultIndexer(
        LogRepositoryInterface logRepository,
        DispatchQueueInterface<LogEntry> logQueue,
        MetricRepositoryInterface metricRepositor,
        DispatchQueueInterface<MetricEntry> metricQueue,
        MetricRegistry metricRegistry,
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher,
        IgnoreExecutionService ignoreExecutionService,
        QueueService queueService) {
        this.logRepository = logRepository;
        this.logQueue = logQueue;
        this.metricRepository = metricRepositor;
        this.metricQueue = metricQueue;
        this.metricRegistry = metricRegistry;
        this.eventPublisher = eventPublisher;
        this.ignoreExecutionService = ignoreExecutionService;
        this.queueService = queueService;

        setState(ServiceState.CREATED);
    }

    @Override
    public void run() {
        log.debug("Starting the indexer");
        startQueues();
        setState(ServiceState.RUNNING);
        log.info("Indexer started");
    }

    protected void startQueues() {
        this.sendBatch(logQueue, logRepository);
        this.sendBatch(metricQueue, metricRepository);
    }

    protected <T extends DispatchEvent> void sendBatch(DispatchQueueInterface<T> queueInterface, IndexingRepository<T> indexingRepository) {
        this.subscribers.addFirst(queueInterface.subscriber().subscribeBatch(eithers ->
        {
            // first, log all deserialization issues
            eithers.stream().filter(either -> either.isRight()).forEach(either -> log.error("unable to deserialize an item: {}", either.getRight().getMessage()));

            // then index all correctly deserialized items
            List<T> items = eithers.stream()
                .filter(either -> either.isLeft())
                .map(either -> either.getLeft())
                .filter(it ->
                {
                    if (ignoreExecutionService.ignoreIndexerRecord(queueService.key(it))) {
                        log.warn("Skipping indexer record for key: {}", queueService.key(it));
                        return false;
                    }
                    return true;
                })
                .toList();

            if (!ListUtils.isEmpty(items)) {
                String itemClassName = items.getFirst().getClass().getName();
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_REQUEST_COUNT, MetricRegistry.METRIC_INDEXER_REQUEST_COUNT_DESCRIPTION, "type", itemClassName).increment();
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT, MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT_DESCRIPTION, "type", itemClassName)
                    .increment(items.size());

                this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_REQUEST_DURATION, MetricRegistry.METRIC_INDEXER_REQUEST_DURATION_DESCRIPTION, "type", itemClassName).record(() ->
                {
                    int saved = indexingRepository.saveBatch(items);
                    this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT, MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT_DESCRIPTION, "type", itemClassName)
                        .increment(saved);
                });
            }
        }));
    }

    private void setState(final ServiceState state) {
        this.state.set(state);
        this.eventPublisher.publishEvent(new ServiceStateChangeEvent(this));
    }

    /** {@inheritDoc} **/
    @Override
    public String getId() {
        return id;
    }

    /** {@inheritDoc} **/
    @Override
    public ServiceType getType() {
        return ServiceType.INDEXER;
    }

    /** {@inheritDoc} **/
    @Override
    public ServiceState getState() {
        return state.get();
    }

    @PreDestroy
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            setState(ServiceState.TERMINATING);
            if (log.isDebugEnabled()) {
                log.debug("Terminating");
            }
            this.subscribers.forEach(QueueSubscriber::close);
            setState(ServiceState.TERMINATED_GRACEFULLY);
        }
    }
}
