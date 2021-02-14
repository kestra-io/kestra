package org.kestra.core.runners;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import org.kestra.core.metrics.MetricRegistry;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.LogEntry;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.repositories.LogRepositoryInterface;
import org.kestra.core.repositories.SaveRepositoryInterface;
import org.kestra.core.repositories.TriggerRepositoryInterface;

import javax.inject.Inject;
import javax.inject.Named;

@Prototype
@Requires(beans = {ExecutionRepositoryInterface.class, LogRepositoryInterface.class, TriggerRepositoryInterface.class})
public class Indexer implements IndexerInterface {
    private final ExecutionRepositoryInterface executionRepository;
    private final QueueInterface<Execution> executionQueue;
    private final LogRepositoryInterface logRepository;
    private final QueueInterface<LogEntry> logQueue;
    private final MetricRegistry metricRegistry;

    @Inject
    public Indexer(
        ExecutionRepositoryInterface executionRepository,
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        LogRepositoryInterface logRepository,
        @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED) QueueInterface<LogEntry> logQueue,
        LogRepositoryInterface triggerRepository,
        @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED) QueueInterface<LogEntry> triggerQueue,
        MetricRegistry metricRegistry
    ) {
        this.executionRepository = executionRepository;
        this.executionQueue = executionQueue;
        this.logRepository = logRepository;
        this.logQueue = logQueue;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void run() {
        this.send(executionQueue, executionRepository);
        this.send(logQueue, logRepository);
    }

    protected <T> void send(QueueInterface<T> queueInterface, SaveRepositoryInterface<T> saveRepositoryInterface) {
        queueInterface.receive(Indexer.class, item -> {
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_REQUEST_COUNT, "type", item.getClass().getName()).increment();
            this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_IN_COUNT, "type", item.getClass().getName()).increment();

            this.metricRegistry.timer(MetricRegistry.METRIC_INDEXER_REQUEST_DURATION, "type", item.getClass().getName()).record(() -> {
                saveRepositoryInterface.save(item);
                this.metricRegistry.counter(MetricRegistry.METRIC_INDEXER_MESSAGE_OUT_COUNT, "type", item.getClass().getName()).increment();
            });
        });
    }
}
