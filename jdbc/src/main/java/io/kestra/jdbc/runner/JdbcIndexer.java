package io.kestra.jdbc.runner;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.repositories.SaveRepositoryInterface;
import io.kestra.core.runners.Indexer;
import io.kestra.core.runners.IndexerInterface;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.ListUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * This class is responsible to batch-indexed asynchronously queue messages.<p>
 * Some queue messages are indexed synchronously via the {@link JdbcQueueIndexer}.
 */
@SuppressWarnings("this-escape")
@Slf4j
@Singleton
@JdbcRunnerEnabled
public class JdbcIndexer implements IndexerInterface {
    private final LogRepositoryInterface logRepository;
    private final JdbcQueue<LogEntry> logQueue;

    private final MetricRepositoryInterface metricRepository;
    private final JdbcQueue<MetricEntry> metricQueue;
    private final MetricRegistry metricRegistry;
    private final List<Runnable> receiveCancellations = new ArrayList<>();

    private final String id = IdUtils.create();
    private final AtomicReference<ServiceState> state = new AtomicReference<>();
    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Inject
    public JdbcIndexer(
        LogRepositoryInterface logRepository,
        @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED) QueueInterface<LogEntry> logQueue,
        MetricRepositoryInterface metricRepositor,
        @Named(QueueFactoryInterface.METRIC_QUEUE) QueueInterface<MetricEntry> metricQueue,
        MetricRegistry metricRegistry,
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher
    ) {
        this.logRepository = logRepository;
        this.logQueue = (JdbcQueue<LogEntry>) logQueue;
        this.metricRepository = metricRepositor;
        this.metricQueue = (JdbcQueue<MetricEntry>) metricQueue;
        this.metricRegistry = metricRegistry;
        this.eventPublisher = eventPublisher;

        setState(ServiceState.CREATED);
    }

    @Override
    public void run() {
        log.debug("Starting the indexer");
        startQueues();
        setState(ServiceState.RUNNING);
    }

    protected void startQueues() {
        this.sendBatch(logQueue, logRepository);
        this.sendBatch(metricQueue, metricRepository);
    }

    protected <T> void sendBatch(JdbcQueue<T> queueInterface, SaveRepositoryInterface<T> saveRepositoryInterface) {
        this.receiveCancellations.addFirst(queueInterface.receiveBatch(Indexer.class, eithers -> {
            // first, log all deserialization issues
            eithers.stream().filter(either -> either.isRight()).forEach(either -> log.error("unable to deserialize an item: {}", either.getRight().getMessage()));

            // then index all correctly deserialized items
            List<T> items = eithers.stream().filter(either -> either.isLeft()).map(either -> either.getLeft()).toList();
            if (!ListUtils.isEmpty(items)) {
                String itemClassName = items.getFirst().getClass().getName();
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_REQUEST_COUNT, "type", itemClassName).increment();
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT, "type", itemClassName).increment(items.size());

                this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_REQUEST_DURATION, "type", itemClassName).record(() -> {
                    int saved = saveRepositoryInterface.saveBatch(items);
                    this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT, "type", itemClassName).increment(saved);
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
            this.receiveCancellations.forEach(Runnable::run);
            try {
                stopQueue();
                setState(ServiceState.TERMINATED_GRACEFULLY);
            } catch (IOException e) {
                log.error("Failed to close the queue", e);
                setState(ServiceState.TERMINATED_FORCED);
            }
        }
    }

    protected void stopQueue() throws IOException {
        this.logQueue.close();
        this.metricQueue.close();
    }
}
