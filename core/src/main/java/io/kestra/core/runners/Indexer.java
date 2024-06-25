package io.kestra.core.runners;

import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.repositories.MetricRepositoryInterface;
import io.kestra.core.repositories.SaveRepositoryInterface;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.server.ServiceStateChangeEvent;
import io.kestra.core.utils.IdUtils;
import io.micronaut.context.annotation.Requires;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("this-escape")
@Slf4j
@Singleton
@Requires(beans = {ExecutionRepositoryInterface.class, LogRepositoryInterface.class, TriggerRepositoryInterface.class})
public class Indexer implements IndexerInterface {
    private final ExecutionRepositoryInterface executionRepository;
    private final QueueInterface<Execution> executionQueue;
    private final LogRepositoryInterface logRepository;
    private final QueueInterface<LogEntry> logQueue;

    private final MetricRepositoryInterface metricRepository;
    private final QueueInterface<MetricEntry> metricQueue;
    private final MetricRegistry metricRegistry;
    private final List<Runnable> receiveCancellations = new ArrayList<>();

    private final String id = IdUtils.create();
    private final AtomicReference<ServiceState> state = new AtomicReference<>();
    private final ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher;

    @Inject
    public Indexer(
        ExecutionRepositoryInterface executionRepository,
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        LogRepositoryInterface logRepository,
        @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED) QueueInterface<LogEntry> logQueue,
        MetricRepositoryInterface metricRepositor,
        @Named(QueueFactoryInterface.METRIC_QUEUE) QueueInterface<MetricEntry> metricQueue,
        MetricRegistry metricRegistry,
        ApplicationEventPublisher<ServiceStateChangeEvent> eventPublisher
    ) {
        this.executionRepository = executionRepository;
        this.executionQueue = executionQueue;
        this.logRepository = logRepository;
        this.logQueue = logQueue;
        this.metricRepository = metricRepositor;
        this.metricQueue = metricQueue;
        this.metricRegistry = metricRegistry;
        this.eventPublisher = eventPublisher;
        setState(ServiceState.CREATED);
    }

    @Override
    public void run() {
        this.send(executionQueue, executionRepository);
        this.send(logQueue, logRepository);
        this.send(metricQueue, metricRepository);
        setState(ServiceState.RUNNING);
    }

    protected <T> void send(QueueInterface<T> queueInterface, SaveRepositoryInterface<T> saveRepositoryInterface) {
        this.receiveCancellations.addFirst(queueInterface.receive(Indexer.class, either -> {
            if (either.isRight()) {
                log.error("unable to deserialize an item: {}", either.getRight().getMessage());
                return;
            }

            T item = either.getLeft();
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_REQUEST_COUNT, "type", item.getClass().getName()).increment();
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT, "type", item.getClass().getName()).increment();

            this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_REQUEST_DURATION, "type", item.getClass().getName()).record(() -> {
                saveRepositoryInterface.save(item);
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT, "type", item.getClass().getName()).increment();
            });
        }));
    }

    protected void setState(final ServiceState state) {
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
        setState(ServiceState.TERMINATING);
        this.receiveCancellations.forEach(Runnable::run);
        try {
            this.executionQueue.close();
            this.logQueue.close();
            this.metricQueue.close();
            setState(ServiceState.TERMINATED_GRACEFULLY);
        } catch (IOException e) {
            log.error("Failed to close the queue", e);
            setState(ServiceState.TERMINATED_FORCED);
        }
    }
}
